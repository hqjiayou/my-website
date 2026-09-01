# ADR-0004 AI must fail honestly without provider

```yaml
artifact_type: DECISION
status: ACCEPTED
date: 2026-08-08
```

## Context

AI LIVE 依赖外部 Provider。缺失密钥时若返回假成功会污染验收与产品预期。

## Decision

无 Provider / agent blocked 时返回明确失败（如 503）。admin-web 的 Prompt / KB / Conversation / Preview **不得**用假数据冒充 LIVE；无 `AI_MODEL` 时预览与聊天必须诚实失败。

## Consequences

状态标签：无 Provider 时仍为 `EXTERNAL_BLOCKED`。admin-web AI 管理页已 FORCE REAL。禁止伪造成功响应体。
