# LOCAL_DEVELOPMENT

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-08-09
```

## 后端

详见 [`../../services/website-services/README.md`](../../services/website-services/README.md)（`.env`、端口、启动顺序）。

推荐外部入口：**gateway :8080**。

WSL/Docker：`docs/website-services/设计文档/基础架构/WSL与Docker环境配置说明.md`。

## 前端 admin-web

```bash
cd apps/admin-web
pnpm install
pnpm dev
```

核心域 FORCE REAL——本地需可用的 gateway/auth/admin。`VITE_USE_MOCK` 仅影响 AI/runtime 等非 FORCE REAL 域。

## site-web

各子应用独立 README；认证对接尚未完成。
