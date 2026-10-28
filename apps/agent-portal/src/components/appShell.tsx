import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { BookOpen, House, LogOut, MessageSquare, Moon, PanelLeftClose, PanelLeftOpen, Sparkles, Sun, X } from 'lucide-react'

import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import { errorMessage } from '@/utils/http/apiError'

import { SidebarExtraContext } from './sidebarExtra'

/**
 * 全站唯一的导航：左侧边栏（桌面可折叠、移动端抽屉），GPT 风格。
 * 折叠状态存 localStorage；折叠后主区顶部出现展开钮。页面不再各自带
 * header / 底部 tab；聊天页经 SidebarExtraContext 把会话列表注入
 * 侧边栏下半区，导航仍只有这一处。
 */

const NAV_ITEMS = [
  { to: ROUTES.home, label: '首頁', end: true, Icon: House },
  { to: ROUTES.chat, label: 'AI 對話', end: false, Icon: MessageSquare },
  { to: ROUTES.knowledge, label: '知識庫', end: true, Icon: BookOpen },
  { to: ROUTES.memories, label: '我的記憶', end: true, Icon: Sparkles },
] as const

const COLLAPSED_KEY = 'portal.sidebar.collapsed'

function sectionLabel(pathname: string): string {
  if (pathname.startsWith(ROUTES.chat)) return 'AI 對話'
  if (pathname.startsWith(ROUTES.knowledge)) return '知識庫'
  if (pathname.startsWith(ROUTES.memories)) return '我的記憶'
  return '首頁'
}

function readCollapsed(): boolean {
  try {
    return window.localStorage.getItem(COLLAPSED_KEY) === '1'
  } catch {
    return false
  }
}

export function AppShell() {
  const navigate = useNavigate()
  const location = useLocation()
  const displayName = useAuthStore((state) => state.user?.displayName || state.user?.username || '')
  const siteName = useAuthStore((state) => state.site?.siteName || 'Agent Portal')
  const logout = useAuthStore((state) => state.logout)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [collapsed, setCollapsed] = useState<boolean>(readCollapsed)
  const [dark, setDark] = useState<boolean>(() =>
    typeof document !== 'undefined' ? document.documentElement.classList.contains('dark') : false,
  )
  const [extra, setExtraState] = useState<ReactNode>(null)
  const [loggingOut, setLoggingOut] = useState(false)
  const [logoutError, setLogoutError] = useState<string | null>(null)

  const setExtra = useCallback((node: ReactNode) => setExtraState(node), [])
  const closeDrawer = useCallback(() => setDrawerOpen(false), [])
  const contextValue = useMemo(() => ({ setExtra, closeDrawer }), [setExtra, closeDrawer])

  useEffect(() => {
    try {
      window.localStorage.setItem(COLLAPSED_KEY, collapsed ? '1' : '0')
    } catch {
      // 存不了就只在本次会话内生效，不挡功能。
    }
  }, [collapsed])

  function toggleTheme() {
    const next = !dark
    setDark(next)
    document.documentElement.classList.toggle('dark', next)
    try {
      window.localStorage.setItem('portal.theme', next ? 'dark' : 'light')
    } catch {
      // 存不了就只改本次会话。
    }
  }

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
      <div className="safe-top flex items-center gap-2 px-3 pb-1 pt-3">
        <p className="min-w-0 flex-1 truncate px-1 text-[15px] font-semibold tracking-tight">{siteName}</p>
        <button
          type="button"
          aria-label="收起側邊欄"
          title="收起側邊欄"
          onClick={() => setCollapsed(true)}
          className="hidden h-9 w-9 shrink-0 items-center justify-center rounded-lg text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink md:flex"
        >
          <PanelLeftClose size={19} strokeWidth={1.8} />
        </button>
        <button
          type="button"
          aria-label="關閉選單"
          onClick={() => setDrawerOpen(false)}
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink md:hidden"
        >
          <X size={19} strokeWidth={1.8} />
        </button>
      </div>
      <nav aria-label="主要導覽" className="px-2 pt-1">
        <ul className="flex flex-col gap-0.5">
          {NAV_ITEMS.map((item) => (
            <li key={item.to}>
              <NavLink
                to={item.to}
                end={item.end}
                onClick={() => setDrawerOpen(false)}
                className={({ isActive }) =>
                  isActive
                    ? 'flex items-center gap-3 rounded-lg bg-gpt-active px-3 py-2 text-sm font-medium text-gpt-ink'
                    : 'flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-gpt-ink hover:bg-gpt-hover'
                }
              >
                <item.Icon size={18} strokeWidth={1.8} className="shrink-0 text-gpt-muted" />
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      {extra ? <div className="mt-3 flex min-h-0 flex-1 flex-col border-t border-gpt-line">{extra}</div> : null}
      <footer className="safe-bottom mt-auto border-t border-gpt-line px-3 py-3">
        <div className="flex items-center gap-2.5 px-1">
          <span
            aria-hidden="true"
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-semibold text-white"
          >
            {(displayName || 'A').slice(0, 1).toUpperCase()}
          </span>
          <p className="min-w-0 flex-1 truncate text-sm">{displayName || '已登入'}</p>
          <button
            type="button"
            onClick={toggleTheme}
            aria-label={dark ? '切換淺色模式' : '切換深色模式'}
            title={dark ? '淺色模式' : '深色模式'}
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink"
          >
            {dark ? <Sun size={17} strokeWidth={1.8} /> : <Moon size={17} strokeWidth={1.8} />}
          </button>
          <button
            type="button"
            onClick={() => void onLogout()}
            disabled={loggingOut}
            aria-label="登出"
            title="登出"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink disabled:opacity-60"
          >
            <LogOut size={17} strokeWidth={1.8} />
          </button>
        </div>
        {logoutError ? <p className="px-1 pt-1.5 text-xs text-danger">{logoutError}</p> : null}
        {loggingOut ? <p className="px-1 pt-1.5 text-xs text-gpt-muted">正在登出…</p> : null}
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
            className="fixed inset-0 z-30 bg-black/40 md:hidden"
            onClick={() => setDrawerOpen(false)}
          />
        ) : null}
        <aside
          className={
            drawerOpen
              ? 'fixed inset-y-0 left-0 z-40 flex w-[268px] shrink-0 border-r border-gpt-line md:static md:z-auto'
              : collapsed
                ? 'hidden w-[268px] shrink-0 border-r border-gpt-line'
                : 'hidden w-[268px] shrink-0 border-r border-gpt-line md:flex'
          }
        >
          {sidebar}
        </aside>
        <div className="flex min-w-0 flex-1 flex-col">
          <div
            className={
              collapsed
                ? 'safe-top flex h-12 shrink-0 items-center gap-1 border-b border-gpt-line px-2'
                : 'safe-top flex h-12 shrink-0 items-center gap-1 border-b border-gpt-line px-2 md:hidden'
            }
          >
            <button
              type="button"
              aria-label="開啟選單"
              title="開啟側邊欄"
              onClick={() => {
                if (window.matchMedia('(min-width: 768px)').matches) {
                  setCollapsed(false)
                } else {
                  setDrawerOpen(true)
                }
              }}
              className="flex h-10 w-10 items-center justify-center rounded-lg text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink"
            >
              <PanelLeftOpen size={20} strokeWidth={1.8} />
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
