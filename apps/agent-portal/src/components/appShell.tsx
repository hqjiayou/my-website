import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'

import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import { errorMessage } from '@/utils/http/apiError'

import { SidebarExtraContext } from './sidebarExtra'

/**
 * 全站唯一的导航：左侧边栏（桌面常驻、移动端抽屉）。
 * 页面不再各自带 header / 底部 tab；聊天页经 SidebarExtraContext
 * 把会话列表注入侧边栏下半区，导航仍只有这一处。
 */

const NAV_ITEMS = [
  { to: ROUTES.home, label: '首頁', end: true },
  { to: ROUTES.chat, label: 'AI 對話', end: false },
  { to: ROUTES.knowledge, label: '知識庫', end: true },
  { to: ROUTES.memories, label: '我的記憶', end: true },
] as const

function sectionLabel(pathname: string): string {
  if (pathname.startsWith(ROUTES.chat)) return 'AI 對話'
  if (pathname.startsWith(ROUTES.knowledge)) return '知識庫'
  if (pathname.startsWith(ROUTES.memories)) return '我的記憶'
  return '首頁'
}

export function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()
  const displayName = useAuthStore((state) => state.user?.displayName || state.user?.username || '')
  const siteName = useAuthStore((state) => state.site?.siteName || 'Agent Portal')
  const logout = useAuthStore((state) => state.logout)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [extra, setExtraState] = useState<ReactNode>(null)
  const [loggingOut, setLoggingOut] = useState(false)
  const [logoutError, setLogoutError] = useState<string | null>(null)

  const setExtra = useCallback((node: ReactNode) => setExtraState(node), [])
  const closeDrawer = useCallback(() => setDrawerOpen(false), [])
  const contextValue = useMemo(() => ({ setExtra, closeDrawer }), [setExtra, closeDrawer])

  async function onLogout() {
    setLoggingOut(true)
    setLogoutError(null)
    try {
      await logout()
      navigate(ROUTES.login, { replace: true })
    } catch (error) {
      setLogoutError(errorMessage(error))
      setLoggingOut(false)
    }
  }

  const sidebar = (
    <div className="flex h-full min-h-0 w-full flex-col bg-gpt-sidebar">
      <div className="safe-top px-4 pb-2 pt-4">
        <p className="truncate text-base font-semibold tracking-tight">{siteName}</p>
      </div>
      <nav aria-label="主要導覽" className="px-2">
        <ul className="flex flex-col gap-0.5">
          {NAV_ITEMS.map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.end}
                onClick={() => setDrawerOpen(false)}
                className={({ isActive }) =>
                  isActive
                    ? 'block rounded-lg bg-primary-soft px-3 py-2 text-sm font-semibold text-primary'
                    : 'block rounded-lg px-3 py-2 text-sm text-gpt-ink hover:bg-gpt-hover'
                }
              >
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      {extra ? <div className="mt-3 flex min-h-0 flex-1 flex-col border-t border-gpt-line">{extra}</div> : null}
      <footer className="safe-bottom mt-auto border-t border-gpt-line px-3 py-3">
        <p className="truncate px-2 text-sm">{displayName || '已登入'}</p>
        {logoutError ? <p className="px-2 pt-1 text-xs text-danger">{logoutError}</p> : null}
        <button
          type="button"
          onClick={() => void onLogout()}
          disabled={loggingOut}
          className="mt-1 rounded-lg px-2 py-1.5 text-xs text-gpt-muted hover:bg-gpt-hover disabled:opacity-60"
        >
          {loggingOut ? '正在登出…' : '登出'}
        </button>
      </footer>
    </div>
  )

  return (
    <SidebarExtraContext.Provider value={contextValue}>
      <div className="flex h-dvh w-full bg-background text-gpt-ink">
        {drawerOpen ? (
          <button
            type="button"
            aria-label="關閉選單"
            className="fixed inset-0 z-30 bg-gpt-ink/30 md:hidden"
            onClick={() => setDrawerOpen(false)}
          />
        ) : null}
        <aside
          className={
            drawerOpen
              ? 'fixed inset-y-0 left-0 z-40 flex w-[268px] shrink-0 border-r border-gpt-line md:static md:z-auto'
              : 'hidden w-[268px] shrink-0 border-r border-gpt-line md:flex'
          }
        >
          {sidebar}
        </aside>
        <div className="flex min-w-0 flex-1 flex-col">
          <div className="safe-top flex h-12 shrink-0 items-center gap-2 border-b border-gpt-line px-2 md:hidden">
            <button
              type="button"
              onClick={() => setDrawerOpen(true)}
              className="h-10 rounded-lg px-3 text-sm hover:bg-gpt-hover"
            >
              選單
            </button>
            <p className="min-w-0 flex-1 truncate text-sm font-medium">{sectionLabel(location.pathname)}</p>
          </div>
          <div className="flex min-h-0 flex-1 flex-col">
            <Outlet />
          </div>
        </div>
      </div>
    </SidebarExtraContext.Provider>
  )
}
