# Template: Security Fix

```yaml
artifact_type: INSTRUCTION
status: CURRENT
reusable: true
```

1. Reproduce with evidence (code path + test or exploit sketch without weaponizing).  
2. Minimal fix；no drive-by refactors.  
3. Add regression test.  
4. Separate commit/PR from docs/features.  
5. Update `operations/SECURITY.md` + Issue status.  
6. If HIGH remains open → platform verdict **BLOCKED**.  
