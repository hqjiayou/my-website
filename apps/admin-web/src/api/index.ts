/**
 * 功能注解：统一导出分组后的 API 模块，供页面层集中引入。
 */
export { aiApi, previewChatStreamUrl, streamPreviewChat } from './apps/aiApi'
export { authApi } from './authApi'
export { rbacApi } from './rbacApi'
export {
  appearanceApi,
  APPEARANCE_SCHEMA_VERSION_V1,
  BUTTON_STYLE_VALUES,
  FONT_STYLE_VALUES,
  HERO_LAYOUT_VALUES,
  PRIMARY_COLOR_PATTERN,
  createDefaultAppearanceConfig,
} from './appearanceApi'
export {
  siteApi,
  SITE_CODE_PATTERN,
  SITE_INDUSTRY_VALUES,
  SITE_STATUS,
  SITE_STATUS_VALUES,
  SITE_TYPE_VALUES,
  SITE_TYPE_PRESETS,
  SITE_TYPE_LEGACY,
  SITE_TAG_PATTERN,
} from './apps/siteApi'
export { templateApi, TEMPLATE_STATUS, TEMPLATE_STATUS_VALUES } from './apps/templateApi'
export {
  mediaApi,
  MEDIA_ACCEPT,
  MEDIA_ALLOWED_CONTENT_TYPES,
  MEDIA_STATUS,
  MEDIA_STATUS_VALUES,
} from './apps/mediaApi'
export {
  contentApi,
  CONTENT_STATUS,
  CONTENT_STATUS_VALUES,
  CONTENT_TYPE_VALUES,
} from './apps/contentApi'
export {
  reservationApi,
  RESERVATION_ALLOWED_TRANSITIONS,
  RESERVATION_STATUS,
  RESERVATION_STATUS_VALUES,
  getReservationStatusOptions,
} from './apps/reservationApi'
export { OPERATION_CONTENTS_CONNECTED } from './operation/contentApi'
export { fileApi, OPERATION_FILES_CONNECTED } from './operation/fileApi'
export { OPERATION_RESERVATIONS_CONNECTED } from './operation/reservationApi'

export type {
  AppearanceConfigV1,
  AppearanceDetail,
  AppearancePutRequest,
  ButtonStyle,
  HeroLayout,
  SiteFontStyle,
} from './appearanceApi'
export type {
  TemplateDetail,
  TemplateListItem,
  TemplateListQuery,
  TemplateStatus,
  UpdateTemplateStatusInput,
} from './apps/templateApi'
export type {
  MediaDetail,
  MediaListItem,
  MediaListQuery,
  MediaPreview,
  MediaStatus,
} from './apps/mediaApi'
export type {
  ContentDetail,
  ContentListItem,
  ContentListQuery,
  ContentStatus,
  ContentType,
  CreateContentInput,
  UpdateContentInput,
  UpdateContentStatusInput,
} from './apps/contentApi'
export type {
  CreateReservationInput,
  ReservationDetail,
  ReservationListItem,
  ReservationListQuery,
  ReservationStatus,
  UpdateReservationStatusInput,
} from './apps/reservationApi'
export type {
  CreateSiteInput,
  PageResult,
  Site,
  SiteDetail,
  SiteListItem,
  SiteListQuery,
  SiteRuntimeStatus,
  SiteStatus,
  SiteType,
  UpdateSiteInput,
  UpdateSiteStatusInput,
} from './apps/siteApi'
