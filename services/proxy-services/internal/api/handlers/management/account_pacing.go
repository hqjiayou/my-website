package management

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

// GetAccountPacing returns the pacing gates currently applied on the auth manager.
// Values are the effective in-memory settings, not a re-read of the config file.
func (h *Handler) GetAccountPacing(c *gin.Context) {
	var minIntervalMs, jitterMs, maxWaitMs int64
	var maxInFlight, quotaUsedPercent int
	if h != nil && h.authManager != nil {
		minInterval, jitter, inFlight, quota, maxWait := h.authManager.AccountPacingConfig()
		minIntervalMs = minInterval.Milliseconds()
		jitterMs = jitter.Milliseconds()
		maxInFlight = inFlight
		quotaUsedPercent = quota
		maxWaitMs = maxWait.Milliseconds()
	}
	c.JSON(http.StatusOK, gin.H{
		"min-interval-ms":        minIntervalMs,
		"min-interval-jitter-ms": jitterMs,
		"max-in-flight":          maxInFlight,
		"quota-used-percent":     quotaUsedPercent,
		"max-wait-ms":            maxWaitMs,
	})
}
