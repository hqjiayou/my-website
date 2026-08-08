import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import { loadAdminPreferences } from '@/config/preferences'

import {
  DEFAULT_LOCALE,
  isSupportedLocale,
  LOCALE_STORAGE_KEY,
  SUPPORTED_LOCALES,
  type SupportedLocale,
} from './constants'
import { I18N_NAMESPACES, resources } from './resources'

export { DEFAULT_LOCALE, LOCALE_STORAGE_KEY, SUPPORTED_LOCALES, type SupportedLocale, isSupportedLocale }

export function readStoredLocale(): SupportedLocale | undefined {
  if (typeof localStorage === 'undefined') return undefined
  const raw = localStorage.getItem(LOCALE_STORAGE_KEY)
  return isSupportedLocale(raw) ? raw : undefined
}

export function resolveInitialLocale(): SupportedLocale {
  const fromPreferences = loadAdminPreferences().locale
  if (fromPreferences) return fromPreferences

  const stored = readStoredLocale()
  if (stored) return stored

  if (typeof navigator === 'undefined') {
    return DEFAULT_LOCALE
  }

  const language = navigator.language.toLowerCase()
  if (language.startsWith('zh')) {
    return 'zh-CN'
  }
  if (language.startsWith('en')) {
    return 'en-US'
  }
  if (language.startsWith('ja')) {
    return 'ja-JP'
  }
  return DEFAULT_LOCALE
}

export function persistLocale(locale: SupportedLocale) {
  if (typeof localStorage === 'undefined') return
  localStorage.setItem(LOCALE_STORAGE_KEY, locale)
}

void i18n.use(initReactI18next).init({
  resources,
  lng: resolveInitialLocale(),
  fallbackLng: DEFAULT_LOCALE,
  supportedLngs: [...SUPPORTED_LOCALES],
  ns: [...I18N_NAMESPACES],
  defaultNS: 'common',
  interpolation: { escapeValue: false },
})

export default i18n
