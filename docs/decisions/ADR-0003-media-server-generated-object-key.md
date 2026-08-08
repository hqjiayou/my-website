# ADR-0003 Media server-generated object key

```yaml
artifact_type: DECISION
status: ACCEPTED
date: 2026-08-08
```

## Context

客户端指定 object key / 把原始文件名写入 key 易导致覆盖、路径遍历与不一致。

## Decision

Object key 由服务端生成；原始文件名不进入 storage key。

## Consequences

上传 API 不接受客户端 key；前端只传文件与元数据。
