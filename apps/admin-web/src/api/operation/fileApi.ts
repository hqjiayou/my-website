/**
 * 运营「文件中心」API。
 *
 * 全局文件列表尚无真实后端；站点媒体请使用始终 REAL 的 mediaApi
 *（`/admin/sites/{siteId}/media`）。
 *
 * 已退役 mock 与错误路径 `/site/files`，避免生产路径落在假数据上。
 */
import { mediaApi } from '@/api/apps/mediaApi'

/** 运营全局文件中心未接入；UI 应展示未接入占位，勿请求假接口。 */
export const OPERATION_FILES_CONNECTED = false

/**
 * 兼容导出：站点媒体能力与 mediaApi 同源（始终 REAL）。
 * 勿再依赖 listFiles / USE_MOCK。
 */
export const fileApi = {
  listMedia: mediaApi.listMedia,
  getMedia: mediaApi.getMedia,
  uploadMedia: mediaApi.uploadMedia,
  previewMedia: mediaApi.previewMedia,
  deleteMedia: mediaApi.deleteMedia,
}
