import { AI_ADMIN_LOCALES } from '@/pages/ai/locales'
import { ANALYTICS_LOCALES } from '@/pages/analytics/locales'
import { SITES_LOCALES } from '@/pages/apps/sites/locales'
import { STYLE_SETTINGS_LOCALES } from '@/pages/apps/locales'
import { TEMPLATES_LOCALES } from '@/pages/apps/templates/locales'
import { PARAMS_CONFIG_LOCALES } from '@/pages/config/locales'
import { DASHBOARD_LOCALES } from '@/pages/dashboard/locales'
import { DEVELOPER_LOCALES } from '@/pages/developer/locales'
import { OPERATION_LOCALES } from '@/pages/operation/locales'
import { SYSTEM_SETTINGS_LOCALES } from '@/pages/system/locales'
import { TEAM_LOCALES } from '@/pages/team/locales'

import type { SupportedLocale } from './index'

/** 各功能页在 i18next 中的命名空间，与各页面目录下 locales 一一对应；下线某功能时从这里与 resources 组装处移除即可。 */
export const PAGE_I18N_NAMESPACES = [
  'dashboard',
  'sites',
  'templates',
  'operation',
  'aiAdmin',
  'styleSettings',
  'paramsConfig',
  'analytics',
  'team',
  'systemSettings',
  'developer',
] as const

export function pageNamespacesFor(locale: SupportedLocale) {
  return {
    dashboard: DASHBOARD_LOCALES[locale],
    sites: SITES_LOCALES[locale],
    templates: TEMPLATES_LOCALES[locale],
    operation: OPERATION_LOCALES[locale],
    aiAdmin: AI_ADMIN_LOCALES[locale],
    styleSettings: STYLE_SETTINGS_LOCALES[locale],
    paramsConfig: PARAMS_CONFIG_LOCALES[locale],
    analytics: ANALYTICS_LOCALES[locale],
    team: TEAM_LOCALES[locale],
    systemSettings: SYSTEM_SETTINGS_LOCALES[locale],
    developer: DEVELOPER_LOCALES[locale],
  }
}
