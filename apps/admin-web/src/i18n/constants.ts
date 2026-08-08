export const SUPPORTED_LOCALES = ['zh-CN', 'en-US', 'ja-JP'] as const

export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]

export const DEFAULT_LOCALE: SupportedLocale = 'ja-JP'

export const LOCALE_STORAGE_KEY = 'admin-web.locale'

export function isSupportedLocale(value: string | null | undefined): value is SupportedLocale {
  return value !== undefined && value !== null && (SUPPORTED_LOCALES as readonly string[]).includes(value)
}
