# Template: Documentation Consolidation

```yaml
artifact_type: INSTRUCTION
status: CURRENT
reusable: true
```

## Method

1. Inventory all docs + agent instructions + analyses before moving.  
2. Verify claims against **current code** (not prior reports).  
3. Establish CURRENT SoT (`overview/CURRENT_STATE`, contracts, ADR).  
4. SUPERSEDE/ARCHIVE historical reports **without rewriting PASS/FAIL**.  
5. Unresolved → GitHub Issues only.  
6. Extract reusable templates/ADRs from one-off instructions.  

## Safety

- Do not delete problems by deleting docs.  
- Do not treat MockMvc/build as Browser E2E.  
- Do not invent COMPLETE without scope.  

## Outputs

Inventory · CURRENT tree · Archive list · Issue handoff · Verification · Knowledge Consolidation Report · Verdict `READY` | `READY_WITH_TRACKED_ISSUES` | `BLOCKED`
