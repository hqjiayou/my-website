import type { LucideIcon } from 'lucide-react'
import {
  BarChart3,
  BookOpen,
  Bot,
  Braces,
  CalendarClock,
  FileText,
  FolderKanban,
  LayoutDashboard,
  LayoutTemplate,
  Layers3,
  MessageSquare,
  Palette,
  Settings2,
  SlidersHorizontal,
  Users,
} from 'lucide-react'

import { ROUTES } from '@/router/route-paths'

import { pathMatchesLeaf } from './nav-path'

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
          { labelKey: 'items.templates', path: ROUTES.apps.templates, icon: LayoutTemplate },
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
      { labelKey: 'items.reservations', path: ROUTES.operation.reservations, icon: CalendarClock },
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
      { labelKey: 'items.team', path: ROUTES.governance.team, icon: Users },
      { labelKey: 'items.system', path: ROUTES.governance.system, icon: Settings2 },
      { labelKey: 'items.developer', path: ROUTES.governance.developer, icon: Braces },
    ],
  },
]

export function resolveNavTitleKey(pathname: string): string | undefined {
  if (pathname === ROUTES.apps.siteCreate || pathname.startsWith(`${ROUTES.apps.siteCreate}/`)) {
    return 'items.siteCreate'
  }
  if (/^\/apps\/sites\/[^/]+(?:\/overview)?\/?$/.test(pathname)) {
    return 'items.siteOverview'
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
  if (/^\/apps\/sites\/[^/]+\/reservations(?:\/|$)/.test(pathname)) {
    return 'items.siteReservations'
  }
  if (/^\/apps\/sites\/[^/]+\/ai(?:\/|$)/.test(pathname)) {
    return 'items.siteAi'
  }
  if (/^\/apps\/sites\/[^/]+\/files(?:\/|$)/.test(pathname)) {
    return 'items.siteFiles'
  }
  if (/^\/apps\/templates\/[^/]+(?:\/|$)/.test(pathname)) {
    return 'items.templateDetail'
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
