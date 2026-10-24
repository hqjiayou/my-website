import { NavLink } from 'react-router-dom'

import { ROUTES } from '@/router/config/routePaths'

const TABS = [
  { to: ROUTES.home, label: '首頁', end: true },
  { to: ROUTES.chat, label: 'AI 對話', end: true },
  { to: ROUTES.knowledge, label: '知識搜尋', end: true },
] as const

/** 首頁與知識搜尋釘在視埠底部。對話頁改放進版面流，才能跟著鍵盤內縮一起上移。 */
export function BottomNav({ pinned = true }: { pinned?: boolean }) {
  return (
    <nav
      aria-label="主要導覽"
      className={
        pinned
          ? 'safe-x safe-bottom fixed inset-x-0 bottom-0 z-30 border-t border-border bg-surface'
          : 'safe-x safe-bottom shrink-0 border-t border-border bg-surface'
      }
    >
      <ul className="mx-auto flex w-full max-w-lg">
        {TABS.map((tab) => (
          <li key={tab.to} className="min-w-0 flex-1">
            <NavLink
              to={tab.to}
              end={tab.end}
              className={({ isActive }) =>
                isActive
                  ? 'flex h-[var(--bottom-nav-bar)] items-center justify-center px-1 text-sm font-semibold text-primary'
                  : 'flex h-[var(--bottom-nav-bar)] items-center justify-center px-1 text-sm text-muted-foreground'
              }
            >
              {({ isActive }) => (
                <span className={isActive ? 'max-w-full truncate rounded-pill bg-primary-soft px-3 py-1' : 'max-w-full truncate px-3 py-1'}>
                  {tab.label}
                </span>
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
