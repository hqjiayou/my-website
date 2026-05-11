import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import { I18N_NAMESPACES, resources } from './resources'

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US', 'ja-JP'] as const

export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]

/** 无存储、且浏览器语言无法匹配时的默认语言 */
export const DEFAULT_LOCALE: SupportedLocale = 'ja-JP'

export const LOCALE_STORAGE_KEY = 'admin-web.locale'

function isSupportedLocale(value: string | null | undefined): value is SupportedLocale {
  return value !== undefined && value !== null && (SUPPORTED_LOCALES as readonly string[]).includes(value)
}

export function readStoredLocale(): SupportedLocale | undefined {
  if (typeof localStorage === 'undefined') return undefined
  const raw = localStorage.getItem(LOCALE_STORAGE_KEY)
  return isSupportedLocale(raw) ? raw : undefined
}

export function resolveInitialLocale(): SupportedLocale {
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
