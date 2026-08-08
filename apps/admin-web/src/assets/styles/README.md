# admin-web 全局样式分层

`src/assets/styles/` **只放全应用共享的样式**。单页、单模块样式放在对应 `pages/**` 或 `components/**`，**不要**放进本目录。

应用入口仅在 `main.tsx` 引入一次：

```ts
import '@/assets/styles/globals.css'
```

## 三层职责

| 文件 | 层级 | 放什么 | 不放什么 |
|------|------|--------|----------|
| `globals.css` | **入口 / 基础** | `@import tailwind`、import 链、`box-sizing`、`body` / `#root` | 颜色 token、字体族、页面 class、组件局部样式 |
| `theme.css` | **主题 / 令牌** | CSS 变量、`.dark`、`data-primary-color` / `data-density`、`@theme inline`、layout 壳层语义类（`.bg-admin-*`、`.admin-control`） | `font-family`、根 `font-size`、某业务页表格/表单样式 |
| `font.css` | **字体 / 排版** | `font-family`、`line-height`、`data-font-size` 根字号 | 颜色、间距 token、布局壳层 class |

## 与偏好配置的关系

`config/preferences.ts` 的 `applyPreferencesToDocument` 写入：

- **theme**：`html.dark`、`data-primary-color`、`data-density` → `theme.css`
- **font**：`data-font-size` → `font.css`

首屏防闪烁脚本见项目根 `index.html`（与上述 `data-*` 一致）。

## 新增样式时的判断

1. 只在一个页面用 → 该页面的 `.module.css` 或 Tailwind 写在组件内  
2. 全站 layout / 设置面板 / 多页复用壳层 → `theme.css` 或 `font.css`  
3. 新的全局 reset 或 import → `globals.css`  

## 禁止

- 在本目录新增 `pages/`、`permissions.css` 等按业务命名的文件  
- 在 `globals.css` 写大段业务选择器  
- 从页面 `import '@/assets/styles/xxx.css'`（除 `globals.css` 外）
