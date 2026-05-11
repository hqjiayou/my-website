import { Menu, Search } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useLocation } from 'react-router-dom'

import { SUPPORTED_LOCALES, DEFAULT_LOCALE, persistLocale, type SupportedLocale } from '@/i18n'
import { resolveNavTitleKey } from '@/navigation/menu'
import { useLayoutStore } from '@/stores/layout'

export function AdminHeader() {
  const location = useLocation()
  const { t: tNav } = useTranslation('nav')
  const { t: tLayout } = useTranslation('layout')
  const { i18n } = useTranslation()
  const { toggleSidebar } = useLayoutStore()

  const resolved = (i18n.resolvedLanguage ?? i18n.language) as SupportedLocale
  const selectLocale = SUPPORTED_LOCALES.includes(resolved) ? resolved : DEFAULT_LOCALE

  const titleKey = resolveNavTitleKey(location.pathname)
  const pageTitle = titleKey ? tNav(titleKey) : tLayout('fallbackPageTitle')

  function handleLocaleChange(locale: SupportedLocale) {
    void i18n.changeLanguage(locale)
    persistLocale(locale)
  }

  return (
    <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/90 backdrop-blur">
      <div className="flex h-16 items-center gap-3 px-4 sm:px-6">
        <button
          type="button"
          onClick={toggleSidebar}
          className="inline-flex h-9 w-9 items-center justify-center rounded-md border border-slate-200 text-slate-600 transition hover:bg-slate-50 lg:hidden"
        >
          <Menu className="h-4 w-4" />
        </button>

        <div className="mr-auto min-w-0">
          <p className="truncate text-sm font-semibold text-slate-900">{pageTitle}</p>
          <p className="truncate text-xs text-slate-500">{tLayout('header.subtitle')}</p>
        </div>

        <label className="hidden items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm text-slate-500 transition focus-within:border-slate-300 focus-within:ring-2 focus-within:ring-slate-200 md:flex">
          <Search className="h-4 w-4 shrink-0" />
          <input
            className="w-40 border-none bg-transparent text-sm text-slate-700 outline-none placeholder:text-slate-400"
            placeholder={tLayout('search.placeholder')}
          />
        </label>

        <label className="flex items-center gap-2 text-sm text-slate-600">
          <span className="hidden sm:inline">{tLayout('language.label')}</span>
          <select
            className="min-h-9 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-sm text-slate-800 shadow-sm outline-none transition hover:border-slate-300 focus-visible:ring-2 focus-visible:ring-slate-200"
            value={selectLocale}
            onChange={(event) => handleLocaleChange(event.target.value as SupportedLocale)}
          >
            {SUPPORTED_LOCALES.map((locale) => (
              <option key={locale} value={locale}>
                {tLayout(`language.${locale}`)}
              </option>
            ))}
          </select>
        </label>
      </div>
    </header>
  )
}
