# ROADMAP

```yaml
artifact_type: REFERENCE
status: CURRENT
last_verified: 2026-08-09
```

未完成工程 / 产品工作以 **GitHub Issues** 为唯一跟踪源。本文只保留 Issue 号 + 短状态，不复制完整 TODO。

## P1 — 安全 / 核心产品缺口

| 主题 | 状态 | Issue |
|------|------|-------|
| Auth trusted-proxy / XFF / rate-limit boundary | PARTIALLY_RESOLVED（配置已加；缺集成断言 + 暴露面硬化） | [#1](https://github.com/hqjiayou/my-website/issues/1) |
| Site Web Auth 前端接入 | BACKEND_COMPLETE / FE NOT_IMPLEMENTED | [#3](https://github.com/hqjiayou/my-website/issues/3) |

## P2 — 测试 / 阻塞中的产品能力

| 主题 | 状态 | Issue |
|------|------|-------|
| Browser golden-path E2E | NOT_IMPLEMENTED | [#2](https://github.com/hqjiayou/my-website/issues/2) |
| AI LIVE provider integration | EXTERNAL_BLOCKED | [#5](https://github.com/hqjiayou/my-website/issues/5) |

## P3 — 技术债

| 主题 | 状态 | Issue |
|------|------|-------|
| Soft-delete `deleted_at` 一致性（Site/Appearance 等） | STILL_OPEN · LOW | [#4](https://github.com/hqjiayou/my-website/issues/4) |

## 明确不做 / 延期（无 Issue）

| 主题 | 状态 | 理由 |
|------|------|------|
| Menu write API | DEFERRED_BY_DESIGN | 只读已够用；产品决定需要时再建 Issue |
| Template 本体 CRUD | DEFERRED_BY_DESIGN | 目录+status 已闭环 |
| Reservation 冲突/公开预约 | NOT_PLANNED（本阶段） | COMPLETE_V1_ADMIN_SCOPE 边界外 |
| Site runtime-status REAL | DEFERRED | UI 在 REAL 下已诚实占位；产品确认后再开 Issue |
| Analytics / System 业务 | PLACEHOLDER | 有意占位 |

## 建议下一阶段顺序

1. [#1](https://github.com/hqjiayou/my-website/issues/1) 关闭 / 证明 Auth IP 信任边界  
2. [#3](https://github.com/hqjiayou/my-website/issues/3) site-web auth **或** [#2](https://github.com/hqjiayou/my-website/issues/2) Browser E2E（择一/并行）  
3. [#5](https://github.com/hqjiayou/my-website/issues/5) 在 provider 可用时解除 AI LIVE  
4. [#4](https://github.com/hqjiayou/my-website/issues/4) 软删时间戳一致性（可穿插）
