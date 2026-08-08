import { AI_ADMIN_LOCALES } from '@/pages/ai/locales'
import { ANALYTICS_LOCALES } from '@/pages/analytics/locales'
import { SITES_LOCALES } from '@/pages/apps/sites/locales'
import { STYLE_SETTINGS_LOCALES } from '@/pages/apps/locales'
import { TEMPLATES_LOCALES } from '@/pages/apps/templates/locales'
import { PARAMS_CONFIG_LOCALES } from '@/pages/config/locales'
import { DASHBOARD_LOCALES } from '@/pages/dashboard/locales'
import { DEVELOPER_LOCALES } from '@/pages/developer/locales'
import { LOGIN_LOCALES } from '@/pages/login/locales'
import { MENUS_LOCALES } from '@/pages/menus/locales'
import { PERMISSIONS_LOCALES } from '@/pages/permissions/locales'
import { OPERATION_LOCALES } from '@/pages/operation/locales'
import { ROLES_LOCALES } from '@/pages/roles/locales'
import { SYSTEM_SETTINGS_LOCALES } from '@/pages/system/locales'
import { USERS_LOCALES } from '@/pages/users/locales'

import type { SupportedLocale } from './index'

/** 各功能页在 i18next 中的命名空间，与各页面目录下 locales 一一对应；下线某功能时从这里与 resources 组装处移除即可。 */
export const PAGE_I18N_NAMESPACES = [
  'dashboard',
  'login',
  'sites',
  'templates',
  'operation',
  'aiAdmin',
  'styleSettings',
  'paramsConfig',
  'analytics',
  'systemSettings',
  'developer',
  'permissions',
  'users',
  'roles',
  'menus',
] as const

export function pageNamespacesFor(locale: SupportedLocale) {
  return {
    dashboard: DASHBOARD_LOCALES[locale],
    login: LOGIN_LOCALES[locale],
    sites: SITES_LOCALES[locale],
    templates: TEMPLATES_LOCALES[locale],
    operation: OPERATION_LOCALES[locale],
    aiAdmin: AI_ADMIN_LOCALES[locale],
    styleSettings: STYLE_SETTINGS_LOCALES[locale],
    paramsConfig: PARAMS_CONFIG_LOCALES[locale],
    analytics: ANALYTICS_LOCALES[locale],
    systemSettings: SYSTEM_SETTINGS_LOCALES[locale],
    developer: DEVELOPER_LOCALES[locale],
    permissions: PERMISSIONS_LOCALES[locale],
    users: USERS_LOCALES[locale],
    roles: ROLES_LOCALES[locale],
    menus: MENUS_LOCALES[locale],
  }
}
