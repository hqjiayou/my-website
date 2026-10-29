# admin-web ROUTING

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

- 路由配置在 `apps/admin-web/src/router/`（见该目录 README 与 `.cursor/rules/admin-web-router.mdc`）。  
- 页面组件：`pages/**/**Page.tsx`（camelCase 文件名）。  
- 受保护路由需登记实际 ACTION permission。  
- 智能体列表是 `/agent`，工作区是 `/agent/:agentId/*`。API 管理在 `/agent/api`，挂在 agent 菜单下。旧地址 `/apps/sites` 与 `/ai/proxy-services` 会跳到新地址。域名路径 `/domain` 回到基础信息。  
- 占位（`EmptyModulePage`）：参数配置、分析、系统设置、开发者。AI 设置页只有说明文案。站点 AI、全局 Prompt/知识库/会话已接真实 API，不要标成占位。  
