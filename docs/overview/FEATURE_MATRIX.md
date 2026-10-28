# FEATURE_MATRIX

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-28
```

| Feature | Backend | DB | Admin API | admin-web | agent-portal | Tests (non-browser) | Browser E2E | Status |
|---------|---------|----|-----------|-----------|----------|---------------------|-------------|--------|
| Admin login/refresh/logout | ✅ | ✅ | ✅ auth | ✅ | — | ✅ MVC | ✅*（路径 01 登录段） | COMPLETE_V1（XFF 已 RESOLVED，PR #112） |
| Agent login/refresh/logout/me（原 Site Auth） | ✅ | ✅ | ✅ auth | — | ✅ | ✅ | ✅*（路径 02 用到登录） | COMPLETE_V1 |
| RBAC roles/permissions | ✅ | ✅ | ✅ | ✅ | — | ✅ | ❌ | COMPLETE_V1 |
| Menu read | ✅ | ✅ | ✅ | ✅ | — | ✅ | ❌ | COMPLETE_V1 |
| Menu write | ❌ | seed | ❌ | 只读 | — | — | ❌ | DEFERRED_BY_DESIGN |
| Admin users CRUD | ✅ | ✅ | ✅ | ✅ rbacApi | — | ✅ | ✅*（路径 02） | COMPLETE_V1 |
| Agent CRUD/status（原 Site） | ✅ | ✅ | ✅ | FORCE REAL | 登录后消费 | ✅+PG | ✅*（路径 01） | COMPLETE_V1 |
| Site runtime-status | ❌ | ❌ | ❌ | 由 REAL API 推导（访问量 / 当日 AI 会话为 null） | — | — | ❌ | PLACEHOLDER |
| Template list/status | ❌ | 软删（V30） | ❌ | ❌ | — | — | ❌ | REMOVED（2026-10-27，PR #72） |
| Template body CRUD | ❌ | — | ❌ | ❌ | — | — | ❌ | REMOVED（同上；不再是延期） |
| Appearance get/put | ✅ | ✅ | ✅ | FORCE REAL | — | ✅+PG | ❌ | COMPLETE_V1 |
| Media | ✅ | ✅ | ✅ | FORCE REAL | — | ✅ | ✅*（路径 03） | COMPLETE_V1 |
| Content | ✅ | ✅ | ✅ | FORCE REAL | — | ✅ | ✅*（路径 03） | COMPLETE_V1 |
| ~~Reservation admin~~ | ❌ | ❌ | ❌ | ❌ | — | — | ❌ | REMOVED（2026-10-27 整包删除；规格文档已于 2026-10-29 随历史文档一并移除，见 git 历史） |
| AI admin Prompt/KB/Conversation/Preview | ✅ | ✅（迁移至 V35） | ✅ | FORCE REAL | 对话在 Portal | ✅ MVC | ❌ | COMPLETE_V1_ADMIN_SCOPE |
| AI LIVE provider | path ✅ / env | — | agent+admin SSE | preview | ✅ Portal 对话 | contract IT | ❌ | RESOLVED（2026-10-25 本机实证；部署环境需各自配置 key，见 CURRENT_STATE #5） |
| Analytics/System | ❌ | ❌ | ❌ | placeholder | — | — | ❌ | PLACEHOLDER |

Legend: ✅ present · ❌ absent · FORCE REAL = admin-web 该域走真实 API。`VITE_USE_MOCK` 已无消费者。

\* Browser E2E：Playwright 套件已于 PR #111 合入 dev（路径见 [`../testing/E2E_GOLDEN_PATHS.md`](../testing/E2E_GOLDEN_PATHS.md)）；套件首次 CI 实跑待 2026-11-01 Actions 额度重置后补验，未取得真实结论前 ✅* 只表示「有用例覆盖」，不表示已实跑通过。
