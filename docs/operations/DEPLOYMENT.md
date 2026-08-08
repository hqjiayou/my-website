# DEPLOYMENT

```yaml
artifact_type: RUNBOOK
status: CURRENT
last_verified: 2026-08-09
```

当前仓库以本地/开发运行为主。部署时注意：

1. 仅暴露 gateway；auth/admin/site 内网。  
2. Flyway 向前迁移；不改已执行脚本。  
3. Auth 私钥与 JWKS 发布流程。  
4. `trusted-proxies` 配真实前置 LB CIDR。  
5. 关闭开放注册（除非显式 bootstrap）。  
