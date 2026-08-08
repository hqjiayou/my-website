# ADR-0004 AI must fail honestly without provider

```yaml
artifact_type: DECISION
status: ACCEPTED
date: 2026-08-08
```

## Context

AI LIVE 依赖外部 Provider。缺失密钥时若返回假成功会污染验收与产品预期。

## Decision

无 Provider / agent blocked 时返回明确失败（如 503）；admin-web AI 可保留 **明示 mock**，但不得标 COMPLETE / LIVE。

## Consequences

状态标签：`EXTERNAL_BLOCKED` + frontend mock。禁止伪造成功响应体。
