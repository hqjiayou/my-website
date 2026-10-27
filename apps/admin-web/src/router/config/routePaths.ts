/** 路由路径常量（零依赖，可供 layout / pages / router/navigation 安全导入）。 */
export const ROUTES = {
  root: '/',
  login: '/login',
  forbidden: '/403',
  dashboard: '/dashboard',
  system: {
    users: '/system/users',
    userCreate: '/system/users/new',
    userEdit: (userId: number | string) => `/system/users/${userId}/edit`,
    userRoles: (userId: number | string) => `/system/users/${userId}/roles`,
    roles: '/system/roles',
    roleCreate: '/system/roles/new',
    roleEdit: (roleId: number | string) => `/system/roles/${roleId}/edit`,
    rolePermissions: (roleId: number | string) => `/system/roles/${roleId}/permissions`,
    permissions: '/system/permissions',
    menus: '/system/menus',
  },
  apps: {
    sites: '/apps/sites',
    siteCreate: '/apps/sites/create',
    siteDetailPattern: '/apps/sites/:siteId',
    siteDetail: (siteId: number | string) => `/apps/sites/${siteId}`,
    siteAgent: (siteId: number | string) => `/apps/sites/${siteId}/agent`,
    siteAgentPrompt: (siteId: number | string) => `/apps/sites/${siteId}/agent/prompt`,
    siteAgentKnowledge: (siteId: number | string) => `/apps/sites/${siteId}/agent/knowledge`,
    siteAgentConversations: (siteId: number | string) => `/apps/sites/${siteId}/agent/conversations`,
    siteAgentPreview: (siteId: number | string) => `/apps/sites/${siteId}/agent/preview`,
    siteAgentRuntime: (siteId: number | string) => `/apps/sites/${siteId}/agent/runtime`,
    siteBasic: (siteId: number | string) => `/apps/sites/${siteId}/basic`,
    siteTheme: (siteId: number | string) => `/apps/sites/${siteId}/theme`,
    siteDomain: (siteId: number | string) => `/apps/sites/${siteId}/domain`,
    siteContents: (siteId: number | string) => `/apps/sites/${siteId}/contents`,
    /** 旧 AI 页路径（仅供重定向匹配，不再作为导航目标）。 */
    siteAiLegacy: (siteId: number | string) => `/apps/sites/${siteId}/ai`,
    siteFiles: (siteId: number | string) => `/apps/sites/${siteId}/files`,
    style: '/apps/style',
  },
  operation: {
    contents: '/operation/contents',
    files: '/operation/files',
  },
  ai: {
    settings: '/ai/settings',
    prompts: '/ai/prompts',
    knowledgeBases: '/ai/knowledge-bases',
    conversations: '/ai/conversations',
  },
  config: {
    params: '/config/params',
  },
  governance: {
    analytics: '/analytics',
    /** 旧「人员管理」并行面；adminRoutes 内 redirect → system.users */
    team: '/team',
    system: '/system',
    developer: '/developer',
  },
  legacy: {
    sites: '/sites',
    ai: '/ai',
    settings: '/settings',
  },
} as const
