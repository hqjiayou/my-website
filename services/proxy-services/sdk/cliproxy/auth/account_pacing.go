package auth

import (
	"context"
	"math/rand/v2"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/router-for-me/CLIProxyAPI/v8/internal/registry"
	"github.com/router-for-me/CLIProxyAPI/v8/internal/thinking"
	cliproxyexecutor "github.com/router-for-me/CLIProxyAPI/v8/sdk/cliproxy/executor"
)

// accountPacingWaitedMetadataKey marks a request that already paused once for an interval gate.
const accountPacingWaitedMetadataKey = "account-pacing-waited"

// accountPacingSettings is the in-memory copy of routing.account-pacing.
// A zero duration or count turns that gate off. State itself is not persisted.
type accountPacingSettings struct {
	minInterval      time.Duration
	jitter           time.Duration
	maxInFlight      int
	quotaUsedPercent int
	maxWait          time.Duration
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
func (m *Manager) SetAccountPacingConfig(minInterval, jitter time.Duration, maxInFlight, quotaUsedPercent int, maxWait time.Duration) {
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
	if maxWait < 0 {
		maxWait = 0
	}
	m.accountPacing.Store(&accountPacingSettings{
		minInterval:      minInterval,
		jitter:           jitter,
		maxInFlight:      maxInFlight,
		quotaUsedPercent: quotaUsedPercent,
		maxWait:          maxWait,
	})
}

// AccountPacingConfig returns the gates currently applied. Zeros mean the gate is off.
func (m *Manager) AccountPacingConfig() (minInterval, jitter time.Duration, maxInFlight, quotaUsedPercent int, maxWait time.Duration) {
	cfg := m.loadAccountPacing()
	if cfg == nil {
		return 0, 0, 0, 0, 0
	}
	return cfg.minInterval, cfg.jitter, cfg.maxInFlight, cfg.quotaUsedPercent, cfg.maxWait
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

type accountPacingBlockKind int

const (
	accountPacingOpen accountPacingBlockKind = iota
	accountPacingInterval
	accountPacingHard
)

// accountPacingAllows is the only local pacing eligibility check (issue #152).
// conductor_selection.go and the scheduler predicate both call it.
// An account that fails is simply ineligible for this pick: no new error code and no cooldown write.
// Home mode does not apply. Patch notes: services/proxy-services/docs/LOCAL_PATCHES.md.
func (m *Manager) accountPacingAllows(candidate *Auth, now time.Time) bool {
	kind, _ := m.accountPacingBlock(candidate, now)
	return kind == accountPacingOpen
}

// accountPacingBlock reports why an account is out this round.
// Interval-only blocks can pause once. Quota and in-flight blocks cannot.
func (m *Manager) accountPacingBlock(candidate *Auth, now time.Time) (accountPacingBlockKind, time.Time) {
	if m == nil || candidate == nil || m.HomeEnabled() {
		return accountPacingOpen, time.Time{}
	}
	cfg := m.loadAccountPacing()
	if cfg == nil {
		return accountPacingOpen, time.Time{}
	}
	hard := false
	if cfg.quotaUsedPercent > 0 {
		if used, ok := observedAccountQuotaUsedPercent(candidate); ok && used >= cfg.quotaUsedPercent {
			hard = true
		}
	}
	slot := m.peekAccountPacingSlot(candidate.ID)
	var next time.Time
	interval := false
	if slot != nil && cfg.minInterval > 0 {
		if nano := slot.nextAllowedAt.Load(); nano > 0 {
			next = time.Unix(0, nano)
			if now.Before(next) {
				interval = true
			}
		}
	}
	if slot != nil && cfg.maxInFlight > 0 && int(slot.inFlight.Load()) >= cfg.maxInFlight {
		hard = true
	}
	if hard {
		return accountPacingHard, next
	}
	if interval {
		return accountPacingInterval, next
	}
	return accountPacingOpen, time.Time{}
}

// accountPacingBeforePick pauses once when the accounts this request could use
// are blocked only by the interval gate, then lets the caller select again.
// A quota or in-flight block does not pause. max-wait 0 does not pause.
// The pause reuses waitForCooldown so cancellation ends it. One request pauses at most once.
func (m *Manager) accountPacingBeforePick(ctx context.Context, providers []string, affinityProvider, model string, opts cliproxyexecutor.Options, tried map[string]struct{}) error {
	if m == nil || m.HomeEnabled() || !m.accountPacingActive() {
		return nil
	}
	cfg := m.loadAccountPacing()
	if cfg == nil || cfg.maxWait <= 0 || cfg.minInterval <= 0 {
		return nil
	}
	if ctx == nil {
		ctx = context.Background()
	}
	opts.EnsureMetadata()
	if waited, _ := opts.Metadata[accountPacingWaitedMetadataKey].(bool); waited {
		return nil
	}
	now := m.pacingNow()
	if auth, bound := m.boundPacingAuth(affinityProvider, model, opts); bound {
		kind, next := m.accountPacingBlock(auth, now)
		if kind != accountPacingInterval || !accountPacingWaitWithin(now, next, cfg.maxWait) {
			return nil
		}
		opts.Metadata[accountPacingWaitedMetadataKey] = true
		if err := waitForCooldown(ctx, next.Sub(now), cfg.maxWait); err != nil {
			return err
		}
		fresh, found := m.GetByID(auth.ID)
		if !found || fresh == nil {
			return &Error{Code: "auth_not_found", Message: "no auth available"}
		}
		if kind, _ = m.accountPacingBlock(fresh, m.pacingNow()); kind != accountPacingOpen {
			return &Error{Code: "auth_not_found", Message: "no auth available"}
		}
		return nil
	}
	wait, ok := m.nearestIntervalWait(providers, model, opts, tried, now, cfg.maxWait)
	if !ok {
		return nil
	}
	opts.Metadata[accountPacingWaitedMetadataKey] = true
	return waitForCooldown(ctx, wait, cfg.maxWait)
}

func accountPacingWaitWithin(now, next time.Time, maxWait time.Duration) bool {
	if next.IsZero() || maxWait <= 0 || !next.After(now) {
		return false
	}
	return !next.After(now.Add(maxWait))
}

func (m *Manager) nearestIntervalWait(providers []string, model string, opts cliproxyexecutor.Options, tried map[string]struct{}, now time.Time, maxWait time.Duration) (time.Duration, bool) {
	candidates := m.pacingSelectableAuths(providers, model, opts, tried)
	if len(candidates) == 0 {
		return 0, false
	}
	var nearest time.Time
	for _, candidate := range candidates {
		kind, next := m.accountPacingBlock(candidate, now)
		if kind != accountPacingInterval {
			return 0, false
		}
		if nearest.IsZero() || next.Before(nearest) {
			nearest = next
		}
	}
	if !accountPacingWaitWithin(now, nearest, maxWait) {
		return 0, false
	}
	return nearest.Sub(now), true
}

func (m *Manager) boundPacingAuth(provider, model string, opts cliproxyexecutor.Options) (*Auth, bool) {
	if m == nil {
		return nil, false
	}
	affinity, ok := m.Selector().(*SessionAffinitySelector)
	if !ok || affinity == nil {
		return nil, false
	}
	authID, bound := affinity.boundAuthID(provider, model, opts)
	if !bound || authID == "" {
		return nil, false
	}
	auth, found := m.GetByID(authID)
	if !found || auth == nil {
		return nil, false
	}
	return auth, true
}

func (m *Manager) pacingSelectableAuths(providers []string, model string, opts cliproxyexecutor.Options, tried map[string]struct{}) []*Auth {
	if m == nil {
		return nil
	}
	providerSet := make(map[string]struct{}, len(providers))
	for _, provider := range providers {
		key := canonicalSchedulingProvider(provider)
		if key == "" {
			continue
		}
		providerSet[key] = struct{}{}
	}
	if len(providerSet) == 0 {
		return nil
	}
	pinnedAuthID := pinnedAuthIDFromMetadata(opts.Metadata)
	eligibility := authSelectionEligibilityForRequest(context.Background(), opts)
	modelKey := strings.TrimSpace(model)
	if modelKey != "" {
		parsed := thinking.ParseSuffix(modelKey)
		if parsed.ModelName != "" {
			modelKey = strings.TrimSpace(parsed.ModelName)
		}
	}
	registryRef := registry.GetGlobalRegistry()
	m.mu.RLock()
	defer m.mu.RUnlock()
	candidates := make([]*Auth, 0, len(m.auths))
	for _, candidate := range m.auths {
		if candidate == nil || candidate.Disabled {
			continue
		}
		if pinnedAuthID != "" && candidate.ID != pinnedAuthID {
			continue
		}
		if !eligibility.allows(candidate) {
			continue
		}
		providerKey := executorKeyFromAuth(candidate)
		if _, ok := providerSet[providerKey]; !ok {
			continue
		}
		if _, ok := m.executorLocked(providerKey); !ok {
			continue
		}
		if modelKey != "" && !m.authSupportsRouteModel(registryRef, candidate, model) {
			continue
		}
		if tried != nil {
			if _, used := tried[candidate.ID]; used {
				continue
			}
		}
		candidates = append(candidates, candidate)
	}
	return candidates
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
