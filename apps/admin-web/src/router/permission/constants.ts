import { PERMISSION_CODES } from '@/types/rbac'

import { ROUTES } from '../config/routePaths'

/** 路由路径 → 所需权限码；未列出的路径仅校验登录态。 */
export const ROUTE_PERMISSIONS: Record<string, string> = {
  [ROUTES.system.users]: PERMISSION_CODES.userList,
  [ROUTES.system.userCreate]: PERMISSION_CODES.userCreate,
  [ROUTES.system.roles]: PERMISSION_CODES.roleList,
  [ROUTES.system.roleCreate]: PERMISSION_CODES.roleCreate,
  [ROUTES.system.permissions]: PERMISSION_CODES.permissionList,
  [ROUTES.system.menus]: PERMISSION_CODES.menuList,
  [ROUTES.apps.sites]: PERMISSION_CODES.siteList,
  [ROUTES.apps.siteCreate]: PERMISSION_CODES.siteCreate,
  [ROUTES.apps.templates]: PERMISSION_CODES.templateList,
  [ROUTES.ai.prompts]: PERMISSION_CODES.aiPromptRead,
  [ROUTES.ai.knowledgeBases]: PERMISSION_CODES.aiKbList,
  [ROUTES.ai.conversations]: PERMISSION_CODES.aiConversationList,
}
