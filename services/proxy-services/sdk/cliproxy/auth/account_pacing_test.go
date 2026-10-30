package auth

import (
	"context"
	"errors"
	"net/http"
	"runtime"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/google/uuid"
	internalconfig "github.com/router-for-me/CLIProxyAPI/v8/internal/config"
	"github.com/router-for-me/CLIProxyAPI/v8/internal/registry"
	cliproxyexecutor "github.com/router-for-me/CLIProxyAPI/v8/sdk/cliproxy/executor"
)

func TestAccountPacingSuccessClockAndFailureLeavesIt(t *testing.T) {
	clock := time.Date(2026, 10, 31, 0, 0, 0, 0, time.UTC)
	manager := NewManager(nil, nil, nil)
	manager.setAccountPacingClock(func() time.Time { return clock })
	manager.SetAccountPacingConfig(2*time.Second, 0, 0, 0, 0)
	auth := &Auth{ID: "pace-clock", Provider: "codex", Status: StatusActive}
	if _, err := manager.Register(context.Background(), auth); err != nil {
		t.Fatal(err)
	}

	manager.MarkResult(context.Background(), Result{AuthID: auth.ID, Provider: "codex", Model: "m", Success: true})
	got := manager.accountNextAllowedAt(auth.ID)
	if !got.Equal(clock.Add(2 * time.Second)) {
		t.Fatalf("nextAllowedAt = %s, want %s", got, clock.Add(2*time.Second))
	}

	manager.MarkResult(context.Background(), Result{
		AuthID: auth.ID, Provider: "codex", Model: "m", Success: false,
		Error: &Error{HTTPStatus: http.StatusTooManyRequests, Message: "limited"},
	})
	if again := manager.accountNextAllowedAt(auth.ID); !again.Equal(got) {
		t.Fatalf("failure moved nextAllowedAt from %s to %s", got, again)
	}
}

func TestAccountPacingJitterStaysInsideConfiguredBound(t *testing.T) {
	clock := time.Date(2026, 10, 31, 1, 0, 0, 0, time.UTC)
	manager := NewManager(nil, nil, nil)
	manager.setAccountPacingClock(func() time.Time { return clock })
	interval := 2 * time.Second
	jitter := 500 * time.Millisecond
	manager.SetAccountPacingConfig(interval, jitter, 0, 0, 0)
	if _, err := manager.Register(context.Background(), &Auth{ID: "pace-jitter", Provider: "codex"}); err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 40; i++ {
		manager.noteAccountPacingSuccess("pace-jitter", clock)
		got := manager.accountNextAllowedAt("pace-jitter")
		if got.Before(clock.Add(interval)) || got.After(clock.Add(interval+jitter)) {
			t.Fatalf("sample %d nextAllowedAt = %s, want [%s, %s]", i, got, clock.Add(interval), clock.Add(interval+jitter))
		}
	}
}

func TestAccountPacingInFlightReleasesOnEveryExit(t *testing.T) {
	model := "pace-inflight-" + uuid.NewString()
	for _, name := range []string{"success", "failure", "cancel", "panic"} {
		t.Run(name, func(t *testing.T) {
			manager, _ := newPacingManager(t, model, 0, 1, 0)
			id := registerPacingAuth(t, manager, "pace-"+name, model)
			ctx, cancel := context.WithCancel(context.Background())
			defer cancel()
			entered := make(chan struct{})
			manager.RegisterExecutor(&mockCustomErrorExecutor{
				identifier: "codex",
				executeFn: func(execCtx context.Context, _ *Auth, _ cliproxyexecutor.Request, _ cliproxyexecutor.Options) (cliproxyexecutor.Response, error) {
					close(entered)
					switch name {
					case "failure":
						return cliproxyexecutor.Response{}, &Error{HTTPStatus: http.StatusBadGateway, Message: "down"}
					case "cancel":
						<-execCtx.Done()
						return cliproxyexecutor.Response{}, execCtx.Err()
					case "panic":
						panic("upstream panic")
					default:
						return cliproxyexecutor.Response{Payload: []byte("ok")}, nil
					}
				},
			})
			if name == "cancel" {
				go func() {
					<-entered
					cancel()
				}()
			}
			func() {
				if name == "panic" {
					defer func() { _ = recover() }()
				}
				_, _ = manager.Execute(ctx, []string{"codex"}, cliproxyexecutor.Request{Model: model}, cliproxyexecutor.Options{})
			}()
			select {
			case <-entered:
			default:
				t.Fatal("executor was not entered")
			}
			if got := manager.accountInFlight(id); got != 0 {
				t.Fatalf("in-flight = %d, want 0", got)
			}
		})
	}
}

func TestAccountPacingInFlightDoesNotExceedLimit(t *testing.T) {
	model := "pace-race-" + uuid.NewString()
	manager, _ := newPacingManager(t, model, 0, 2, 0)
	id := registerPacingAuth(t, manager, "pace-race", model)
	var current, peak atomic.Int32
	release := make(chan struct{})
	manager.RegisterExecutor(&mockCustomErrorExecutor{
		identifier: "codex",
		executeFn: func(context.Context, *Auth, cliproxyexecutor.Request, cliproxyexecutor.Options) (cliproxyexecutor.Response, error) {
			n := current.Add(1)
			defer current.Add(-1)
			for {
				seen := peak.Load()
				if n <= seen || peak.CompareAndSwap(seen, n) {
					break
				}
			}
			<-release
			return cliproxyexecutor.Response{Payload: []byte("ok")}, nil
		},
	})

	var wg sync.WaitGroup
	for range 6 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, _ = manager.Execute(context.Background(), []string{"codex"}, cliproxyexecutor.Request{Model: model}, cliproxyexecutor.Options{})
		}()
	}
	var finished sync.Once
	finish := func() {
		finished.Do(func() {
			close(release)
			wg.Wait()
		})
	}
	defer finish()
	deadline := time.Now().Add(2 * time.Second)
	for manager.accountInFlight(id) < 2 && time.Now().Before(deadline) {
		time.Sleep(5 * time.Millisecond)
	}
	time.Sleep(50 * time.Millisecond)
	if peak.Load() > 2 {
		t.Fatalf("peak in executor = %d, want <= 2", peak.Load())
	}
	if manager.accountInFlight(id) != 2 {
		t.Fatalf("reserved = %d, want 2", manager.accountInFlight(id))
	}
	finish()
	if got := manager.accountInFlight(id); got != 0 {
		t.Fatalf("in-flight after release = %d, want 0", got)
	}
}

func TestAccountPacingQuotaThreshold(t *testing.T) {
	model := "pace-quota-" + uuid.NewString()
	manager, calls := newPacingManager(t, model, 0, 0, 85)
	id := registerPacingAuth(t, manager, "pace-quota", model)
	ctx := context.Background()
	req := cliproxyexecutor.Request{Model: model}

	setUsed := func(percent string) {
		current, ok := manager.GetByID(id)
		if !ok {
			t.Fatal("missing auth")
		}
		if percent == "" {
			current.Quota.Signals = nil
		} else {
			current.Quota.Signals = map[string]string{"X-Codex-Primary-Used-Percent": percent}
		}
		if _, err := manager.Update(ctx, current); err != nil {
			t.Fatal(err)
		}
	}

	setUsed("90")
	if _, err := manager.Execute(ctx, []string{"codex"}, req, cliproxyexecutor.Options{}); authErrorCode(err) != "auth_not_found" || calls.Load() != 0 {
		t.Fatalf("over threshold: err=%v calls=%d", err, calls.Load())
	}
	setUsed("85")
	if _, err := manager.Execute(ctx, []string{"codex"}, req, cliproxyexecutor.Options{}); authErrorCode(err) != "auth_not_found" || calls.Load() != 0 {
		t.Fatalf("at threshold: err=%v calls=%d", err, calls.Load())
	}
	setUsed("84")
	if _, err := manager.Execute(ctx, []string{"codex"}, req, cliproxyexecutor.Options{}); err != nil || calls.Load() != 1 {
		t.Fatalf("under threshold: err=%v calls=%d", err, calls.Load())
	}
	setUsed("")
	if _, err := manager.Execute(ctx, []string{"codex"}, req, cliproxyexecutor.Options{}); err != nil || calls.Load() != 2 {
		t.Fatalf("no observation: err=%v calls=%d", err, calls.Load())
	}
}

func TestAccountPacingSessionAffinityDoesNotRebind(t *testing.T) {
	model := "pace-affinity-" + uuid.NewString()
	selector := NewSessionAffinitySelector(&RoundRobinSelector{})
	t.Cleanup(selector.Stop)
	manager := NewManager(nil, selector, nil)
	manager.SetRetryConfig(3, 30*time.Second, 0)
	clock := time.Date(2026, 10, 31, 2, 0, 0, 0, time.UTC)
	manager.setAccountPacingClock(func() time.Time { return clock })
	manager.SetAccountPacingConfig(time.Hour, 0, 0, 0, 0)
	var calls atomic.Int32
	manager.RegisterExecutor(&mockCustomErrorExecutor{
		identifier: "codex",
		executeFn: func(_ context.Context, auth *Auth, _ cliproxyexecutor.Request, _ cliproxyexecutor.Options) (cliproxyexecutor.Response, error) {
			calls.Add(1)
			return cliproxyexecutor.Response{Payload: []byte(auth.ID)}, nil
		},
	})
	first := registerPacingAuth(t, manager, "pace-a", model)
	second := registerPacingAuth(t, manager, "pace-b", model)
	opts := cliproxyexecutor.Options{Headers: http.Header{"X-Session-Id": []string{"sticky-session"}}}
	ctx := context.Background()
	resp, err := manager.Execute(ctx, []string{"codex"}, cliproxyexecutor.Request{Model: model}, opts)
	if err != nil {
		t.Fatal(err)
	}
	bound := string(resp.Payload)
	if bound != first && bound != second {
		t.Fatalf("bound = %q", bound)
	}
	before := calls.Load()
	start := time.Now()
	_, err = manager.Execute(ctx, []string{"codex"}, cliproxyexecutor.Request{Model: model}, opts)
	if authErrorCode(err) != "auth_not_found" {
		t.Fatalf("affinity hit = %v, want auth_not_found", err)
	}
	if time.Since(start) > 200*time.Millisecond {
		t.Fatal("affinity hit entered a retry wait")
	}
	if calls.Load() != before {
		t.Fatal("affinity hit attempted another upstream call")
	}
	if got, ok := selector.boundAuthID("mixed", model, opts); !ok || got != bound {
		t.Fatalf("binding = %q ok=%v, want %s", got, ok, bound)
	}
}

func TestAccountPacingWithoutAffinitySelectsAnotherAccount(t *testing.T) {
	model := "pace-switch-" + uuid.NewString()
	manager := NewManager(nil, &FillFirstSelector{}, nil)
	manager.SetAccountPacingConfig(time.Hour, 0, 0, 0, 0)
	var calls atomic.Int32
	manager.RegisterExecutor(&mockCustomErrorExecutor{
		identifier: "codex",
		executeFn: func(_ context.Context, auth *Auth, _ cliproxyexecutor.Request, _ cliproxyexecutor.Options) (cliproxyexecutor.Response, error) {
			calls.Add(1)
			return cliproxyexecutor.Response{Payload: []byte(auth.ID)}, nil
		},
	})
	high := registerPacingAuthWithPriority(t, manager, "pace-high", model, "4")
	low := registerPacingAuthWithPriority(t, manager, "pace-low", model, "3")
	ctx := context.Background()
	req := cliproxyexecutor.Request{Model: model}
	first, err := manager.Execute(ctx, []string{"codex"}, req, cliproxyexecutor.Options{})
	if err != nil {
		t.Fatal(err)
	}
	second, err := manager.Execute(ctx, []string{"codex"}, req, cliproxyexecutor.Options{})
	if err != nil {
		t.Fatal(err)
	}
	if string(first.Payload) != high || string(second.Payload) != low {
		t.Fatalf("selection = %s then %s, want %s then %s", first.Payload, second.Payload, high, low)
	}
	if calls.Load() != 2 {
		t.Fatalf("calls = %d, want 2", calls.Load())
	}
}

func TestAccountPacingStreamHoldsInFlightUntilForwardingEnds(t *testing.T) {
	model := "pace-stream-" + uuid.NewString()
	manager, _ := newPacingManager(t, model, 0, 1, 0)
	id := registerPacingAuth(t, manager, "pace-stream", model)
	var unary atomic.Int32
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	manager.RegisterExecutor(&customStreamMockExecutor{
		identifier: "codex",
		mockCustomErrorExecutor: mockCustomErrorExecutor{
			executeFn: func(context.Context, *Auth, cliproxyexecutor.Request, cliproxyexecutor.Options) (cliproxyexecutor.Response, error) {
				unary.Add(1)
				return cliproxyexecutor.Response{Payload: []byte("unary")}, nil
			},
		},
		streamFn: func(streamCtx context.Context, _ *Auth, _ cliproxyexecutor.Request, _ cliproxyexecutor.Options) (*cliproxyexecutor.StreamResult, error) {
			chunks := make(chan cliproxyexecutor.StreamChunk)
			go func() {
				defer close(chunks)
				chunks <- cliproxyexecutor.StreamChunk{Payload: []byte("hi")}
				<-streamCtx.Done()
			}()
			return &cliproxyexecutor.StreamResult{Chunks: chunks}, nil
		},
	})
	result, err := manager.ExecuteStream(ctx, []string{"codex"}, cliproxyexecutor.Request{Model: model}, cliproxyexecutor.Options{})
	if err != nil {
		t.Fatal(err)
	}
	if manager.accountInFlight(id) != 1 {
		t.Fatalf("in-flight while stream is open = %d, want 1", manager.accountInFlight(id))
	}
	_, err = manager.Execute(context.Background(), []string{"codex"}, cliproxyexecutor.Request{Model: model}, cliproxyexecutor.Options{})
	if authErrorCode(err) != "auth_not_found" || unary.Load() != 0 {
		t.Fatalf("overlap err=%v unary=%d", err, unary.Load())
	}
	cancel()
	if result != nil && result.Chunks != nil {
		for range result.Chunks {
		}
	}
	deadline := time.Now().Add(2 * time.Second)
	for manager.accountInFlight(id) != 0 && time.Now().Before(deadline) {
		time.Sleep(5 * time.Millisecond)
	}
	if got := manager.accountInFlight(id); got != 0 {
		t.Fatalf("in-flight after stream end = %d, want 0", got)
	}
}

func TestAccountPacingAllIneligibleSkipsUpstreamAndRetry(t *testing.T) {
	model := "pace-none-" + uuid.NewString()
	manager, calls := newPacingManager(t, model, time.Hour, 0, 0)
	manager.SetRetryConfig(3, 30*time.Second, 0)
	id := registerPacingAuth(t, manager, "pace-only", model)
	manager.noteAccountPacingSuccess(id, manager.pacingNow())
	start := time.Now()
	_, err := manager.Execute(context.Background(), []string{"codex"}, cliproxyexecutor.Request{Model: model}, cliproxyexecutor.Options{})
	if authErrorCode(err) != "auth_not_found" {
		t.Fatalf("err = %v, want auth_not_found", err)
	}
	if calls.Load() != 0 {
		t.Fatalf("upstream calls = %d, want 0", calls.Load())
	}
	if time.Since(start) > 200*time.Millisecond {
		t.Fatal("all-ineligible result entered a retry wait")
	}
	current, _ := manager.GetByID(id)
	if current.Unavailable || !current.NextRetryAfter.IsZero() || current.Failed != 0 {
		t.Fatalf("pacing wrote cooldown state: unavailable=%v retry=%v failed=%d", current.Unavailable, current.NextRetryAfter, current.Failed)
	}
}

func TestAccountPacingZeroConfigDoesNotBlock(t *testing.T) {
	model := "pace-off-" + uuid.NewString()
	manager, calls := newPacingManager(t, model, 0, 0, 0)
	registerPacingAuth(t, manager, "pace-off", model)
	req := cliproxyexecutor.Request{Model: model}
	for i := 0; i < 2; i++ {
		if _, err := manager.Execute(context.Background(), []string{"codex"}, req, cliproxyexecutor.Options{}); err != nil {
			t.Fatal(err)
		}
	}
	if calls.Load() != 2 {
		t.Fatalf("calls = %d, want 2", calls.Load())
	}
}

func TestAccountPacingReloadChangesEffectiveGate(t *testing.T) {
	model := "pace-reload-" + uuid.NewString()
	manager, calls := newPacingManager(t, model, time.Hour, 0, 0)
	id := registerPacingAuth(t, manager, "pace-reload", model)
	manager.noteAccountPacingSuccess(id, manager.pacingNow())
	if _, err := manager.Execute(context.Background(), []string{"codex"}, cliproxyexecutor.Request{Model: model}, cliproxyexecutor.Options{}); authErrorCode(err) != "auth_not_found" {
		t.Fatal(err)
	}
	manager.SetAccountPacingConfig(0, 0, 0, 0, 0)
	if _, err := manager.Execute(context.Background(), []string{"codex"}, cliproxyexecutor.Request{Model: model}, cliproxyexecutor.Options{}); err != nil || calls.Load() != 1 {
		t.Fatalf("after reload err=%v calls=%d", err, calls.Load())
	}
}

func TestAccountPacingIgnoredWhenHomeEnabled(t *testing.T) {
	manager := NewManager(nil, nil, nil)
	manager.runtimeConfig.Store(&internalconfig.Config{Home: internalconfig.HomeConfig{Enabled: true}})
	manager.SetAccountPacingConfig(time.Hour, 0, 1, 50, 0)
	auth := &Auth{ID: "pace-home", Provider: "codex", Quota: QuotaState{Signals: map[string]string{"X-Codex-Primary-Used-Percent": "99"}}}
	manager.noteAccountPacingSuccess(auth.ID, time.Now())
	if !manager.accountPacingAllows(auth, time.Now()) {
		t.Fatal("home mode applied local pacing")
	}
}

func TestAccountPacingIntervalWaitThenSucceeds(t *testing.T) {
	model := "pace-wait-" + uuid.NewString()
	manager, calls := newPacingManager(t, model, 120*time.Millisecond, 0, 0)
	manager.SetAccountPacingConfig(120*time.Millisecond, 0, 0, 0, time.Second)
	registerPacingAuth(t, manager, "pace-wait", model)
	req := cliproxyexecutor.Request{Model: model}
	if _, err := manager.Execute(context.Background(), []string{"codex"}, req, cliproxyexecutor.Options{}); err != nil {
		t.Fatal(err)
	}
	start := time.Now()
	if _, err := manager.Execute(context.Background(), []string{"codex"}, req, cliproxyexecutor.Options{}); err != nil {
		t.Fatalf("second = %v, want success after one wait", err)
	}
	if calls.Load() != 2 {
		t.Fatalf("calls = %d, want 2", calls.Load())
	}
	if elapsed := time.Since(start); elapsed > time.Second || elapsed < 40*time.Millisecond {
		t.Fatalf("second took %s, want one wait inside max-wait", elapsed)
	}
}

func TestAccountPacingIntervalBeyondMaxWaitRejectsImmediately(t *testing.T) {
	model := "pace-past-" + uuid.NewString()
	manager, calls := newPacingManager(t, model, 2*time.Second, 0, 0)
	manager.SetAccountPacingConfig(2*time.Second, 0, 0, 0, 40*time.Millisecond)
	registerPacingAuth(t, manager, "pace-past", model)
	req := cliproxyexecutor.Request{Model: model}
	if _, err := manager.Execute(context.Background(), []string{"codex"}, req, cliproxyexecutor.Options{}); err != nil {
		t.Fatal(err)
	}
	start := time.Now()
	_, err := manager.Execute(context.Background(), []string{"codex"}, req, cliproxyexecutor.Options{})
	if authErrorCode(err) != "auth_not_found" || calls.Load() != 1 {
		t.Fatalf("err=%v calls=%d", err, calls.Load())
	}
	if time.Since(start) > 150*time.Millisecond {
		t.Fatal("remaining interval past max-wait still waited")
	}
}

func TestAccountPacingQuotaDoesNotWait(t *testing.T) {
	model := "pace-quota-nowait-" + uuid.NewString()
	manager, calls := newPacingManager(t, model, 0, 0, 85)
	manager.SetAccountPacingConfig(0, 0, 0, 85, 2*time.Second)
	id := registerPacingAuth(t, manager, "pace-quota-nowait", model)
	current, ok := manager.GetByID(id)
	if !ok {
		t.Fatal("missing auth")
	}
	current.Quota.Signals = map[string]string{"X-Codex-Primary-Used-Percent": "90"}
	if _, err := manager.Update(context.Background(), current); err != nil {
		t.Fatal(err)
	}
	start := time.Now()
	_, err := manager.Execute(context.Background(), []string{"codex"}, cliproxyexecutor.Request{Model: model}, cliproxyexecutor.Options{})
	if authErrorCode(err) != "auth_not_found" || calls.Load() != 0 {
		t.Fatalf("err=%v calls=%d", err, calls.Load())
	}
	if time.Since(start) > 150*time.Millisecond {
		t.Fatal("quota block entered an interval wait")
	}
}

func TestAccountPacingWaitsOnceThenRejects(t *testing.T) {
	model := "pace-once-" + uuid.NewString()
	manager, calls := newPacingManager(t, model, 200*time.Millisecond, 0, 85)
	manager.SetAccountPacingConfig(200*time.Millisecond, 0, 0, 85, 2*time.Second)
	id := registerPacingAuth(t, manager, "pace-once", model)
	manager.noteAccountPacingSuccess(id, time.Now().Add(-50*time.Millisecond))
	go func() {
		time.Sleep(30 * time.Millisecond)
		current, ok := manager.GetByID(id)
		if !ok {
			return
		}
		current.Quota.Signals = map[string]string{"X-Codex-Primary-Used-Percent": "90"}
		_, _ = manager.Update(context.Background(), current)
	}()
	start := time.Now()
	_, err := manager.Execute(context.Background(), []string{"codex"}, cliproxyexecutor.Request{Model: model}, cliproxyexecutor.Options{})
	elapsed := time.Since(start)
	if authErrorCode(err) != "auth_not_found" || calls.Load() != 0 {
		t.Fatalf("err=%v calls=%d", err, calls.Load())
	}
	if elapsed < 60*time.Millisecond || elapsed > 700*time.Millisecond {
		t.Fatalf("elapsed = %s, want one interval wait and no second wait", elapsed)
	}
}

func TestAccountPacingAffinityWaitsForBoundAccount(t *testing.T) {
	model := "pace-affinity-wait-" + uuid.NewString()
	selector := NewSessionAffinitySelector(&RoundRobinSelector{})
	t.Cleanup(selector.Stop)
	manager := NewManager(nil, selector, nil)
	manager.SetRetryConfig(3, 30*time.Second, 0)
	manager.SetAccountPacingConfig(80*time.Millisecond, 0, 0, 0, time.Second)
	manager.RegisterExecutor(&mockCustomErrorExecutor{
		identifier: "codex",
		executeFn: func(_ context.Context, auth *Auth, _ cliproxyexecutor.Request, _ cliproxyexecutor.Options) (cliproxyexecutor.Response, error) {
			return cliproxyexecutor.Response{Payload: []byte(auth.ID)}, nil
		},
	})
	registerPacingAuth(t, manager, "pace-bound-a", model)
	registerPacingAuth(t, manager, "pace-bound-b", model)
	opts := cliproxyexecutor.Options{Headers: http.Header{"X-Session-Id": []string{"sticky-wait"}}}
	first, err := manager.Execute(context.Background(), []string{"codex"}, cliproxyexecutor.Request{Model: model}, opts)
	if err != nil {
		t.Fatal(err)
	}
	start := time.Now()
	second, err := manager.Execute(context.Background(), []string{"codex"}, cliproxyexecutor.Request{Model: model}, opts)
	if err != nil {
		t.Fatalf("second = %v, want the bound account after one wait", err)
	}
	if string(second.Payload) != string(first.Payload) {
		t.Fatalf("second = %s, want bound %s", second.Payload, first.Payload)
	}
	if got, ok := selector.boundAuthID("mixed", model, opts); !ok || got != string(first.Payload) {
		t.Fatalf("binding = %q ok=%v, want %s", got, ok, first.Payload)
	}
	if elapsed := time.Since(start); elapsed > time.Second || elapsed < 20*time.Millisecond {
		t.Fatalf("second took %s, want one wait inside max-wait", elapsed)
	}
}

func TestAccountPacingWaitHonorsCancel(t *testing.T) {
	model := "pace-cancel-" + uuid.NewString()
	manager, calls := newPacingManager(t, model, 2*time.Second, 0, 0)
	manager.SetAccountPacingConfig(2*time.Second, 0, 0, 0, 3*time.Second)
	id := registerPacingAuth(t, manager, "pace-cancel", model)
	manager.noteAccountPacingSuccess(id, time.Now())
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	before := runtime.NumGoroutine()
	errCh := make(chan error, 1)
	go func() {
		_, err := manager.Execute(ctx, []string{"codex"}, cliproxyexecutor.Request{Model: model}, cliproxyexecutor.Options{})
		errCh <- err
	}()
	time.Sleep(30 * time.Millisecond)
	cancel()
	var err error
	select {
	case err = <-errCh:
	case <-time.After(time.Second):
		t.Fatal("cancel during wait did not return")
	}
	if !errors.Is(err, context.Canceled) || calls.Load() != 0 {
		t.Fatalf("err=%v calls=%d, want context.Canceled and no upstream call", err, calls.Load())
	}
	time.Sleep(50 * time.Millisecond)
	if got := runtime.NumGoroutine(); got > before+2 {
		t.Fatalf("goroutines = %d, before = %d", got, before)
	}
}

func TestAccountPacingMaxWaitZeroRejectsImmediately(t *testing.T) {
	model := "pace-nowait-" + uuid.NewString()
	manager, calls := newPacingManager(t, model, 80*time.Millisecond, 0, 0)
	registerPacingAuth(t, manager, "pace-nowait", model)
	req := cliproxyexecutor.Request{Model: model}
	if _, err := manager.Execute(context.Background(), []string{"codex"}, req, cliproxyexecutor.Options{}); err != nil {
		t.Fatal(err)
	}
	start := time.Now()
	_, err := manager.Execute(context.Background(), []string{"codex"}, req, cliproxyexecutor.Options{})
	if authErrorCode(err) != "auth_not_found" || calls.Load() != 1 {
		t.Fatalf("err=%v calls=%d", err, calls.Load())
	}
	if time.Since(start) > 150*time.Millisecond {
		t.Fatal("max-wait 0 entered an interval wait")
	}
}

func newPacingManager(t *testing.T, model string, interval time.Duration, inFlight, quota int) (*Manager, *atomic.Int32) {
	t.Helper()
	manager := NewManager(nil, nil, nil)
	manager.SetAccountPacingConfig(interval, 0, inFlight, quota, 0)
	var calls atomic.Int32
	manager.RegisterExecutor(&mockCustomErrorExecutor{
		identifier: "codex",
		executeFn: func(_ context.Context, auth *Auth, _ cliproxyexecutor.Request, _ cliproxyexecutor.Options) (cliproxyexecutor.Response, error) {
			calls.Add(1)
			return cliproxyexecutor.Response{Payload: []byte(auth.ID)}, nil
		},
	})
	t.Cleanup(func() {
		registry.GetGlobalRegistry().UnregisterClient("unused-" + model)
	})
	return manager, &calls
}

func registerPacingAuth(t *testing.T, manager *Manager, id, model string) string {
	t.Helper()
	return registerPacingAuthWithPriority(t, manager, id, model, "")
}

func registerPacingAuthWithPriority(t *testing.T, manager *Manager, id, model, priority string) string {
	t.Helper()
	registry.GetGlobalRegistry().RegisterClient(id, "codex", []*registry.ModelInfo{{ID: model}})
	t.Cleanup(func() { registry.GetGlobalRegistry().UnregisterClient(id) })
	auth := &Auth{ID: id, Provider: "codex", Status: StatusActive}
	if priority != "" {
		auth.Attributes = map[string]string{"priority": priority}
	}
	if _, err := manager.Register(context.Background(), auth); err != nil {
		t.Fatal(err)
	}
	return id
}

func authErrorCode(err error) string {
	var authErr *Error
	if errors.As(err, &authErr) && authErr != nil {
		return authErr.Code
	}
	return ""
}
