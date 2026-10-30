package config

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"gopkg.in/yaml.v3"
)

func TestEffectiveAccountPacingDefaultsAndExplicitZero(t *testing.T) {
	minInterval, jitter, inFlight, quota, maxWait := (&Config{}).EffectiveAccountPacing()
	if minInterval != 500*time.Millisecond || jitter != 200*time.Millisecond || inFlight != 0 || quota != 85 || maxWait != 3*time.Second {
		t.Fatalf("omitted = %s %s %d %d %s, want 500ms 200ms 0 85 3s", minInterval, jitter, inFlight, quota, maxWait)
	}

	zero := 0
	cfg := &Config{Routing: RoutingConfig{AccountPacing: AccountPacingConfig{
		MinInterval:       "0s",
		MinIntervalJitter: "0s",
		MaxInFlight:       &zero,
		QuotaUsedPercent:  &zero,
	}}}
	minInterval, jitter, inFlight, quota, maxWait = cfg.EffectiveAccountPacing()
	if minInterval != 0 || jitter != 0 || inFlight != 0 || quota != 0 || maxWait != 3*time.Second {
		t.Fatalf("explicit zero gates = %s %s %d %d %s, want gates off and default wait", minInterval, jitter, inFlight, quota, maxWait)
	}

	var decoded Config
	raw := []byte("routing:\n  account-pacing:\n    max-in-flight: 0\n    quota-used-percent: 0\n    min-interval: 0s\n    min-interval-jitter: 0s\n")
	if err := yaml.Unmarshal(raw, &decoded); err != nil {
		t.Fatal(err)
	}
	minInterval, jitter, inFlight, quota, maxWait = decoded.EffectiveAccountPacing()
	if minInterval != 0 || jitter != 0 || inFlight != 0 || quota != 0 || maxWait != 3*time.Second {
		t.Fatalf("yaml zero = %s %s %d %d %s, want gates off and default wait", minInterval, jitter, inFlight, quota, maxWait)
	}

	var waitOff Config
	if err := yaml.Unmarshal([]byte("routing:\n  account-pacing:\n    max-wait: 0s\n"), &waitOff); err != nil {
		t.Fatal(err)
	}
	minInterval, jitter, inFlight, quota, maxWait = waitOff.EffectiveAccountPacing()
	if minInterval != 500*time.Millisecond || jitter != 200*time.Millisecond || inFlight != 0 || quota != 85 || maxWait != 0 {
		t.Fatalf("max-wait 0 = %s %s %d %d %s, want defaults with wait off", minInterval, jitter, inFlight, quota, maxWait)
	}
}

func TestExampleConfigCarriesAccountPacingDefaults(t *testing.T) {
	example, err := os.ReadFile(filepath.Join("..", "..", "config.example.yaml"))
	if err != nil {
		t.Fatal(err)
	}
	cfg, err := ParseConfigBytes(example)
	if err != nil {
		t.Fatal(err)
	}
	minInterval, jitter, inFlight, quota, maxWait := cfg.EffectiveAccountPacing()
	if minInterval != 500*time.Millisecond || jitter != 200*time.Millisecond || inFlight != 0 || quota != 85 || maxWait != 3*time.Second {
		t.Fatalf("example = %s %s %d %d %s, want 500ms 200ms 0 85 3s", minInterval, jitter, inFlight, quota, maxWait)
	}
}
