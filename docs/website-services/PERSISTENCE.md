# PERSISTENCE

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
```

- Flyway 脚本宿主：`infra/src/main/resources/db/migration`（V1–V15+）。  
- **禁止修改已执行 migration**。  
- 验证：`mvn -B -ntp -Ppersistence-verify-pg test`（Testcontainers PostgreSQL）。  
- 蓝图：`设计文档/数据与持久层/`；表字典：`数据字典/`。  
- 冲突时：**schema / Flyway > 旧设计文档**。  
