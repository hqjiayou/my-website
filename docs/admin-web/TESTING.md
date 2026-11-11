# admin-web TESTING

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-11-11
```

```bash
cd apps/admin-web
pnpm lint
pnpm build
node scripts/check-imports.mjs
node scripts/check-import-casing.mjs
npm run test:boundary
npm run test:sse
```

## INT-2A 真实联调（2026-11-11，commit 见 git log）

拓扑（本机）：Gateway `:8080`、Auth `:8083`、Admin `:8081`、agent-app `:8082`、agent-services `:3001`、proxy-services `:8317`、Postgres/Redis/Minio（local-dev）、admin-web Vite `:5173`。**不是** `e2e/docker-compose.e2e.yml` 可抛栈。

真实验证（独立测试 Agent / KEY 入口，测后删除）：Agent CRUD、status、kind、乐观锁 409、KEY 创建/留空 Key 保密/删、平台 `defaultEntry` 不改 Agent chain、CLI `sync-cli` + `derived=true` 409 只读、proxy 可达、OAuth 启动（`antigravity`/`claude`/`codex`）、无角色账号 403。OAuth 完整回调与真实推理属 INT-2C / 外部凭证面，INT-2A 不记完整 SSE PASS。

`test:boundary`：静态扫描 `src/pages/agents/**`，禁止 `createModelEntry` / `updateModelEntry` / `defaultEntry: true`（Agent Console V2 P0；平台默认只允许在模型 API 管理页写入）。

`test:table`：表格底座纯逻辑（排序作用域、列隐藏下限、筛选重置页码、选择集合、批量 available 过滤）。

`test:sse`：Agent 预览 SSE 解析 / 消费纯逻辑（`node:test`，无 DOM / 网络；`scripts/check-preview-sse.ts` 对 `agentAiPreviewSse.ts`）。覆盖：分片 TEXT_DELTA + DONE、帧被任意切分、UTF-8 多字节跨片、ERROR、AbortSignal 取消与取消后新一轮、请求模型 ≠ 实际服务模型、tool/skills/todo/usage/citations 事件、切换 Agent 后旧流事件被代际守卫丢弃。不改后端 SSE 协议。

`test:e2e`：真实栈 golden path（需 docker E2E）。

`test:e2e:mock`：Agent Console V2 冒烟（Mock API + `vite preview`；不证明真实后端；含 `04`–`08`：布局、模型页、配置 ResourcePicker、PR-4.1 未保存离开／后退拦截、PR-5 调试 Inspector / Runs）。

**BROWSER E2E: 真实栈与 mock 冒烟分开标注，不得将 lint/build 称为 E2E PASS。真实联调需 gateway + auth + admin + agent 全起且可登录；仅 infra（postgres/redis/minio）或 login 返回 5xx 时不得记为 PASS。**
