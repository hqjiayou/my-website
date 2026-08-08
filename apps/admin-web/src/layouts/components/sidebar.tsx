import { ChevronDown, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, useLocation } from 'react-router-dom'

import { cn } from '@/lib/cn'
import { useAdminPreferencesStore } from '@/stores/adminPreferencesStore'
import { useAuthStore } from '@/stores/authStore'
import { useLayoutStore } from '@/stores/layout'
import { buildSidebarEntries, sidebarGroupHasActiveChild, type SidebarGroup, type SidebarLink } from '@/utils/menu'

/** 左侧导航壳（含移动端遮罩）。菜单数据来自 auth，属业务配置，此处只负责展示与交互。 */
export function Sidebar() {
  const location = useLocation()
  const { t: tLayout } = useTranslation('layout')
  const { isSidebarOpen, closeSidebar } = useLayoutStore()
  const sidebarCollapsed = useAdminPreferencesStore((state) => state.preferences.sidebarCollapsed)
  const menus = useAuthStore((state) => state.menus)
  const sidebarEntries = useMemo(() => buildSidebarEntries(menus), [menus])

  const [manualGroupOpen, setManualGroupOpen] = useState<Record<string, boolean>>({})

  function isGroupExpanded(group: SidebarGroup) {
    if (sidebarCollapsed) return false
    if (sidebarGroupHasActiveChild(group, location.pathname)) return true
    return manualGroupOpen[group.id] === true
  }

  useEffect(() => {
    closeSidebar()
  }, [location.pathname, closeSidebar])

  function toggleGroup(group: SidebarGroup) {
    if (sidebarCollapsed) return
    if (sidebarGroupHasActiveChild(group, location.pathname)) return
    setManualGroupOpen((prev) => {
      const expandedNow = prev[group.id] === true
      return { ...prev, [group.id]: !expandedNow }
    })
  }

  function renderLeafLink(item: SidebarLink, compact = false) {
    return (
      <NavLink
        key={item.path}
        to={item.path}
        end
        title={compact ? item.label : undefined}
        className={({ isActive }) =>
          cn(
            'flex items-center rounded-lg text-sm font-medium transition-colors admin-control',
            compact ? 'justify-center px-2' : 'gap-3 px-3',
            isActive
              ? 'bg-admin-primary text-admin-on-primary shadow-sm'
              : 'text-admin-muted hover:bg-admin-surface-muted hover:text-admin',
          )
        }
      >
        <item.icon className="h-4 w-4 shrink-0 opacity-90" />
        {!compact ? <span>{item.label}</span> : null}
      </NavLink>
    )
  }

  function renderGroup(group: SidebarGroup) {
    if (sidebarCollapsed) {
      return (
        <div key={group.id} className="space-y-1">
          {group.children.map((child) => renderLeafLink(child, true))}
        </div>
      )
    }

    const expanded = isGroupExpanded(group)
    const activeChild = sidebarGroupHasActiveChild(group, location.pathname)

    return (
      <div key={group.id} className="space-y-1">
        <button
          type="button"
          onClick={() => toggleGroup(group)}
          className={cn(
            'flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-xs font-semibold uppercase tracking-[0.12em] transition-colors admin-control',
            activeChild ? 'text-admin' : 'text-admin-muted hover:text-admin',
            activeChild && 'cursor-default',
          )}
          aria-expanded={expanded}
        >
          <span>{group.label}</span>
          <ChevronDown
            className={cn(
              'h-3.5 w-3.5 shrink-0 text-admin-muted transition-transform duration-200',
              expanded ? 'rotate-0' : '-rotate-90',
              activeChild && 'opacity-40',
            )}
          />
        </button>
        {expanded ? (
          <div className="relative ml-2 space-y-0.5 border-l border-admin pl-3">
            {group.children.map((child) => renderLeafLink(child))}
          </div>
        ) : null}
      </div>
    )
  }

  return (
    <>
      <div
        aria-hidden
        className={cn(
          'fixed inset-0 z-30 bg-slate-900/40 transition-opacity lg:hidden',
          isSidebarOpen ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
        onClick={closeSidebar}
      />
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex flex-col border-r border-admin bg-admin-surface p-4 shadow-sm transition-all duration-200 lg:translate-x-0',
          sidebarCollapsed ? 'w-16 px-2' : 'w-72',
          isSidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className={cn('mb-6 flex shrink-0 items-center', sidebarCollapsed ? 'justify-center' : 'justify-between')}>
          {!sidebarCollapsed ? (
            <div>
              <p className="text-[10px] font-semibold uppercase tracking-[0.22em] text-admin-muted">{tLayout('sidebar.kicker')}</p>
              <p className="text-lg font-semibold tracking-tight text-admin">{tLayout('sidebar.title')}</p>
            </div>
          ) : (
            <p className="text-xs font-bold text-admin" title={tLayout('sidebar.title')}>
              MW
            </p>
          )}
          <button
            type="button"
            onClick={closeSidebar}
            className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-admin text-admin-muted transition hover:bg-admin-surface-muted lg:hidden"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <nav className="min-h-0 flex-1 space-y-3 overflow-y-auto pr-1">
          {sidebarEntries.length === 0 ? (
            <p className={cn('text-sm text-admin-muted', sidebarCollapsed ? 'px-0 text-center' : 'px-3')}>
              {sidebarCollapsed ? '—' : tLayout('sidebar.emptyMenus')}
            </p>
          ) : (
            sidebarEntries.map((entry) =>
              entry.type === 'group' ? (
                renderGroup(entry)
              ) : (
                <div key={entry.path}>{renderLeafLink(entry, sidebarCollapsed)}</div>
              ),
            )
          )}
        </nav>
      </aside>
    </>
  )
}
