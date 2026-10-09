# ERROR_CONTRACT

```yaml
artifact_type: CONTRACT
status: CURRENT
last_verified: 2026-10-07
```

- 统一包装：`ApiResponse` / `HttpMessageBody`：`code`、`message`、`data`（`code === 0` 成功）。  
- 业务错误码定义在 `common` ErrorCode；前端 `ApiError` 解析。  
- Bearer 无效：`auth.token.invalid` + `WWW-Authenticate: invalid_token`（401）。  
- 限流：HTTP 429（auth rate limit）。  
- AI 无 Provider：诚实失败（如 503 `EXTERNAL_DEPENDENCY_BLOCKED` / 1007），禁止伪造成功体。  
- Agent 单轮预算超限：HTTP **422**，业务码 **1008** `AGENT_BUDGET_EXCEEDED`（对应 Python `errorCode=AGENT_BUDGET_EXCEEDED`；与 1000 `SYSTEM_ERROR` 区分）。  
