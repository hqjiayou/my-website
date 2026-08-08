> **Status: SUPERSEDED / HISTORICAL**  
> **Superseded by:** docs/admin-web/CURRENT_STATE.md + docs/contracts/  
> Historical snapshot only. Do not use as current implementation truth.  
> Do not rewrite historical PASS/FAIL conclusions in this file.

---
> 功能注解：约束 admin-web 的 API 分组结构、封装方式与注释规范。

# API 开发规范

## 1. 目录分组
- `api/apps/`：站点与模板等业务应用域接口。
- `api/operation/`：内容、文件、预约、用户等运营域接口。
- `api/ai/`：AI 相关接口。
- `api/developer/`：系统参数、分析等开发者域接口。
- `api/index.ts`：统一对外导出，页面优先从 `@/api` 引入。

## 2. 请求封装
- 禁止在业务 API 文件中直接创建 axios 实例。
- 统一复用 `src/utils/http/axios.ts` 的 `httpClient`。
- 接口路径统一使用相对路径（由 axios `baseURL` 管理）。

## 3. Mock 切换策略
- 使用 `VITE_USE_MOCK` 控制 mock/real API 切换。
- API 文件保持 `const xxxApi = USE_MOCK ? mockService : httpService` 模式。

## 4. 注释要求
- 除 `tsx` 文件外，新增或修改的文件必须在首部写“功能注解”。
- 推荐格式：
  - TypeScript: `/** 功能注解：... */`
  - Markdown: `> 功能注解：...`
