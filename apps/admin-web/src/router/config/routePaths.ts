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
    agentCreate: '/apps/sites/create',
    agentDetailPattern: '/apps/sites/:agentId',
    agentDetail: (agentId: number | string) => `/apps/sites/${agentId}`,
    agent: (agentId: number | string) => `/apps/sites/${agentId}/agent`,
    agentPrompt: (agentId: number | string) => `/apps/sites/${agentId}/agent/prompt`,
    agentKnowledge: (agentId: number | string) => `/apps/sites/${agentId}/agent/knowledge`,
    agentConversations: (agentId: number | string) => `/apps/sites/${agentId}/agent/conversations`,
    agentPreview: (agentId: number | string) => `/apps/sites/${agentId}/agent/preview`,
    agentRuntime: (agentId: number | string) => `/apps/sites/${agentId}/agent/runtime`,
    agentBasic: (agentId: number | string) => `/apps/sites/${agentId}/basic`,
    agentTheme: (agentId: number | string) => `/apps/sites/${agentId}/theme`,
    agentDomain: (agentId: number | string) => `/apps/sites/${agentId}/domain`,
    agentContents: (agentId: number | string) => `/apps/sites/${agentId}/contents`,
    /** 旧 AI 页路径（仅供重定向匹配，不再作为导航目标）。 */
    agentAiLegacy: (agentId: number | string) => `/apps/sites/${agentId}/ai`,
    agentFiles: (agentId: number | string) => `/apps/sites/${agentId}/files`,
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
