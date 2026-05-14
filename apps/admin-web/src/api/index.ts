/**
 * 功能注解：统一导出分组后的 API 模块，供页面层集中引入。
 */
export { aiApi } from './ai/ai-api'
export { appearanceApi } from './appearance-api'
export { siteApi, SITE_INDUSTRY_VALUES } from './apps/site-api'
export { templateApi } from './apps/template-api'
export { analyticsApi } from './developer/analytics-api'
export { systemApi } from './developer/system-api'
export { contentApi } from './operation/content-api'
export { fileApi } from './operation/file-api'
export { reservationApi } from './operation/reservation-api'
export { userApi } from './operation/user-api'

export type { SiteTemplate, TemplateStatus } from './apps/template-api'
export type {
  ButtonStyle,
  CardStyle,
  ContainerWidth,
  CtaStyle,
  FooterStyle,
  HeaderStyle,
  HeroLayout,
  HomeSectionConfig,
  HomeSectionKey,
  ImageStyle,
  SectionSpacing,
  SiteAppearanceConfig,
  SiteColorScheme,
  SiteFontStyle,
  SiteRadiusStyle,
  SiteShadowStyle,
  TitleStyle,
} from './appearance-api'
export type { ContentItem, ContentStatus, ContentType, CreateContentInput } from './operation/content-api'
export type { CreateSiteInput, Site, SiteIndustry, SiteRuntimeStatus, SiteStatus, UpdateSiteInput } from './apps/site-api'
