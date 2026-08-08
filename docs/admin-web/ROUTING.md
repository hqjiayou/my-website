# admin-web ROUTING

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
```

- 路由配置在 `apps/admin-web/src/router/`（见该目录 README 与 `.cursor/rules/admin-web-router.mdc`）。  
- 页面组件：`pages/**/**Page.tsx`（camelCase 文件名）。  
- 受保护路由需登记实际 ACTION permission。  
- 站点工作区：`sites/:siteId/*` layout + 子页（overview/basic/theme/files/contents/reservations…）。  
- Domain / AI workspace / Analytics 等可为占位，不得标 COMPLETE。  
