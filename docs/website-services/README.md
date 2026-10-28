# website-services 文档

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-10-29
```

全库分格见 [`../README.md`](../README.md)。本目录只列后端文件落在哪一格。运行命令在 [`../../services/website-services/README.md`](../../services/website-services/README.md)，不在这里复制一份。

## 现在

[`CURRENT_STATE.md`](./CURRENT_STATE.md) 以及域摘要 [`AUTH.md`](./AUTH.md) [`RBAC.md`](./RBAC.md) [`AGENT.md`](./AGENT.md) [`MEDIA.md`](./MEDIA.md) [`CONTENT.md`](./CONTENT.md) （Reservation 已删除，规格文档已于 2026-10-29 随历史文档一并移除，见 git 历史） [`PERSISTENCE.md`](./PERSISTENCE.md)。

完成度只读本目录 `CURRENT_STATE.md` 与各域摘要；原 `总览/` 已于 2026-10-29 移除，见 git 历史。

## 规格

| 问题 | 文件 |
|------|------|
| Maven 依赖 | [`../../services/website-services/README.md`](../../services/website-services/README.md)（模块树与依赖图；原《总览/模块职责与依赖边界》已于 2026-10-29 移除） |
| 模块长文 | [`ARCHITECTURE.md`](./ARCHITECTURE.md)（原《设计文档/基础架构/架构设计》已于 2026-10-29 移除） |
| 新 Java 文件放哪、包名 | [`../../.cursor/rules/backend-structure.mdc`](../../.cursor/rules/backend-structure.mdc)（原 V1 长文两篇已于 2026-10-29 移除，见 git 历史） |
| 表字典 | [`数据字典/`](./数据字典/) |
| 持久层 | [`PERSISTENCE.md`](./PERSISTENCE.md)、[`数据字典/`](./数据字典/)（原《设计文档/数据与持久层》已于 2026-10-29 移除） |
| RBAC 规则 / 菜单 | [`RBAC.md`](./RBAC.md)（原阶段 5A/5E 长文已于 2026-10-29 移除） |
| AI | [`AGENT.md`](./AGENT.md)、[`../decisions/ADR-0004`](../decisions/ADR-0004-ai-must-fail-honestly-without-provider.md)、[`../decisions/ADR-0005`](../decisions/ADR-0005-java-business-truth-python-ai-execution.md)（原 Agent 设计 V1 已于 2026-10-29 移除） |
| 基础设施环境 | [`../operations/LOCAL_DEVELOPMENT.md`](../operations/LOCAL_DEVELOPMENT.md)（原 WSL/Docker 专项已于 2026-10-29 移除） |

原 `设计文档/`（含阶段 4/5 落地记录）与 `规则/` 已于 2026-10-29 整体移除，见 git 历史；现行约束以本目录各域摘要、契约与 `.cursor/rules/` 为准。

## 跳转（没有第二份正文）

| 文件 | 去哪里 |
|------|--------|
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | 架构设计 + 模块依赖 |
| [`../overview/SYSTEM_ARCHITECTURE.md`](../overview/SYSTEM_ARCHITECTURE.md) | 全栈连线与本地启动（原《整体架构说明》《后端整体架构总览》已于 2026-10-29 移除） |
| [`../overview/ROADMAP.md`](../overview/ROADMAP.md) | ROADMAP（原《设计文档/后续规划》已于 2026-10-29 移除） |
