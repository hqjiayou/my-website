import { ChevronDown, X } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, useLocation } from 'react-router-dom'

import { focusInitialInPanel, registerModalLayer } from '@/components/admin/modalLayer'
import { cn } from '@/lib/cn'
import { useAdminPreferencesStore } from '@/stores/adminPreferencesStore'
import { useAuthStore } from '@/stores/authStore'
import { useLayoutStore } from '@/stores/layout'
import { buildSidebarEntries, sidebarGroupHasActiveChild, type SidebarGroup, type SidebarLink } from '@/utils/menu'

/** ≥lg 才应用桌面折叠宽；移动端抽屉始终 256，避免继承 64px 图标栏。 */
function useDesktopSidebar() {
  const [desktop, setDesktop] = useState(() =>
    typeof window !== 'undefined' ? window.matchMedia('(min-width: 1024px)').matches : true,
  )
  useEffect(() => {
    const media = window.matchMedia('(min-width: 1024px)')
    const sync = () => setDesktop(media.matches)
    sync()
    media.addEventListener('change', sync)
    return () => media.removeEventListener('change', sync)
  }, [])
  return desktop
}

/** 左侧导航壳（含移动端遮罩）。菜单数据来自 auth，属业务配置，此处只负责展示与交互。 */
export function Sidebar() {
  const location = useLocation()
  const { t: tLayout } = useTranslation('layout')
  const { isSidebarOpen, closeSidebar } = useLayoutStore()
  const sidebarCollapsed = useAdminPreferencesStore((state) => state.preferences.sidebarCollapsed)
  const menus = useAuthStore((state) => state.menus)
  const sidebarEntries = useMemo(() => buildSidebarEntries(menus), [menus])
  const desktop = useDesktopSidebar()
  const compact = sidebarCollapsed && desktop
  const asideRef = useRef<HTMLElement>(null)
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const previouslyFocused = useRef<HTMLElement | null>(null)

  const [manualGroupOpen, setManualGroupOpen] = useState<Record<string, boolean>>({})

  function isGroupExpanded(group: SidebarGroup) {
    if (compact) return false
    if (sidebarGroupHasActiveChild(group, location.pathname)) return true
    return manualGroupOpen[group.id] === true
  }

  useEffect(() => {
    closeSidebar()
  }, [location.pathname, closeSidebar])

  // 窄屏抽屉：Esc / Tab 约束 / 打开聚焦 / 关闭还原焦点；桌面常驻侧栏不走模态层。
  useEffect(() => {
    if (desktop || !isSidebarOpen) return
    previouslyFocused.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null
    const panel = asideRef.current
    const release = registerModalLayer({
      panel,
      onEscape: closeSidebar,
    })
    const focusTimer = window.setTimeout(() => {
      if (panel) focusInitialInPanel(panel, closeButtonRef.current)
    }, 0)
    return () => {
      window.clearTimeout(focusTimer)
      release()
      previouslyFocused.current?.focus?.()
    }
  }, [closeSidebar, desktop, isSidebarOpen])

  function toggleGroup(group: SidebarGroup) {
    if (compact) return
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
        className={cn(
          'admin-nav-item admin-focus-ring flex items-center text-sm font-medium',
          compact ? 'justify-center px-0' : 'gap-3 px-3',
        )}
      >
        <item.icon className="h-4.5 w-4.5 shrink-0" />
        {!compact ? <span className="truncate">{item.label}</span> : null}
      </NavLink>
    )
  }

  function renderGroup(group: SidebarGroup) {
    if (compact) {
      return (
        <div key={group.id} className="flex flex-col gap-1">
          {group.children.map((child) => renderLeafLink(child, true))}
        </div>
      )
    }

    const expanded = isGroupExpanded(group)
    const activeChild = sidebarGroupHasActiveChild(group, location.pathname)

    return (
      <div key={group.id}>
        <button
          type="button"
          onClick={() => toggleGroup(group)}
          className={cn(
            'admin-nav-group-title admin-focus-ring mb-1 flex w-full items-center justify-between px-3 text-left',
            activeChild && 'cursor-default',
          )}
          aria-expanded={expanded}
        >
          <span className="truncate">{group.label}</span>
          <ChevronDown
            className={cn(
              'h-3.5 w-3.5 shrink-0 transition-transform duration-200',
              expanded ? 'rotate-0' : '-rotate-90',
              activeChild && 'opacity-40',
            )}
          />
        </button>
        {expanded ? (
          <div className="flex flex-col gap-0.5">
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
          'admin-overlay fixed inset-0 z-40 transition-opacity lg:hidden',
          isSidebarOpen ? 'opacity-100' : 'pointer-events-none opacity-0',
        )}
        onClick={closeSidebar}
      />
      <aside
        ref={asideRef}
        id="admin-sidebar"
        tabIndex={-1}
        role={desktop ? 'navigation' : isSidebarOpen ? 'dialog' : 'navigation'}
        aria-modal={desktop || !isSidebarOpen ? undefined : true}
        aria-label={tLayout('sidebar.title')}
        className={cn(
          // 手机抽屉固定 w-64（256）；桌面折叠才变 lg:w-16，不把折叠偏好带进抽屉。
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-admin bg-admin-sidebar outline-none transition-all duration-200 ease-out lg:translate-x-0',
          compact && 'lg:w-16',
          isSidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div
          className={cn(
            'flex h-16 shrink-0 items-center gap-3 border-b border-admin px-4 max-lg:pr-12',
            compact && 'lg:justify-center lg:px-2',
          )}
        >
          <span className="admin-brand-mark" title={tLayout('sidebar.title')}>
            MW
          </span>
          {!compact ? (
            <div className="min-w-0">
              <p className="text-[10px] font-semibold uppercase tracking-[0.22em] text-admin-muted">{tLayout('sidebar.kicker')}</p>
              <p className="truncate text-[15px] font-semibold leading-tight text-admin">{tLayout('sidebar.title')}</p>
            </div>
          ) : null}
          <button
            ref={closeButtonRef}
            type="button"
            onClick={closeSidebar}
            className="admin-icon-button admin-focus-ring absolute top-4 right-2 lg:hidden"
            aria-label={tLayout('preferences.close')}
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <nav className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto px-3 py-4">
          {sidebarEntries.length === 0 ? (
            <p className={cn('text-sm text-admin-muted', compact ? 'text-center' : 'px-3')}>
              {compact ? '—' : tLayout('sidebar.emptyMenus')}
            </p>
          ) : (
            sidebarEntries.map((entry) =>
              entry.type === 'group' ? (
                renderGroup(entry)
              ) : (
                <div key={entry.path}>{renderLeafLink(entry, compact)}</div>
              ),
            )
          )}
        </nav>
      </aside>
    </>
  )
}
