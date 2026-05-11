export const LOCALES = ['zh-CN', 'ja-JP', 'en-US'] as const

export type Locale = (typeof LOCALES)[number]

export const LOCALE_OPTIONS: Array<{ value: Locale; label: string }> = [
  { value: 'zh-CN', label: '中文' },
  { value: 'ja-JP', label: '日本語' },
  { value: 'en-US', label: 'English' },
]

export function resolveInitialLocale(): Locale {
  if (typeof navigator === 'undefined') {
    return 'zh-CN'
  }

  const language = navigator.language.toLowerCase()
  if (language.startsWith('ja')) {
    return 'ja-JP'
  }
  if (language.startsWith('en')) {
    return 'en-US'
  }
  return 'zh-CN'
}
