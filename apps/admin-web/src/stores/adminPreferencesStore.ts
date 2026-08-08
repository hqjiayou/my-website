import { create } from 'zustand'

import {
  applyPreferencesToDocument,
  clearAdminPreferencesStorage,
  DEFAULT_ADMIN_PREFERENCES,
  loadAdminPreferences,
  saveAdminPreferences,
  type AdminPreferences,
  type FontSize,
  type PageDensity,
  type PrimaryColor,
  type ThemeMode,
} from '@/config/preferences'
import type { SupportedLocale } from '@/i18n/constants'
import i18n from '@/i18n'
import { persistLocale } from '@/i18n'

type PreferencePatch = Partial<AdminPreferences>

interface AdminPreferencesState {
  preferences: AdminPreferences
  setPreferences: (patch: PreferencePatch) => void
  setThemeMode: (themeMode: ThemeMode) => void
  setFontSize: (fontSize: FontSize) => void
  setPageDensity: (pageDensity: PageDensity) => void
  setPrimaryColor: (primaryColor: PrimaryColor) => void
  setSidebarCollapsed: (sidebarCollapsed: boolean) => void
  setLocale: (locale: SupportedLocale) => void
  resetPreferences: () => void
}

function commitPreferences(preferences: AdminPreferences) {
  saveAdminPreferences(preferences)
  applyPreferencesToDocument(preferences)
}

const initialPreferences = loadAdminPreferences()
applyPreferencesToDocument(initialPreferences)

export const useAdminPreferencesStore = create<AdminPreferencesState>((set, get) => ({
  preferences: initialPreferences,

  setPreferences(patch) {
    const next = { ...get().preferences, ...patch }
    commitPreferences(next)
    set({ preferences: next })
  },

  setThemeMode(themeMode) {
    get().setPreferences({ themeMode })
  },

  setFontSize(fontSize) {
    get().setPreferences({ fontSize })
  },

  setPageDensity(pageDensity) {
    get().setPreferences({ pageDensity })
  },

  setPrimaryColor(primaryColor) {
    get().setPreferences({ primaryColor })
  },

  setSidebarCollapsed(sidebarCollapsed) {
    get().setPreferences({ sidebarCollapsed })
  },

  setLocale(locale) {
    const next = { ...get().preferences, locale }
    commitPreferences(next)
    set({ preferences: next })
    void i18n.changeLanguage(locale)
    persistLocale(locale)
  },

  resetPreferences() {
    clearAdminPreferencesStorage()
    const next = { ...DEFAULT_ADMIN_PREFERENCES }
    commitPreferences(next)
    set({ preferences: next })
    void i18n.changeLanguage(next.locale)
    persistLocale(next.locale)
  },
}))
