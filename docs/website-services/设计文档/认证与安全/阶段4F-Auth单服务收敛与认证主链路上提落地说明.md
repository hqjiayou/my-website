# 阶段 4F：Auth 单服务收敛与认证主链路上提（落地说明）

> **文档状态**：当前有效（落地记录；架构决策脉络见 [阶段4D](./阶段4D-Auth独立认证服务架构回归设计与迁移方案.md)）  
> **对应阶段**：4F  
> **当前适用性**：描述 **当前仓库真实结构**；实施 4G/4H 前请先读本文与 [架构设计](../基础架构/架构设计.md)。  
> **最近同步**：2026-05-17  

---

## 1. 背景（与 4E 的差别）

- **4E** 曾采用 **`auth-core` + `auth-app`** 双模块与 **`auth` 父 POM** 聚合，认证主链路仍在 **admin**。  
- **4F** **已撤销**上述拆分，**仅保留单一可执行模块 `auth`**（`AuthApplication`），**完整承接** Admin **Login / Refresh / Logout**、Access JWT **签发（HS256 过渡）**、Refresh Cookie、**Redis** 会话轮换与重放防御。  
- **顶层 Maven `config` 模块已删除**（网站配置等业务预留不再单独占模块名；未来能力放回业务域或新模块时需另起设计）。

---

## 2. 实际落地结果（代码事实）

| 项 | 结果 |
|----|------|
| **可执行模块** | `gateway`、`auth`、`admin`、`site/site-app` |
| **Auth 端口** | 默认 **8083**（`AUTH_APP_PORT`） |
| **Gateway** | **`/api/admin/auth/**`、`/api/site/auth/**` → `auth`；**`/api/admin/**`（普通业务）→ `admin`；**`/api/site/**` → `site-app`** |
| **Admin** | **OAuth2 Resource Server** + 业务 API；**无** `/api/admin/auth/*` 编排职责 |
| **Flyway** | **Auth** 显式关闭；DDL 仍由 **admin**（等）执行，Auth **可读**同库用户表 |
| **共享契约** | **`com.mywebsite.common.security`**（Claims、Audience、`AuthenticatedUser` 等） |
| **测试** | Auth/Admin 冒烟与契约测随模块迁移；**`mvn -pl auth -am`** 确保 `common`/`infra` 在类路径 |

---

## 3. Admin「瘦身」摘要

- 移除 **认证主链路控制器/服务** 自 Admin 的 **对外职责**（实现细节以代码为准）：业务服务 **仅验 JWT** 与授权。  
- **用户表**仍落在共享 schema；**Auth** 自持读 `admin_user` 的 Mapper，**不**依赖 `admin` JAR。

---

## 4. 环境与密钥（4F～4G 过渡风险）

- **`SECURITY_JWT_SECRET`**：过渡期为 **HS256 对称密钥**，**auth（签发）** 与 **admin（验签）** 须一致；**不得提交真实生产密钥**。  
- **4G** 后：签发侧改为 **RS256 + JWKS**，业务服务 **不再持有** 签名私钥与对称 secret。

---

## 5. 相关文档

- [阶段4D-Auth独立认证服务架构回归设计与迁移方案.md](./阶段4D-Auth独立认证服务架构回归设计与迁移方案.md)（§「4F 实施结果」已并入双向引用）  
- [架构设计](../基础架构/架构设计.md)  
- [联调说明/阶段4C](../联调说明/阶段4C-Admin认证真实联调与安全边界验证.md)（历史 HTTP 步骤 + **4F 后入口变化**）  
- [`services/website-services/README.md`](../../../services/website-services/README.md)  
