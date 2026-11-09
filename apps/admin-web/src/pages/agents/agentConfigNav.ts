import { BookOpen, Braces, Cpu, Plug, type LucideIcon } from 'lucide-react'

import { ROUTES } from '@/router/config/routePaths'

export type AgentConfigNavKey = 'models' | 'prompt' | 'knowledge' | 'tools'

export type AgentConfigNavPermissions = {
  canModel: boolean
  canPrompt: boolean
  canKnowledge: boolean
}

export type AgentConfigNavItem = {
  key: AgentConfigNavKey
  to: string
  icon: LucideIcon
  labelKey: string
}

/**
 * 配置子导航项：可见性与各子页 PermissionGate 对齐。
 * models → aiModelRead；prompt/tools → aiPromptRead；knowledge → aiKbList。
 */
export function buildAgentConfigNavItems(
  agentId: number | string,
  permissions: AgentConfigNavPermissions,
): AgentConfigNavItem[] {
  const items: Array<AgentConfigNavItem | null> = [
    permissions.canModel
      ? {
          key: 'models',
          to: ROUTES.apps.agentConfigModels(agentId),
          icon: Cpu,
          labelKey: 'workspace.configNav.models',
        }
      : null,
    permissions.canPrompt
      ? {
          key: 'prompt',
          to: ROUTES.apps.agentConfigPrompt(agentId),
          icon: Braces,
          labelKey: 'workspace.configNav.prompt',
        }
      : null,
    permissions.canKnowledge
      ? {
          key: 'knowledge',
          to: ROUTES.apps.agentConfigKnowledge(agentId),
          icon: BookOpen,
          labelKey: 'workspace.configNav.knowledge',
        }
      : null,
    permissions.canPrompt
      ? {
          key: 'tools',
          to: ROUTES.apps.agentConfigTools(agentId),
          icon: Plug,
          labelKey: 'workspace.configNav.tools',
        }
      : null,
  ]
  return items.filter((item): item is AgentConfigNavItem => item != null)
}

/** 配置根路径应进入的第一个可访问子页；无权限时返回 null。 */
export function firstAgentConfigPath(
  agentId: number | string,
  permissions: AgentConfigNavPermissions,
): string | null {
  return buildAgentConfigNavItems(agentId, permissions)[0]?.to ?? null
}
