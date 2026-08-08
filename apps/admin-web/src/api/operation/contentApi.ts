/**
 * 运营「全局内容」中心。
 *
 * 跨站内容列表尚无真实后端；站点内容请使用始终 REAL 的 apps/contentApi
 *（`/admin/sites/{siteId}/contents`）。
 *
 * 已退役 mock 与错误路径 `/site/contents`，避免生产路径落在假数据上。
 */

/** 运营全局内容中心未接入；UI 应展示未接入占位，勿请求假接口。 */
export const OPERATION_CONTENTS_CONNECTED = false
