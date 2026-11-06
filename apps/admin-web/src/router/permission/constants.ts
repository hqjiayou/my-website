import { PERMISSION_CODES } from '@/types/rbac'

import { ROUTES } from '../config/routePaths'

/**
 * 路由路径 → 所需 ACTION 权限码；未列出的路径仅校验登录态。
 * 分析、参数、系统设置、开发者、AI 设置说明没有对应 ACTION code，不在此登记。
 * 菜单可见性仍由 /admin/auth/me 的 menus 决定，与这里的进页鉴权分开。
 */
export const ROUTE_PERMISSIONS: Record<string, string> = {
  [ROUTES.dashboard]: PERMISSION_CODES.dashboardView,
  [ROUTES.system.users]: PERMISSION_CODES.userList,
  [ROUTES.system.roles]: PERMISSION_CODES.roleList,
  [ROUTES.system.menus]: PERMISSION_CODES.menuList,
  [ROUTES.apps.sites]: PERMISSION_CODES.agentList,
  [ROUTES.apps.api]: PERMISSION_CODES.aiModelRead,
  [ROUTES.apps.agentCreate]: PERMISSION_CODES.agentCreate,
  [ROUTES.apps.style]: PERMISSION_CODES.agentList,
  [ROUTES.operation.contents]: PERMISSION_CODES.contentList,
  [ROUTES.operation.files]: PERMISSION_CODES.mediaList,
  [ROUTES.ai.settings]: PERMISSION_CODES.aiModelRead,
  [ROUTES.ai.prompts]: PERMISSION_CODES.aiPromptRead,
  [ROUTES.ai.knowledgeBases]: PERMISSION_CODES.aiKbList,
  [ROUTES.ai.conversations]: PERMISSION_CODES.aiConversationList,
  [ROUTES.ai.modelEntries]: PERMISSION_CODES.aiModelRead,
  [ROUTES.ai.configPresets]: PERMISSION_CODES.aiPromptRead,
}
