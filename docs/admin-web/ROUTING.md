# admin-web ROUTING

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

- 路由配置在 `apps/admin-web/src/router/`（见该目录 README 与 `.cursor/rules/admin-web-router.mdc`）。  
- 页面组件：`pages/**/**Page.tsx`（camelCase 文件名）。  
- 受保护路由需登记实际 ACTION permission。  
- 站点工作区：`sites/:siteId/*` 下 overview、basic、theme、domain、files、contents、reservations、ai。domain 是占位页。  
- 占位（`EmptyModulePage`）：参数配置、分析、系统设置、开发者。AI 设置页只有说明文案。站点 AI、全局 Prompt/知识库/会话已接真实 API，不要标成占位。  
