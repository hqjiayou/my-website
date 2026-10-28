# RESERVATION

> ⚠️ **已归档 — 功能已删除**：Reservation（预约）功能已于 **2026-10-27** 整包删除（Java 后端、admin-web 页面与内部建单接口均已移除，代码中无残留）。本文只保留删除前的历史规格，不得作为现行实现依据。现行状态见 [`../../overview/CURRENT_STATE.md`](../../overview/CURRENT_STATE.md)。

```yaml
artifact_type: SPEC
status: ARCHIVED
last_verified: 2026-10-10
```

**后台工单**：`AdminReservationController` 提供 list、detail、create、`PATCH .../status`、delete。没有整单 PUT。没有冲突检测，也没有给访客的公开预约接口。

状态闭集：`NEW` | `CONFIRMED` | `DONE` | `CANCELLED`。不要写成 `COMPLETED`。

site-app 只在内部接口 `POST /internal/site/v1/sites/{siteId}/reservations` 给 Agent 建单。运营「预约中心」是 admin-web 客户端聚合（`OPERATION_RESERVATIONS_CONNECTED = true`）。  
