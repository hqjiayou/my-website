# BACKEND_TEST_MATRIX

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-10-10
```

| Check | Command |
|-------|---------|
| unit/MVC | `cd services/website-services && mvn -B -ntp test` |
| PG verify | `mvn -B -ntp -Ppersistence-verify-pg test` |

Gateway↔Auth XFF 集成断言：仍为 **TEST_GAP**（支撑安全 Issue）。
