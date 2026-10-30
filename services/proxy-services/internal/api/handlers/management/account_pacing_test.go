package management

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/router-for-me/CLIProxyAPI/v8/internal/config"
	coreauth "github.com/router-for-me/CLIProxyAPI/v8/sdk/cliproxy/auth"
)

func TestGetAccountPacingReturnsEffectiveValues(t *testing.T) {
	gin.SetMode(gin.TestMode)
	manager := coreauth.NewManager(nil, nil, nil)
	manager.SetAccountPacingConfig(2*time.Second, 500*time.Millisecond, 2, 85)
	handler := NewHandler(&config.Config{}, "", manager)
	router := gin.New()
	router.GET("/v8/management/routing/account-pacing", handler.GetAccountPacing)

	response := httptest.NewRecorder()
	router.ServeHTTP(response, httptest.NewRequest(http.MethodGet, "/v8/management/routing/account-pacing", nil))
	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, body = %s", response.Code, response.Body.String())
	}
	var body map[string]int64
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	if body["min-interval-ms"] != 2000 || body["min-interval-jitter-ms"] != 500 || body["max-in-flight"] != 2 || body["quota-used-percent"] != 85 {
		t.Fatalf("body = %#v", body)
	}

	manager.SetAccountPacingConfig(0, 0, 0, 0)
	response = httptest.NewRecorder()
	router.ServeHTTP(response, httptest.NewRequest(http.MethodGet, "/v8/management/routing/account-pacing", nil))
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatal(err)
	}
	if body["min-interval-ms"] != 0 || body["min-interval-jitter-ms"] != 0 || body["max-in-flight"] != 0 || body["quota-used-percent"] != 0 {
		t.Fatalf("reloaded body = %#v, want zeros", body)
	}
}
