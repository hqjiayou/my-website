# admin-web `router/` 职能划分

## 目录结构

```
router/
├── index.tsx              # AppRouter + 401 会话桥（对外唯一入口）
├── config/
│   └── routePaths.ts      # ROUTES 路径常量（零依赖）
├── routes/
│   ├── appRoutes.tsx      # 守卫嵌套 + Routes 根
│   ├── adminRoutes.tsx    # AdminLayout 下业务 Route 片段
│   └── legacyRedirects.tsx
├── guards/                # guest / auth / permission
├── permission/            # RBAC 工具、hooks、gate
└── navigation/            # 静态 NAV、顶栏标题解析
```

## 接口 vs 静态路由（重要）

| 来源 | 管什么 | 不管什么 |
|------|--------|----------|
| 接口 `auth/me` 的 `menus` | 侧栏显示哪些链接 | 不会自动注册 React Router |
| 接口 `permissions` | 按钮/路由能否访问 | 不决定渲染哪个页面组件 |
| `routes/appRoutes.tsx` | path → 页面组件映射 | 须手写；新页在 `adminRoutes.tsx` 加 `<Route />` |
| `permission/constants.ts` | 部分 path → 权限码 | 与接口 permissions 配合校验 |

**结论：** 菜单走接口；**页面渲染仍走静态路由表**。后端新增菜单 path 时，在 `routes/adminRoutes.tsx` 增加 `<Route />`，并保证 `config/routePaths.ts` 与种子 path 一致。

## 导入约定

```ts
import { ROUTES } from '@/router/config/routePaths'
import { resolveNavTitleKey } from '@/router/navigation'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { AppRouter } from '@/router'
```

禁止从 `@/router` barrel re-export `ROUTES` / `permission`（避免循环依赖）。

## 与 authStore 的区别

| 模块 | 职责 |
|------|------|
| `stores/authStore` | token、用户、menus、permissions **数据** |
| `permission/` | 权限 **判定** |
| `guards/` | 路由 **拦截** |
| `routes/` | 页面 **注册** |

侧栏数据：`utils/menu.ts` 将接口 menus 转为侧栏项。
