# ROADMAP

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-08-09
```

未完成工程工作以 **GitHub Issues** 为唯一跟踪源。本文只保留 Issue 号 + 短状态，不复制完整 TODO。

## 阻塞下一产品阶段

| 主题 | 状态 | Issue |
|------|------|-------|
| Auth client IP / XFF trust chain | PARTIALLY_RESOLVED（gateway trusted-proxies 已加；缺集成断言） | handoff Issue A（创建后回填 #） |

## 跟踪中 / 计划中

| 主题 | 状态 | Issue |
|------|------|-------|
| Browser E2E golden paths | NOT_IMPLEMENTED | handoff |
| site-web authentication integration | BACKEND_COMPLETE / FE NOT_IMPLEMENTED | handoff |
| Soft-delete `deleted_at` consistency | STILL_OPEN · LOW | handoff |
| Admin user mutation onError feedback | STILL_OPEN · LOW | handoff |
| Site runtime-status REAL | PLACEHOLDER | handoff（产品确认后） |
| AI LIVE provider integration | EXTERNAL_BLOCKED | handoff（blocked） |

## 明确不做 / 延期（无 Issue）

| 主题 | 状态 | 理由 |
|------|------|------|
| Menu write API | DEFERRED_BY_DESIGN | 只读已够用；进 roadmap 再建 Issue |
| Template 本体 CRUD | DEFERRED_BY_DESIGN | 目录+status 已闭环 |
| Reservation 冲突/公开预约 | NOT_PLANNED（本阶段） | COMPLETE_V1_ADMIN_SCOPE 边界外 |
| Analytics / System 业务 | PLACEHOLDER | 有意占位 |

## 建议下一阶段顺序

1. 关闭 XFF HIGH（或合并已提交的 security fix）
2. orphan mock 清理（本轮 chore）
3. site-web auth 对接 **或** Browser E2E 择一/并行
4. 产品确认后的 runtime-status / AI provider
