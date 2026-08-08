# SOURCE_OF_TRUTH

```yaml
artifact_type: SPEC
status: CURRENT
last_verified: 2026-08-09
```

## 事实优先级（高 → 低）

1. 当前 source code  
2. 当前 Flyway / DB schema  
3. 当前 automated tests  
4. 当前运行配置  
5. 当前 frontend API consumers  
6. **CURRENT** 文档（`docs/overview/CURRENT_STATE.md` 及域 CURRENT / contracts / ADR）  
7. 历史 implementation / execution / audit 文档  

## 冲突处理

文档与代码冲突 → **以代码为准**，并修正 CURRENT 文档或标记旧文档 SUPERSEDED。

Agent 执行任务时的阅读顺序见 [`../README.md`](../README.md)。

## 分工

| 知识类型 | Source of Truth |
|----------|-----------------|
| 系统现在是什么 | `docs/overview/CURRENT_STATE.md` + 代码 |
| 未完成工程工作 | **GitHub Issues** |
| 为什么这样设计 | `docs/decisions/` |
| 某次历史做过什么 | `docs/implementation-history/` |
