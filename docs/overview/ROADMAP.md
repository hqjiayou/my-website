# ROADMAP

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-10-28
```

未完成工程 / 产品工作以 **GitHub Issues** 为唯一跟踪源。本文只保留 Issue 号 + 短状态，不复制完整 TODO。

## P1 — 安全 / 核心产品缺口

| 主题 | 状态 | Issue |
|------|------|-------|
| Auth trusted-proxy / XFF / rate-limit boundary | RESOLVED（2026-10-30：不信任转发头，限流键取 TCP 对端；Issue #1 的「收窄正则」模型已由 #136 取代） | [#136](https://github.com/hqjiayou/my-website/issues/136) |
| 站点用户 Auth 前端接入 | RESOLVED（superseded：site-web 已移除，整套登录已在 agent-portal 落地，Issue 已关闭） | [#3](https://github.com/hqjiayou/my-website/issues/3) |

## P2 — 测试 / 阻塞中的产品能力

| 主题 | 状态 | Issue |
|------|------|-------|
| Browser golden-path E2E | LANDED（PR #111 合入，Issue 已关闭）；首次 CI 实跑待 2026-11-01 Actions 额度重置后 workflow_dispatch 补跑 | [#2](https://github.com/hqjiayou/my-website/issues/2) |
| AI LIVE provider integration | RESOLVED（2026-10-25 本机实证，Issue 已关闭；部署环境各自配 key） | [#5](https://github.com/hqjiayou/my-website/issues/5) |

## P3 — 技术债

| 主题 | 状态 | Issue |
|------|------|-------|
| Soft-delete `deleted_at` 一致性（Site/Appearance/User） | RESOLVED（Role / 绑定不在 #4） | [#4](https://github.com/hqjiayou/my-website/issues/4) |

## 明确不做 / 延期（无 Issue）

| 主题 | 状态 | 理由 |
|------|------|------|
| Template 功能 | REMOVED（2026-10-27，PR #72） | 种子软删 + 后端/后台页移除，不再是延期项 |
| Reservation 功能 | REMOVED（2026-10-27） | 整包删除；规格文档已于 2026-10-29 随历史文档一并移除，见 git 历史 |
| Site runtime-status REAL | DEFERRED | UI 在 REAL 下已诚实占位；产品确认后再开 Issue |
| Analytics / System 业务 | PLACEHOLDER | 有意占位 |

## 进行中

| 主题 | 状态 | Issue |
|------|------|-------|
| Portal 双模式（CLI / Key）切换 | 进行中（UoW-1 后端 PR #109、UoW-2 前端 PR #114 已合入 dev） | [#110](https://github.com/hqjiayou/my-website/issues/110) |
| 知识库向量按嵌入模型分库，检索跨库合并 | 未做 | [#126](https://github.com/hqjiayou/my-website/issues/126) |
| 试聊与接入模型不一致、切换后无响应 | 调查已记录；对应改动在工作区，未提交 | [#127](https://github.com/hqjiayou/my-website/issues/127) |
| CLI 代理按账号限制在途请求与成功请求间隔 | 已实现（资格过滤；补丁见 `services/proxy-services/docs/LOCAL_PATCHES.md`） | [#152](https://github.com/hqjiayou/my-website/issues/152) |
| 同一会话按轮切换对话模型 | 进行中 | [#153](https://github.com/hqjiayou/my-website/issues/153) |

## 建议下一阶段顺序

以 GitHub Issues 当前 open 列表为准；本文不再维护静态顺序（上一版列的 #1/#2/#3/#5 均已关闭）。
