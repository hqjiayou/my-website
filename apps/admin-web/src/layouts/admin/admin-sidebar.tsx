import { ChevronDown, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, useLocation } from 'react-router-dom'

import { cn } from '@/lib/cn'
import { isNavGroup, NAV_SECTIONS, type NavGroup, type NavLeaf } from '@/navigation/menu'
import { pathMatchesLeaf } from '@/navigation/nav-path'
import { useLayoutStore } from '@/stores/layout'

function groupHasActiveChild(group: NavGroup, pathname: string) {
  return group.children.some((c) => pathMatchesLeaf(pathname, c.path))
}

export function AdminSidebar() {
  const location = useLocation()
  const { t } = useTranslation('nav')
  const { t: tLayout } = useTranslation('layout')
  const { isSidebarOpen, closeSidebar } = useLayoutStore()

  const [manualGroupOpen, setManualGroupOpen] = useState<Record<string, boolean>>({})

  function isGroupExpanded(group: NavGroup) {
    if (groupHasActiveChild(group, location.pathname)) return true
    return manualGroupOpen[group.id] === true
  }

  useEffect(() => {
    closeSidebar()
  }, [location.pathname, closeSidebar])

  function toggleGroup(group: NavGroup) {
    if (groupHasActiveChild(group, location.pathname)) return
    setManualGroupOpen((prev) => {
      const expandedNow = prev[group.id] === true
      return { ...prev, [group.id]: !expandedNow }
    })
  }

  function renderLeafLink(item: NavLeaf) {
    return (
      <NavLink
        key={item.path}
        to={item.path}
        end
        className={({ isActive }) =>
          cn(
            'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
            isActive
              ? 'bg-slate-900 text-white shadow-sm'
              : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900',
          )
        }
      >
        <item.icon className="h-4 w-4 shrink-0 opacity-90" />
        <span>{t(item.labelKey)}</span>
      </NavLink>
    )
  }

  function renderGroup(group: NavGroup) {
    const expanded = isGroupExpanded(group)
    const activeChild = groupHasActiveChild(group, location.pathname)

    return (
      <div key={group.id} className="space-y-1">
        <button
          type="button"
          onClick={() => toggleGroup(group)}
          className={cn(
            'flex w-full items-center justify-between rounded-lg px-3 py-2 text-left text-xs font-semibold uppercase tracking-[0.12em] transition-colors',
            activeChild ? 'text-slate-800' : 'text-slate-400 hover:text-slate-600',
            activeChild && 'cursor-default',
          )}
          aria-expanded={expanded}
        >
          <span>{t(group.labelKey)}</span>
          <ChevronDown
            className={cn(
              'h-3.5 w-3.5 shrink-0 text-slate-400 transition-transform duration-200',
              expanded ? 'rotate-0' : '-rotate-90',
              activeChild && 'opacity-40',
            )}
          />
        </button>
        {expanded ? (
          <div className="relative ml-2 space-y-0.5 border-l border-slate-200 pl-3">
            {group.children.map((child) => renderLeafLink(child))}
          </div>
        ) : null}
      </div>
    )
  }

  return (
    <aside
      className={cn(
        'fixed inset-y-0 left-0 z-40 flex w-72 flex-col border-r border-slate-200 bg-white p-4 shadow-sm transition-transform lg:translate-x-0',
        isSidebarOpen ? 'translate-x-0' : '-translate-x-full',
      )}
    >
      <div className="mb-6 flex shrink-0 items-center justify-between">
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-[0.22em] text-slate-400">{tLayout('sidebar.kicker')}</p>
          <p className="text-lg font-semibold tracking-tight text-slate-900">{tLayout('sidebar.title')}</p>
        </div>
        <button
          type="button"
          onClick={closeSidebar}
          className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-slate-200 text-slate-500 transition hover:bg-slate-50 lg:hidden"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      <nav className="min-h-0 flex-1 space-y-6 overflow-y-auto pr-1">
        {NAV_SECTIONS.map((section, sectionIndex) => (
          <div key={section.id}>
            {sectionIndex > 0 ? <div className="mb-5 border-t border-slate-100" /> : null}
            {section.titleKey ? (
              <p className="mb-3 px-3 text-[11px] font-semibold uppercase tracking-[0.14em] text-slate-400">
                {t(section.titleKey)}
              </p>
            ) : null}
            <div className="space-y-3">
              {section.entries.map((entry) =>
                isNavGroup(entry) ? renderGroup(entry) : <div key={entry.path}>{renderLeafLink(entry)}</div>,
              )}
            </div>
          </div>
        ))}
      </nav>
    </aside>
  )
}
