import type { LucideIcon } from 'lucide-react'
import {
  BarChart3,
  BookOpen,
  Bot,
  Braces,
  FileText,
  FolderKanban,
  LayoutDashboard,
  Layers3,
  MessageSquare,
  Palette,
  Server,
  Settings2,
  SlidersHorizontal,
} from 'lucide-react'

import { ROUTES } from '../config/routePaths'

import { pathMatchesLeaf } from './navPath'

export interface NavLeaf {
  labelKey: string
  path: string
  icon: LucideIcon
}

export interface NavGroup {
  id: string
  labelKey: string
  children: NavLeaf[]
}

export interface NavSection {
  id: string
  titleKey?: string
  entries: Array<NavLeaf | NavGroup>
}

export function isNavGroup(entry: NavLeaf | NavGroup): entry is NavGroup {
  return 'children' in entry
}

/** 静态导航配置：用于顶栏标题解析等；侧栏菜单以接口 auth/me 为准。 */
export const NAV_SECTIONS: NavSection[] = [
  {
    id: 'workspace',
    entries: [{ labelKey: 'items.dashboard', path: ROUTES.dashboard, icon: LayoutDashboard }],
  },
  {
    id: 'apps',
    titleKey: 'sections.business',
    entries: [
      {
        id: 'group-my-app',
        labelKey: 'groups.myApp',
        children: [
          { labelKey: 'items.sites', path: ROUTES.apps.sites, icon: Layers3 },
          { labelKey: 'items.aiUsage', path: ROUTES.ai.usage, icon: BarChart3 },
          { labelKey: 'items.styleSettings', path: ROUTES.apps.style, icon: Palette },
        ],
      },
    ],
  },
  {
    id: 'operation',
    titleKey: 'sections.operation',
    entries: [
      { labelKey: 'items.contents', path: ROUTES.operation.contents, icon: FileText },
      { labelKey: 'items.files', path: ROUTES.operation.files, icon: FolderKanban },
    ],
  },
  {
    id: 'ai',
    titleKey: 'sections.ai',
    entries: [
      {
        // 平台区（Issue #213 UoW-C）：跨站共享资源归组——知识空间、模型入口、
        // Prompt 模板、MCP 注册表（#214 UoW-5）、Skills 货架（#214 UoW-6）、
        // 媒体库（路由仍在运营区文件页，只在此归组呈现）。
        id: 'group-platform',
        labelKey: 'groups.platform',
        children: [
          { labelKey: 'items.aiKnowledgeBases', path: ROUTES.ai.knowledgeBases, icon: BookOpen },
          { labelKey: 'items.aiSettings', path: ROUTES.ai.modelEntries, icon: Bot },
          { labelKey: 'items.aiPromptTemplates', path: ROUTES.ai.promptTemplates, icon: FileText },
          { labelKey: 'items.aiMcpServers', path: ROUTES.ai.mcpServers, icon: Server },
          { labelKey: 'items.aiSkills', path: ROUTES.ai.skills, icon: SlidersHorizontal },
          { labelKey: 'items.files', path: ROUTES.operation.files, icon: FolderKanban },
        ],
      },
      { labelKey: 'items.aiPlatformSettings', path: ROUTES.ai.settings, icon: Settings2 },
      { labelKey: 'items.aiPrompts', path: ROUTES.ai.prompts, icon: Braces },
      { labelKey: 'items.aiConversations', path: ROUTES.ai.conversations, icon: MessageSquare },
    ],
  },
  {
    id: 'governance',
    titleKey: 'sections.governance',
    entries: [{ labelKey: 'items.analytics', path: ROUTES.governance.analytics, icon: BarChart3 }],
  },
]

/**
 * 根据 pathname 解析顶栏 i18n 标题 key（站点工作区等动态路径单独处理）。
 * Agent 工作区内顶栏只保留「智能体」位置上下文；Agent 名 / Tabs / 配置子导航在主体区展示，
 * 避免「页面标题 + Agent 标题 + 配置标题」三层堆叠。
 */
export function resolveNavTitleKey(pathname: string): string | undefined {
  if (pathname === ROUTES.apps.agentCreate || pathname.startsWith(`${ROUTES.apps.agentCreate}/`)) {
    return 'items.agentCreate'
  }
  if (/^\/agent\/(?!api(?:\/|$)|create(?:\/|$))[^/]+(?:\/|$)/.test(pathname)) {
    return 'items.sites'
  }

  let best: { labelKey: string; pathLength: number } | undefined
  for (const section of NAV_SECTIONS) {
    for (const entry of section.entries) {
      const leaves = isNavGroup(entry) ? entry.children : [entry]
      for (const leaf of leaves) {
        if (!pathMatchesLeaf(pathname, leaf.path)) continue
        if (!best || leaf.path.length > best.pathLength) {
          best = { labelKey: leaf.labelKey, pathLength: leaf.path.length }
        }
      }
    }
  }
  return best?.labelKey
}
