/**
 * 运营「全局预约」中心。
 *
 * 跨站预约列表尚无真实后端；站点预约请使用始终 REAL 的 apps/reservationApi
 *（`/admin/sites/{siteId}/reservations`）。
 *
 * 已退役 mock 与错误路径 `/site/reservations`，避免生产路径落在假数据上。
 */

/** 运营全局预约中心未接入；UI 应展示未接入占位，勿请求假接口。 */
export const OPERATION_RESERVATIONS_CONNECTED = false
