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
  agentApi,
  SITE_CODE_PATTERN,
  SITE_INDUSTRY_VALUES,
  SITE_STATUS,
  SITE_STATUS_VALUES,
  SITE_TYPE_VALUES,
  SITE_TYPE_PRESETS,
  SITE_TAG_PATTERN,
} from './apps/agentApi'
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
export { OPERATION_CONTENTS_CONNECTED } from './operation/contentApi'
export { fileApi, OPERATION_FILES_CONNECTED } from './operation/fileApi'

export type {
  AppearanceConfigV1,
  AppearanceDetail,
  AppearancePutRequest,
  ButtonStyle,
  HeroLayout,
  AgentFontStyle,
} from './appearanceApi'
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
  CreateAgentInput,
  PageResult,
  Site,
  AgentDetail,
  AgentListItem,
  AgentListQuery,
  AgentRuntimeStatus,
  AgentStatus,
  AgentType,
  UpdateAgentInput,
  UpdateAgentStatusInput,
} from './apps/agentApi'
