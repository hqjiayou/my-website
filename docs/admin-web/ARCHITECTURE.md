# admin-web ARCHITECTURE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-10-10
```

- **栈**：React + Vite + TypeScript + TanStack Query + Zustand + i18n  
- **入口**：`src/main.tsx` → router → `layouts` → `pages/<domain>`  
- **API**：`src/api/**`；页面不直写 axios  
- **HTTP**：`src/utils/http/`（unwrap ApiResponse、401/403 统一）  
- **权限**：路由 guard + permission hooks；菜单可见性 ≠ API 授权  
- **样式**：`assets/styles/{globals,theme,font}.css` 三层  

细节命名与目录边界见 [`开发规范.md`](./开发规范.md)。历史设计稿已归档，勿再当 SoT。
