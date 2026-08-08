# Template: Legacy Code Cleanup

```yaml
artifact_type: INSTRUCTION
status: CURRENT
reusable: true
```

## Before delete

Search: imports · exports · dynamic imports · routes · tests · docs · scripts · AGENTS / `.cursor`.

## Classifications

`SAFE_TO_REMOVE` · `STILL_REQUIRED` · `DANGEROUS_PARALLEL_PATH`

If uncertain → **do not delete** → GitHub Issue.

## Never

- Remove AI/runtime mocks still required.  
- Re-introduce mock fallback on FORCE REAL domains.  
