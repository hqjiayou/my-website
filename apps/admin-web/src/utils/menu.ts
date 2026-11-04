import type { LucideIcon } from 'lucide-react'
import {
  AppWindow,
  BarChart3,
  Circle,
  Layers,
  LayoutDashboard,
  Menu,
  Settings2,
  Shield,
  Users,
  Zap,
} from 'lucide-react'

import type { MenuNodeDto } from '@/types/rbac'

export interface SidebarLink {
  type: 'link'
  id: string
  label: string
  path: string
  icon: LucideIcon
}

export interface SidebarGroup {
  type: 'group'
  id: string
  label: string
  children: SidebarLink[]
}

export type SidebarEntry = SidebarLink | SidebarGroup

/** 后端 menu.icon → Lucide；未登记键回退 Circle。语义口径见 DESIGN_SYSTEM §12。 */
const ICON_MAP: Record<string, LucideIcon> = {
  setting: Settings2,
  settings: Settings2,
  user: Users,
  team: Users,
  safety: Shield,
  shield: Shield,
  menu: Menu,
  dashboard: LayoutDashboard,
  layers: Layers,
  appstore: AppWindow,
  apps: AppWindow,
  zap: Zap,
  chart: BarChart3,
}

function mapBackendIcon(icon: string | null | undefined): LucideIcon {
  if (!icon) return Circle
  const normalized = icon.trim().toLowerCase()
  return ICON_MAP[normalized] ?? Circle
}

function sortMenus(nodes: MenuNodeDto[]): MenuNodeDto[] {
  return [...nodes]
    .sort((a, b) => a.sort - b.sort)
    .map((node) => ({
      ...node,
      children: sortMenus(node.children ?? []),
    }))
}

function toSidebarLink(node: MenuNodeDto): SidebarLink | null {
  if (!node.path) return null
  return {
    type: 'link',
    id: node.menuCode,
    label: node.menuName,
    path: node.path,
    icon: mapBackendIcon(node.icon),
  }
}

function toSidebarEntry(node: MenuNodeDto): SidebarEntry | null {
  const childLinks = sortMenus(node.children ?? [])
    .map(toSidebarLink)
    .filter((item): item is SidebarLink => item != null)

  if (childLinks.length > 0) {
    return {
      type: 'group',
      id: node.menuCode,
      label: node.menuName,
      children: childLinks,
    }
  }

  return toSidebarLink(node)
}

/** 将后端 menus 树转为侧栏可渲染结构。 */
export function buildSidebarEntries(menus: MenuNodeDto[]): SidebarEntry[] {
  return sortMenus(menus)
    .map(toSidebarEntry)
    .filter((entry): entry is SidebarEntry => entry != null)
}

/**
 * 从后端菜单树解析顶栏标题（最长 path 前缀匹配）。
 * 用于静态 NAV 未覆盖、但侧栏已有的真实菜单页，避免再维护一份标题表。
 */
export function resolveMenuTitle(menus: MenuNodeDto[], pathname: string): string | undefined {
  let best: { pathLength: number; name: string } | undefined

  function walk(nodes: MenuNodeDto[]) {
    for (const node of nodes) {
      const path = node.path?.trim()
      if (path && (pathname === path || pathname.startsWith(`${path}/`))) {
        if (!best || path.length > best.pathLength) {
          best = { pathLength: path.length, name: node.menuName }
        }
      }
      if (node.children?.length) walk(node.children)
    }
  }

  walk(menus)
  return best?.name
}

export function sidebarGroupHasActiveChild(group: SidebarGroup, pathname: string) {
  return group.children.some((child) => pathMatchesSidebarLink(pathname, child.path))
}

export function pathMatchesSidebarLink(pathname: string, path: string) {
  return pathname === path || pathname.startsWith(`${path}/`)
}
