package config

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"gopkg.in/yaml.v3"
)

func TestEffectiveAccountPacingDefaultsAndExplicitZero(t *testing.T) {
	minInterval, jitter, inFlight, quota := (&Config{}).EffectiveAccountPacing()
	if minInterval != 2*time.Second || jitter != 500*time.Millisecond || inFlight != 2 || quota != 85 {
		t.Fatalf("omitted = %s %s %d %d, want 2s 500ms 2 85", minInterval, jitter, inFlight, quota)
	}

	zero := 0
	cfg := &Config{Routing: RoutingConfig{AccountPacing: AccountPacingConfig{
		MinInterval:       "0s",
		MinIntervalJitter: "0s",
		MaxInFlight:       &zero,
		QuotaUsedPercent:  &zero,
	}}}
	minInterval, jitter, inFlight, quota = cfg.EffectiveAccountPacing()
	if minInterval != 0 || jitter != 0 || inFlight != 0 || quota != 0 {
		t.Fatalf("explicit zero = %s %s %d %d, want all off", minInterval, jitter, inFlight, quota)
	}

	var decoded Config
	raw := []byte("routing:\n  account-pacing:\n    max-in-flight: 0\n    quota-used-percent: 0\n    min-interval: 0s\n    min-interval-jitter: 0s\n")
	if err := yaml.Unmarshal(raw, &decoded); err != nil {
		t.Fatal(err)
	}
	minInterval, jitter, inFlight, quota = decoded.EffectiveAccountPacing()
	if minInterval != 0 || jitter != 0 || inFlight != 0 || quota != 0 {
		t.Fatalf("yaml zero = %s %s %d %d, want all off", minInterval, jitter, inFlight, quota)
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
	minInterval, jitter, inFlight, quota := cfg.EffectiveAccountPacing()
	if minInterval != 2*time.Second || jitter != 500*time.Millisecond || inFlight != 2 || quota != 85 {
		t.Fatalf("example = %s %s %d %d, want 2s 500ms 2 85", minInterval, jitter, inFlight, quota)
	}
}
