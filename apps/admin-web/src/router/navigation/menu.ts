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
      { labelKey: 'items.aiSettings', path: ROUTES.ai.settings, icon: Bot },
      { labelKey: 'items.aiPrompts', path: ROUTES.ai.prompts, icon: Braces },
      { labelKey: 'items.aiKnowledgeBases', path: ROUTES.ai.knowledgeBases, icon: BookOpen },
      { labelKey: 'items.aiConversations', path: ROUTES.ai.conversations, icon: MessageSquare },
    ],
  },
  {
    id: 'config',
    titleKey: 'sections.config',
    entries: [{ labelKey: 'items.paramsConfig', path: ROUTES.config.params, icon: SlidersHorizontal }],
  },
  {
    id: 'governance',
    titleKey: 'sections.governance',
    entries: [
      { labelKey: 'items.analytics', path: ROUTES.governance.analytics, icon: BarChart3 },
      { labelKey: 'items.system', path: ROUTES.governance.system, icon: Settings2 },
      { labelKey: 'items.developer', path: ROUTES.governance.developer, icon: Braces },
    ],
  },
]

/** 根据 pathname 解析顶栏 i18n 标题 key（站点工作区等动态路径单独处理）。 */
export function resolveNavTitleKey(pathname: string): string | undefined {
  if (pathname === ROUTES.apps.siteCreate || pathname.startsWith(`${ROUTES.apps.siteCreate}/`)) {
    return 'items.siteCreate'
  }
  if (/^\/apps\/sites\/[^/]+\/?$/.test(pathname)) {
    return 'items.siteAgentOverview'
  }
  if (/^\/apps\/sites\/[^/]+\/agent\/?$/.test(pathname)) {
    return 'items.siteAgentOverview'
  }
  if (/^\/apps\/sites\/[^/]+\/agent\/prompt(?:\/|$)/.test(pathname)) {
    return 'items.siteAgentPrompt'
  }
  if (/^\/apps\/sites\/[^/]+\/agent\/knowledge(?:\/|$)/.test(pathname)) {
    return 'items.siteAgentKnowledge'
  }
  if (/^\/apps\/sites\/[^/]+\/agent\/conversations(?:\/|$)/.test(pathname)) {
    return 'items.siteAgentConversations'
  }
  if (/^\/apps\/sites\/[^/]+\/agent\/preview(?:\/|$)/.test(pathname)) {
    return 'items.siteAgentPreview'
  }
  if (/^\/apps\/sites\/[^/]+\/agent\/runtime(?:\/|$)/.test(pathname)) {
    return 'items.siteAgentRuntime'
  }
  if (/^\/apps\/sites\/[^/]+\/basic(?:\/|$)/.test(pathname)) {
    return 'items.siteBasic'
  }
  if (/^\/apps\/sites\/[^/]+\/theme(?:\/|$)/.test(pathname)) {
    return 'items.siteTheme'
  }
  if (/^\/apps\/sites\/[^/]+\/domain(?:\/|$)/.test(pathname)) {
    return 'items.siteDomain'
  }
  if (/^\/apps\/sites\/[^/]+\/contents(?:\/|$)/.test(pathname)) {
    return 'items.siteContents'
  }
  if (/^\/apps\/sites\/[^/]+\/files(?:\/|$)/.test(pathname)) {
    return 'items.siteFiles'
  }

  for (const section of NAV_SECTIONS) {
    for (const entry of section.entries) {
      if (isNavGroup(entry)) {
        for (const child of entry.children) {
          if (pathMatchesLeaf(pathname, child.path)) {
            return child.labelKey
          }
        }
      } else if (pathMatchesLeaf(pathname, entry.path)) {
        return entry.labelKey
      }
    }
  }
  return undefined
}
