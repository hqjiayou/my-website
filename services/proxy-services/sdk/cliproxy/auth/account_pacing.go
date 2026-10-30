package auth

import (
	"context"
	"math/rand/v2"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	cliproxyexecutor "github.com/router-for-me/CLIProxyAPI/v8/sdk/cliproxy/executor"
)

// accountPacingSettings is the in-memory copy of routing.account-pacing.
// A zero duration or count turns that gate off. State itself is not persisted.
type accountPacingSettings struct {
	minInterval      time.Duration
	jitter           time.Duration
	maxInFlight      int
	quotaUsedPercent int
}

type pacingClockHolder struct {
	now func() time.Time
}

type accountPacingSlot struct {
	nextAllowedAt atomic.Int64
	inFlight      atomic.Int32
}

type accountInFlightLease struct {
	release func()
}

// Release returns the slot at most once. Safe on a zero lease.
func (l *accountInFlightLease) Release() {
	if l == nil || l.release == nil {
		return
	}
	release := l.release
	l.release = nil
	release()
}

type accountPacingContextKey struct{}

// SetAccountPacingConfig publishes the local pacing gates. It sits beside SetRetryConfig
// and is refreshed on config reload. Negative values are treated as off. In-flight counts
// and nextAllowedAt survive the reload; they are cleared only when the process restarts.
// Home mode (-home-jwt) ignores these values. See docs/LOCAL_PATCHES.md.
func (m *Manager) SetAccountPacingConfig(minInterval, jitter time.Duration, maxInFlight, quotaUsedPercent int) {
	if m == nil {
		return
	}
	if minInterval < 0 {
		minInterval = 0
	}
	if jitter < 0 {
		jitter = 0
	}
	if maxInFlight < 0 {
		maxInFlight = 0
	}
	if quotaUsedPercent < 0 {
		quotaUsedPercent = 0
	}
	if quotaUsedPercent > 100 {
		quotaUsedPercent = 100
	}
	m.accountPacing.Store(&accountPacingSettings{
		minInterval:      minInterval,
		jitter:           jitter,
		maxInFlight:      maxInFlight,
		quotaUsedPercent: quotaUsedPercent,
	})
}

// AccountPacingConfig returns the gates currently applied. Zeros mean the gate is off.
func (m *Manager) AccountPacingConfig() (minInterval, jitter time.Duration, maxInFlight, quotaUsedPercent int) {
	cfg := m.loadAccountPacing()
	if cfg == nil {
		return 0, 0, 0, 0
	}
	return cfg.minInterval, cfg.jitter, cfg.maxInFlight, cfg.quotaUsedPercent
}

func (m *Manager) loadAccountPacing() *accountPacingSettings {
	if m == nil {
		return nil
	}
	return m.accountPacing.Load()
}

func (m *Manager) accountPacingActive() bool {
	cfg := m.loadAccountPacing()
	return cfg != nil && (cfg.minInterval > 0 || cfg.maxInFlight > 0 || cfg.quotaUsedPercent > 0)
}

func (m *Manager) pacingNow() time.Time {
	if m != nil {
		if clock := m.accountPacingClock.Load(); clock != nil && clock.now != nil {
			return clock.now()
		}
	}
	return time.Now()
}

func (m *Manager) setAccountPacingClock(now func() time.Time) {
	if m == nil {
		return
	}
	if now == nil {
		m.accountPacingClock.Store(nil)
		return
	}
	m.accountPacingClock.Store(&pacingClockHolder{now: now})
}

// withAccountPacing attaches the shared eligibility filter. Scheduler candidate
// construction and the conductor eligibility path both read it from context.
func (m *Manager) withAccountPacing(ctx context.Context) context.Context {
	if m == nil || m.HomeEnabled() || !m.accountPacingActive() {
		return ctx
	}
	if ctx == nil {
		ctx = context.Background()
	}
	now := m.pacingNow()
	return context.WithValue(ctx, accountPacingContextKey{}, func(candidate *Auth) bool {
		return m.accountPacingAllows(candidate, now)
	})
}

func accountPacingAllowsFromContext(ctx context.Context) func(*Auth) bool {
	if ctx == nil {
		return nil
	}
	allows, _ := ctx.Value(accountPacingContextKey{}).(func(*Auth) bool)
	return allows
}

// accountPacingAllows is the only local pacing eligibility check (issue #152).
// conductor_selection.go and the scheduler predicate both call it.
// An account that fails is simply ineligible for this pick: no new error code and no cooldown write.
// Home mode does not apply. Patch notes: services/proxy-services/docs/LOCAL_PATCHES.md.
func (m *Manager) accountPacingAllows(candidate *Auth, now time.Time) bool {
	if m == nil || candidate == nil || m.HomeEnabled() {
		return true
	}
	cfg := m.loadAccountPacing()
	if cfg == nil {
		return true
	}
	if cfg.quotaUsedPercent > 0 {
		if used, ok := observedAccountQuotaUsedPercent(candidate); ok && used >= cfg.quotaUsedPercent {
			return false
		}
	}
	slot := m.peekAccountPacingSlot(candidate.ID)
	if slot == nil {
		return true
	}
	if cfg.minInterval > 0 {
		next := slot.nextAllowedAt.Load()
		if next > 0 && now.Before(time.Unix(0, next)) {
			return false
		}
	}
	if cfg.maxInFlight > 0 && int(slot.inFlight.Load()) >= cfg.maxInFlight {
		return false
	}
	return true
}

// noteAccountPacingSuccess stamps nextAllowedAt at the MarkResult success accounting point.
// Failure results do not call this, so they leave the clock unchanged.
func (m *Manager) noteAccountPacingSuccess(authID string, completedAt time.Time) {
	if m == nil || authID == "" || m.HomeEnabled() || completedAt.IsZero() {
		return
	}
	cfg := m.loadAccountPacing()
	if cfg == nil || cfg.minInterval <= 0 {
		return
	}
	extra := time.Duration(0)
	if cfg.jitter > 0 {
		extra = rand.N(cfg.jitter + 1)
	}
	m.accountPacingSlot(authID).nextAllowedAt.Store(completedAt.Add(cfg.minInterval + extra).UnixNano())
}

// reserveAccountInFlight occupies a slot when the account is selected.
// The returned function releases it exactly once (success, failure, cancel, panic,
// or a selection that never reaches the upstream). ok is false when the cap is already full.
func (m *Manager) reserveAccountInFlight(authID string) (func(), bool) {
	if m == nil || authID == "" || m.HomeEnabled() {
		return nil, true
	}
	cfg := m.loadAccountPacing()
	if cfg == nil || cfg.maxInFlight <= 0 {
		return nil, true
	}
	slot := m.accountPacingSlot(authID)
	for {
		current := slot.inFlight.Load()
		if int(current) >= cfg.maxInFlight {
			return nil, false
		}
		if slot.inFlight.CompareAndSwap(current, current+1) {
			var once sync.Once
			return func() {
				once.Do(func() { slot.inFlight.Add(-1) })
			}, true
		}
	}
}

func (m *Manager) holdAccountInFlight(lease *accountInFlightLease, authID string) bool {
	release, ok := m.reserveAccountInFlight(authID)
	if !ok {
		return false
	}
	if lease != nil && release != nil {
		lease.release = release
	}
	return true
}

// wrapAccountPacingStream keeps the in-flight slot until forwarding stops.
// ExecuteStream returns before the body is finished; Home uses the same shape in wrapHomeStream.
func wrapAccountPacingStream(ctx context.Context, result *cliproxyexecutor.StreamResult, release func()) *cliproxyexecutor.StreamResult {
	if result == nil || result.Chunks == nil {
		if release != nil {
			release()
		}
		return result
	}
	if ctx == nil {
		ctx = context.Background()
	}
	out := make(chan cliproxyexecutor.StreamChunk)
	go func() {
		defer close(out)
		if release != nil {
			defer release()
		}
		for {
			select {
			case <-ctx.Done():
				return
			case chunk, ok := <-result.Chunks:
				if !ok {
					return
				}
				select {
				case <-ctx.Done():
					return
				case out <- chunk:
				}
			}
		}
	}()
	return &cliproxyexecutor.StreamResult{Headers: result.Headers, Chunks: out}
}

func (m *Manager) peekAccountPacingSlot(authID string) *accountPacingSlot {
	if m == nil || authID == "" {
		return nil
	}
	m.accountPacingMu.Lock()
	defer m.accountPacingMu.Unlock()
	if m.accountPacingSlots == nil {
		return nil
	}
	return m.accountPacingSlots[authID]
}

func (m *Manager) accountPacingSlot(authID string) *accountPacingSlot {
	m.accountPacingMu.Lock()
	defer m.accountPacingMu.Unlock()
	if m.accountPacingSlots == nil {
		m.accountPacingSlots = make(map[string]*accountPacingSlot)
	}
	slot := m.accountPacingSlots[authID]
	if slot == nil {
		slot = &accountPacingSlot{}
		m.accountPacingSlots[authID] = slot
	}
	return slot
}

func (m *Manager) accountInFlight(authID string) int {
	slot := m.peekAccountPacingSlot(authID)
	if slot == nil {
		return 0
	}
	return int(slot.inFlight.Load())
}

func (m *Manager) accountNextAllowedAt(authID string) time.Time {
	slot := m.peekAccountPacingSlot(authID)
	if slot == nil {
		return time.Time{}
	}
	nano := slot.nextAllowedAt.Load()
	if nano == 0 {
		return time.Time{}
	}
	return time.Unix(0, nano)
}

// rejectSessionAffinityPacing resolves the binding first, then judges pacing on that
// account alone. A hit returns auth_not_found and does not rebind or fall back.
func (m *Manager) rejectSessionAffinityPacing(provider, model string, opts cliproxyexecutor.Options) error {
	if m == nil || m.HomeEnabled() || !m.accountPacingActive() {
		return nil
	}
	affinity, ok := m.Selector().(*SessionAffinitySelector)
	if !ok || affinity == nil {
		return nil
	}
	authID, bound := affinity.boundAuthID(provider, model, opts)
	if !bound || authID == "" {
		return nil
	}
	auth, found := m.GetByID(authID)
	if !found || auth == nil || m.accountPacingAllows(auth, m.pacingNow()) {
		return nil
	}
	return &Error{Code: "auth_not_found", Message: "no auth available"}
}

func observedAccountQuotaUsedPercent(candidate *Auth) (int, bool) {
	if candidate == nil || len(candidate.Quota.Signals) == 0 {
		return 0, false
	}
	fallback := -1
	hasFallback := false
	for key, value := range candidate.Quota.Signals {
		lower := strings.ToLower(key)
		if !strings.Contains(lower, "used-percent") && !strings.Contains(lower, "used_percent") {
			continue
		}
		used, ok := parseObservedUsedPercent(value)
		if !ok {
			continue
		}
		if strings.Contains(lower, "primary") {
			return used, true
		}
		if !hasFallback || used > fallback {
			fallback = used
			hasFallback = true
		}
	}
	if !hasFallback {
		return 0, false
	}
	return fallback, true
}

func parseObservedUsedPercent(raw string) (int, bool) {
	raw = strings.TrimSpace(raw)
	raw = strings.TrimSuffix(raw, "%")
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return 0, false
	}
	if parsed, err := strconv.Atoi(raw); err == nil {
		return parsed, true
	}
	parsed, err := strconv.ParseFloat(raw, 64)
	if err != nil {
		return 0, false
	}
	return int(parsed), true
}
