# my-website

一个“多站点网站合集平台”代码仓库骨架（各子项目独立维护）。  
第一阶段聚焦日本风格汽车维修工厂模板，后续可扩展到美容院、诊所、中古车店、行政书士事务所等行业站点。

## 项目定位

- 用一套平台同时承载多个行业站点，不是单个官网。
- `site-web` 是前台子站点集合目录，内部按行业拆分独立站点。
- `car-web` 负责汽车维修场景的前台展示和咨询/预约入口。
- `cake-web` 负责烘焙蛋糕场景的前台展示和咨询/预约入口（当前可作为模板基础继续扩展）。
- `admin-web` 负责总后台与站点后台管理。
- `agent-service` 负责 AI/Agent 编排层（问答、SSE、Prompt、模型调用、转调业务服务）。
- `site-service` 负责真实业务域（站点、模板、预约、知识库、文件、统计等）。

## 目录结构

```text
my-website/
  apps/
    site-web/
      car-web/
      cake-web/
    admin-web/

  services/
    agent-service/
    site-service/
```

## 子项目职责

### apps/site-web

- 子网站聚合目录，用于承载多个行业前台站点。
- 当前包含：
  - `car-web`
  - `cake-web`

### apps/site-web/car-web

- 前台站点渲染应用，用于展示不同客户网站。
- 当前内置“日本风格汽车维修工厂”首页模板，包含：
  - 服务介绍区域
  - 维修案例区域
  - 预约咨询按钮
  - AI 咨询入口占位区域

### apps/site-web/cake-web

- 前台站点渲染应用（蛋糕行业子站）。
- 当前可复用与 `car-web` 相同的工程骨架，后续替换为蛋糕行业页面与内容。

### apps/admin-web

- 后台管理系统（总后台 + 站点运营能力）。
- **整体架构与目录约定**见仓库文档：[doc/admin-web/整体架构设计.md](doc/admin-web/整体架构设计.md)。
- 技术要点摘要：React + Vite + Tailwind v4、分组侧栏与路由壳、全局 i18n（`common` / `layout` / `nav`）+ 各 `pages/*/locales` 模块文案、默认语言日语（`ja-JP`）。

### services/agent-service

- Node.js + Fastify + TypeScript 服务，默认端口 `3001`。
- 当前提供接口：
  - `GET /health`
  - `GET /api/site/:domain/config`
  - `POST /api/site/:siteId/reservations`
  - `POST /api/site/:siteId/ai/chat`
- 当前均为 mock 响应，不接真实 AI SDK、不接认证。

### services/site-service

- Java Spring Boot + Maven 服务，默认端口 `8080`。
- 当前提供接口：
  - `GET /health`
- 数据库为 PostgreSQL 预配置（通过环境变量注入连接信息），暂未实现复杂业务逻辑。

## 调用关系说明

- `site-web`（`car-web` / `cake-web`）主要调用 `agent-service`：
  - AI 咨询
  - 前台预约
  - 站点公开配置

- `admin-web` 主要调用 `site-service`：
  - 站点管理
  - 模板管理
  - 预约管理
  - AI 知识库管理
  - 数据统计

- `agent-service` 后续调用 `site-service`：
  - 获取站点配置
  - 获取知识库
  - 保存预约
  - 保存 AI 聊天记录

## 启动命令（各项目独立）

启动 `car-web`：

```bash
cd apps/site-web/car-web
pnpm install
pnpm dev
```

启动 `cake-web`：

```bash
cd apps/site-web/cake-web
pnpm install
pnpm dev
```

启动 `admin-web`：

```bash
cd apps/admin-web
pnpm install
pnpm dev
```

启动 `agent-service`：

```bash
cd services/agent-service
pnpm install
pnpm dev
```

启动 `site-service`：

```bash
cd services/site-service
mvn spring-boot:run
```

## PostgreSQL 环境变量（site-service）

可选设置（默认值见 `application.yml`）：

- `BUSINESS_DB_URL`（默认：`jdbc:postgresql://localhost:5432/my_website`）
- `BUSINESS_DB_USERNAME`（默认：`postgres`）
- `BUSINESS_DB_PASSWORD`（默认：`postgres`）
