# Template: Independent Acceptance Audit

```yaml
artifact_type: INSTRUCTION
status: CURRENT
reusable: true
```

## Method

1. READ ONLY except the audit document itself.  
2. Treat Execution Report as **claims**, not facts.  
3. Re-run applicable automated verification.  
4. Severity: CRITICAL / HIGH / MEDIUM / LOW；存在 HIGH → `NOT ACCEPTED`.  
5. Scope labels: `COMPLETE_V1_*` / `BACKEND_COMPLETE` / `EXTERNAL_BLOCKED` / etc. — never bare COMPLETE.  

## Outputs

Executive summary · claim accuracy table · findings with evidence · required fixes · commit advice · final `ACCEPTED` | `NOT ACCEPTED`
