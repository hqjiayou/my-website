package config

import (
	"strings"
	"time"
)

const (
	defaultAccountPacingMinInterval = 500 * time.Millisecond
	defaultAccountPacingJitter      = 200 * time.Millisecond
	defaultAccountPacingMaxInFlight = 0
	defaultAccountPacingQuotaUsed   = 85
	defaultAccountPacingMaxWait     = 3 * time.Second
)

// EffectiveAccountPacing resolves routing.account-pacing into the values applied to the auth manager.
// Omitted fields use the defaults. Explicit 0 disables that gate. Invalid durations keep the default.
// max-wait 0 disables the one-shot interval pause and restores immediate rejection.
func (cfg *Config) EffectiveAccountPacing() (minInterval, jitter time.Duration, maxInFlight, quotaUsedPercent int, maxWait time.Duration) {
	if cfg == nil {
		return defaultAccountPacingMinInterval, defaultAccountPacingJitter, defaultAccountPacingMaxInFlight, defaultAccountPacingQuotaUsed, defaultAccountPacingMaxWait
	}
	return resolveAccountPacing(cfg.Routing.AccountPacing)
}

func resolveAccountPacing(cfg AccountPacingConfig) (minInterval, jitter time.Duration, maxInFlight, quotaUsedPercent int, maxWait time.Duration) {
	minInterval = parseAccountPacingDuration(cfg.MinInterval, defaultAccountPacingMinInterval)
	jitter = parseAccountPacingDuration(cfg.MinIntervalJitter, defaultAccountPacingJitter)
	maxInFlight = parseAccountPacingInt(cfg.MaxInFlight, defaultAccountPacingMaxInFlight)
	quotaUsedPercent = parseAccountPacingInt(cfg.QuotaUsedPercent, defaultAccountPacingQuotaUsed)
	if quotaUsedPercent > 100 {
		quotaUsedPercent = 100
	}
	maxWait = parseAccountPacingDuration(cfg.MaxWait, defaultAccountPacingMaxWait)
	return minInterval, jitter, maxInFlight, quotaUsedPercent, maxWait
}

func parseAccountPacingDuration(raw string, fallback time.Duration) time.Duration {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return fallback
	}
	if raw == "0" {
		return 0
	}
	parsed, err := time.ParseDuration(raw)
	if err != nil || parsed < 0 {
		return fallback
	}
	return parsed
}

func parseAccountPacingInt(value *int, fallback int) int {
	if value == nil {
		return fallback
	}
	if *value <= 0 {
		return 0
	}
	return *value
}
