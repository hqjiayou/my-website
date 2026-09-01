# FEATURE_MATRIX

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
```

| Feature | Backend | DB | Admin API | admin-web | site-web | Tests (non-browser) | Browser E2E | Status |
|---------|---------|----|-----------|-----------|----------|---------------------|-------------|--------|
| Admin login/refresh/logout | ✅ | ✅ | ✅ auth | ✅ | — | ✅ MVC | ❌ | PARTIAL（XFF） |
| Site login/refresh/logout/me | ✅ | ✅ | ✅ auth | — | ❌ | ✅ | ❌ | BACKEND_COMPLETE |
| RBAC roles/permissions | ✅ | ✅ | ✅ | ✅ | — | ✅ | ❌ | COMPLETE_V1 |
| Menu read | ✅ | ✅ | ✅ | ✅ | — | ✅ | ❌ | COMPLETE_V1 |
| Menu write | ❌ | seed | ❌ | 只读 | — | — | ❌ | DEFERRED_BY_DESIGN |
| Admin users CRUD | ✅ | ✅ | ✅ | ✅ rbacApi | — | ✅ | ❌ | COMPLETE_V1 |
| Site CRUD/status | ✅ | ✅ | ✅ | FORCE REAL | skeleton | ✅+PG | ❌ | COMPLETE_V1 |
| Site runtime-status | ❌ | ❌ | ❌ | mock/null | — | — | ❌ | PLACEHOLDER |
| Template list/status | ✅ | ✅ | ✅ | FORCE REAL | — | ✅ | ❌ | COMPLETE_V1 |
| Template body CRUD | ❌ | — | ❌ | ❌ | — | — | ❌ | DEFERRED_BY_DESIGN |
| Appearance get/put | ✅ | ✅ | ✅ | FORCE REAL | — | ✅+PG | ❌ | COMPLETE_V1 |
| Media | ✅ | ✅ | ✅ | FORCE REAL | — | ✅ | ❌ | COMPLETE_V1 |
| Content | ✅ | ✅ | ✅ | FORCE REAL | — | ✅ | ❌ | COMPLETE_V1 |
| Reservation admin | ✅ | ✅ | ✅ | FORCE REAL | — | ✅+PG | ❌ | COMPLETE_V1_ADMIN_SCOPE |
| AI admin Prompt/KB/Conversation/Preview | ✅ | ✅ V17–V19 | ✅ | FORCE REAL | ❌ | ✅ MVC | ❌ | COMPLETE_V1_ADMIN_SCOPE |
| AI LIVE provider | path ✅ / env | — | site+admin SSE | preview | ❌ | contract IT | ❌ | EXTERNAL_BLOCKED（无 `AI_MODEL`） |
| Analytics/System | ❌ | ❌ | ❌ | placeholder | — | — | ❌ | PLACEHOLDER |

Legend: ✅ present · ❌ absent · FORCE REAL = 不受 `USE_MOCK` 门控。
