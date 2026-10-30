import { useEffect } from 'react'

import { applyPreferencesToDocument } from '@/config/preferences'
import { cn } from '@/lib/cn'
import { useAdminPreferencesStore } from '@/stores/adminPreferencesStore'

import { Header } from './components/header'
import { MainContainer } from './components/mainContainer'
import { Sidebar } from './components/sidebar'

/**
 * 后台基础布局：Sidebar + Header + Main 三栏组合。
 * 仅负责壳层结构与偏好同步，业务菜单等内容由各自模块注入。
 * 内容最大宽类在 theme.css：admin-content-data / admin-content-form / admin-content-doc。
 */
export function AdminLayout() {
  const sidebarCollapsed = useAdminPreferencesStore((state) => state.preferences.sidebarCollapsed)

  return (
    <div className="min-h-screen bg-admin-page text-admin">
      <SystemThemeSync />
      <Sidebar />
      <div className={cn('transition-[padding] duration-200 ease-out', sidebarCollapsed ? 'lg:pl-16' : 'lg:pl-64')}>
        <Header />
        <MainContainer />
      </div>
    </div>
  )
}

/** themeMode=system 时跟随 prefers-color-scheme。 */
function SystemThemeSync() {
  const themeMode = useAdminPreferencesStore((state) => state.preferences.themeMode)
  const preferences = useAdminPreferencesStore((state) => state.preferences)

  useEffect(() => {
    if (themeMode !== 'system') return

    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const handleChange = () => applyPreferencesToDocument(preferences)

    handleChange()
    media.addEventListener('change', handleChange)
    return () => media.removeEventListener('change', handleChange)
  }, [preferences, themeMode])

  return null
}
