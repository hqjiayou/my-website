import { ChevronDown, LogOut, Menu, PanelLeftClose, PanelLeftOpen, Search, Settings2, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router-dom'

import { adminDangerGhostButtonMdClass } from '@/components/admin/controls'
import type { FontSize, PageDensity, PrimaryColor, ThemeMode } from '@/config/preferences'
import { SUPPORTED_LOCALES, type SupportedLocale } from '@/i18n/constants'
import { cn } from '@/lib/cn'
import { resolveNavTitleKey } from '@/router/navigation'
import { ROUTES } from '@/router/config/routePaths'
import { useAdminPreferencesStore } from '@/stores/adminPreferencesStore'
import { useAuthStore } from '@/stores/authStore'
import { useLayoutStore } from '@/stores/layout'
import { resolveMenuTitle } from '@/utils/menu'

/** 顶栏壳：标题、搜索、用户操作与外观设置。 */
export function Header() {
  const location = useLocation()
  const navigate = useNavigate()
  const { t: tNav } = useTranslation('nav')
  const { t: tLayout } = useTranslation('layout')
  const { toggleSidebar } = useLayoutStore()
  const sidebarCollapsed = useAdminPreferencesStore((state) => state.preferences.sidebarCollapsed)
  const pageDensity = useAdminPreferencesStore((state) => state.preferences.pageDensity)
  const setSidebarCollapsed = useAdminPreferencesStore((state) => state.setSidebarCollapsed)
  const setPageDensity = useAdminPreferencesStore((state) => state.setPageDensity)
  const user = useAuthStore((state) => state.user)
  const menus = useAuthStore((state) => state.menus)
  const logout = useAuthStore((state) => state.logout)

  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const [appearanceOpen, setAppearanceOpen] = useState(false)
  const userMenuRef = useRef<HTMLDivElement>(null)

  // 静态 nav（工作区子页等）优先；其余能从后端菜单派生的标题不再双维护
  const titleKey = resolveNavTitleKey(location.pathname)
  const menuTitle = resolveMenuTitle(menus, location.pathname)
  const pageTitle = titleKey ? tNav(titleKey) : menuTitle ?? tLayout('fallbackPageTitle')
  const displayName = user?.displayName || user?.username || tLayout('header.anonymous')
  const avatarLabel = displayName.trim().slice(0, 1).toUpperCase()
  const densities: PageDensity[] = ['compact', 'default', 'comfortable']

  useEffect(() => {
    function handlePointerDown(event: MouseEvent) {
      if (!userMenuRef.current?.contains(event.target as Node)) {
        setUserMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handlePointerDown)
    return () => document.removeEventListener('mousedown', handlePointerDown)
  }, [])

  async function handleLogout() {
    if (!window.confirm(tLayout('userMenu.logoutConfirm'))) return
    await logout()
    navigate(ROUTES.login, { replace: true })
  }

  return (
    <>
      <header
        className="sticky top-0 z-20 border-b border-admin backdrop-blur"
        style={{ background: 'var(--header-background)' }}
      >
        <div className="flex h-16 items-center gap-3 px-4 sm:px-6">
          <button
            type="button"
            onClick={toggleSidebar}
            className="admin-icon-button admin-focus-ring lg:hidden"
          >
            <Menu className="h-5 w-5" />
          </button>

          <button
            type="button"
            onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
            className="admin-icon-button admin-focus-ring hidden lg:inline-flex"
            aria-label={sidebarCollapsed ? tLayout('preferences.sidebar.collapsed') : tLayout('sidebar.title')}
          >
            {sidebarCollapsed ? <PanelLeftOpen className="h-5 w-5" /> : <PanelLeftClose className="h-5 w-5" />}
          </button>

          <nav className="mr-auto min-w-0 text-sm" aria-label={tLayout('header.subtitle')}>
            <p className="truncate font-medium text-primary">{pageTitle}</p>
          </nav>

          <div className="admin-seg hidden lg:flex" role="group" aria-label={tLayout('preferences.pageDensity.label')}>
            {densities.map((density) => (
              <button
                key={density}
                type="button"
                className={cn('admin-focus-ring', pageDensity === density && 'is-on')}
                onClick={() => setPageDensity(density)}
              >
                {tLayout(`preferences.pageDensity.${density}`)}
              </button>
            ))}
          </div>

          <label className="admin-focus-within hidden h-9 w-60 items-center gap-2 rounded-lg border border-admin bg-admin-surface-muted px-3 text-sm text-admin-muted transition md:flex">
            <Search className="h-4 w-4 shrink-0" />
            <input
              className="w-full border-none bg-transparent text-sm text-admin outline-none placeholder:text-admin-muted"
              placeholder={tLayout('search.placeholder')}
            />
          </label>

          <div ref={userMenuRef} className="relative">
            <button
              type="button"
              onClick={() => setUserMenuOpen((open) => !open)}
              className="admin-focus-ring inline-flex max-w-[12rem] items-center gap-2 rounded-lg px-1.5 py-1 text-sm text-admin transition hover:bg-primary/7"
            >
              <span className="admin-avatar" aria-hidden>
                {avatarLabel}
              </span>
              <span className="truncate">{displayName}</span>
              <ChevronDown className={cn('h-4 w-4 shrink-0 text-admin-muted transition', userMenuOpen && 'rotate-180')} />
            </button>

            {userMenuOpen ? (
              <div className="shadow-admin-pop absolute right-0 z-30 mt-2 w-[200px] overflow-hidden rounded-lg border border-admin bg-admin-surface py-1">
                <button
                  type="button"
                  onClick={() => {
                    setUserMenuOpen(false)
                    setAppearanceOpen(true)
                  }}
                  className="admin-focus-ring flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-admin transition hover:bg-admin-surface-muted"
                >
                  <Settings2 className="h-4 w-4 text-admin-muted" />
                  {tLayout('userMenu.appearance')}
                </button>
                <button
                  type="button"
                  onClick={() => void handleLogout()}
                  className="admin-focus-ring flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-admin-danger transition hover:bg-admin-danger-muted"
                >
                  <LogOut className="h-4 w-4" />
                  {tLayout('userMenu.logout')}
                </button>
              </div>
            ) : null}
          </div>
        </div>
      </header>

      <AppearanceDrawer open={appearanceOpen} onClose={() => setAppearanceOpen(false)} />
    </>
  )
}

function AppearanceDrawer({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation('layout')
  const preferences = useAdminPreferencesStore((state) => state.preferences)
  const {
    setThemeMode,
    setFontSize,
    setPageDensity,
    setPrimaryColor,
    setSidebarCollapsed,
    setLocale,
    resetPreferences,
  } = useAdminPreferencesStore()

  if (!open) return null

  const themeModes: ThemeMode[] = ['light', 'dark', 'system']
  const fontSizes: FontSize[] = ['small', 'medium', 'large']
  const densities: PageDensity[] = ['comfortable', 'default', 'compact']
  const primaryColors: PrimaryColor[] = ['blue', 'purple', 'green', 'orange']

  function handleClearLocal() {
    if (!window.confirm(t('preferences.clearConfirm'))) return
    resetPreferences()
  }

  return (
    <>
      <button
        type="button"
        className="admin-overlay fixed inset-0 z-40"
        aria-label={t('preferences.close')}
        onClick={onClose}
      />
      <aside
        className="shadow-admin-overlay fixed inset-y-0 right-0 z-50 flex w-full max-w-[560px] flex-col border-l border-admin bg-admin-surface"
        role="dialog"
        aria-modal="true"
        aria-labelledby="appearance-settings-title"
      >
        <div className="flex items-center justify-between border-b border-admin px-5 py-4">
          <div>
            <h2 id="appearance-settings-title" className="text-lg font-semibold text-admin">
              {t('preferences.title')}
            </h2>
            <p className="mt-0.5 text-sm text-admin-muted">{t('preferences.description')}</p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="admin-icon-button admin-focus-ring"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="flex-1 space-y-6 overflow-y-auto px-5 py-5">
          <PreferenceOptionGroup
            label={t('preferences.themeMode.label')}
            value={preferences.themeMode}
            options={themeModes}
            onChange={setThemeMode}
            formatLabel={(mode) => t(`preferences.themeMode.${mode}`)}
          />

          <PreferenceOptionGroup
            label={t('preferences.fontSize.label')}
            value={preferences.fontSize}
            options={fontSizes}
            onChange={setFontSize}
            formatLabel={(size) => t(`preferences.fontSize.${size}`)}
          />

          <PreferenceOptionGroup
            label={t('preferences.pageDensity.label')}
            value={preferences.pageDensity}
            options={densities}
            onChange={setPageDensity}
            formatLabel={(density) => t(`preferences.pageDensity.${density}`)}
          />

          <PreferenceOptionGroup
            label={t('preferences.primaryColor.label')}
            value={preferences.primaryColor}
            options={primaryColors}
            onChange={setPrimaryColor}
            formatLabel={(color) => t(`preferences.primaryColor.${color}`)}
          />

          <div className="space-y-2">
            <p className="text-sm font-medium text-admin">{t('preferences.sidebar.label')}</p>
            <label className="flex cursor-pointer items-center justify-between rounded-lg border border-admin bg-admin-surface-muted px-3 py-2.5">
              <span className="text-sm text-admin-muted">{t('preferences.sidebar.collapsed')}</span>
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-admin"
                checked={preferences.sidebarCollapsed}
                onChange={(event) => setSidebarCollapsed(event.target.checked)}
              />
            </label>
          </div>

          <div className="space-y-2">
            <p className="text-sm font-medium text-admin">{t('language.label')}</p>
            <select
              className="admin-field-chrome w-full rounded-lg border border-admin bg-admin-surface px-3 py-2 text-sm text-admin outline-none"
              value={preferences.locale}
              onChange={(event) => setLocale(event.target.value as SupportedLocale)}
            >
              {SUPPORTED_LOCALES.map((locale) => (
                <option key={locale} value={locale}>
                  {t(`language.${locale}`)}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="border-t border-admin px-5 py-4">
          <button
            type="button"
            onClick={handleClearLocal}
            className={cn(adminDangerGhostButtonMdClass, 'w-full')}
          >
            {t('preferences.clearLocal')}
          </button>
          <p className="mt-2 text-xs text-admin-muted">{t('preferences.clearHint')}</p>
        </div>
      </aside>
    </>
  )
}

function PreferenceOptionGroup<T extends string>({
  label,
  value,
  options,
  onChange,
  formatLabel,
}: {
  label: string
  value: T
  options: readonly T[]
  onChange: (value: T) => void
  formatLabel: (value: T) => string
}) {
  return (
    <div className="space-y-2">
      <p className="text-sm font-medium text-admin">{label}</p>
      <div className="flex flex-wrap gap-2">
        {options.map((option) => (
          <button
            key={option}
            type="button"
            onClick={() => onChange(option)}
            className={cn(
              'rounded-lg border px-3 py-1.5 text-sm font-medium transition admin-control',
              value === option
                ? 'border-transparent bg-admin-primary text-admin-on-primary'
                : 'border-admin bg-admin-surface-muted text-admin-muted hover:text-admin',
            )}
          >
            {formatLabel(option)}
          </button>
        ))}
      </div>
    </div>
  )
}
