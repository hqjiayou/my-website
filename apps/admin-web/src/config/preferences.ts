/**
 * admin-web 本地 UI 偏好：类型、默认值与 localStorage 读写。
 *
 * 未来可迁移至后端用户偏好接口（如 GET/PUT /admin/users/me/preferences），
 * 请保持 AdminPreferences 字段稳定，页面与 layout 仅消费该结构。
 */
import {
  DEFAULT_LOCALE,
  isSupportedLocale,
  LOCALE_STORAGE_KEY,
  type SupportedLocale,
} from '@/i18n/constants'

/** localStorage 主键：聚合主题、字号、密度、主色、侧栏、语言等 UI 偏好 */
export const ADMIN_PREFERENCES_STORAGE_KEY = 'admin-web.preferences'

export type ThemeMode = 'light' | 'dark' | 'system'
export type FontSize = 'small' | 'medium' | 'large'
export type PageDensity = 'comfortable' | 'default' | 'compact'
export type PrimaryColor = 'blue' | 'purple' | 'green' | 'orange'

export interface AdminPreferences {
  themeMode: ThemeMode
  fontSize: FontSize
  pageDensity: PageDensity
  primaryColor: PrimaryColor
  sidebarCollapsed: boolean
  locale: SupportedLocale
}

export const DEFAULT_ADMIN_PREFERENCES: AdminPreferences = {
  themeMode: 'system',
  fontSize: 'medium',
  pageDensity: 'default',
  primaryColor: 'blue',
  sidebarCollapsed: false,
  locale: DEFAULT_LOCALE,
}

const THEME_MODES: ThemeMode[] = ['light', 'dark', 'system']
const FONT_SIZES: FontSize[] = ['small', 'medium', 'large']
const PAGE_DENSITIES: PageDensity[] = ['comfortable', 'default', 'compact']
const PRIMARY_COLORS: PrimaryColor[] = ['blue', 'purple', 'green', 'orange']

function isLocale(value: unknown): value is SupportedLocale {
  return typeof value === 'string' && isSupportedLocale(value)
}

function pickEnum<T extends string>(value: unknown, allowed: readonly T[], fallback: T): T {
  return typeof value === 'string' && (allowed as readonly string[]).includes(value) ? (value as T) : fallback
}

function readLegacyLocale(): SupportedLocale | undefined {
  if (typeof localStorage === 'undefined') return undefined
  const raw = localStorage.getItem(LOCALE_STORAGE_KEY)
  return isLocale(raw) ? raw : undefined
}

export function loadAdminPreferences(): AdminPreferences {
  if (typeof localStorage === 'undefined') {
    return { ...DEFAULT_ADMIN_PREFERENCES }
  }

  const raw = localStorage.getItem(ADMIN_PREFERENCES_STORAGE_KEY)
  if (!raw) {
    const legacyLocale = readLegacyLocale()
    return legacyLocale ? { ...DEFAULT_ADMIN_PREFERENCES, locale: legacyLocale } : { ...DEFAULT_ADMIN_PREFERENCES }
  }

  try {
    const parsed = JSON.parse(raw) as Partial<AdminPreferences>
    return {
      themeMode: pickEnum(parsed.themeMode, THEME_MODES, DEFAULT_ADMIN_PREFERENCES.themeMode),
      fontSize: pickEnum(parsed.fontSize, FONT_SIZES, DEFAULT_ADMIN_PREFERENCES.fontSize),
      pageDensity: pickEnum(parsed.pageDensity, PAGE_DENSITIES, DEFAULT_ADMIN_PREFERENCES.pageDensity),
      primaryColor: pickEnum(parsed.primaryColor, PRIMARY_COLORS, DEFAULT_ADMIN_PREFERENCES.primaryColor),
      sidebarCollapsed:
        typeof parsed.sidebarCollapsed === 'boolean'
          ? parsed.sidebarCollapsed
          : DEFAULT_ADMIN_PREFERENCES.sidebarCollapsed,
      locale: isLocale(parsed.locale) ? parsed.locale : (readLegacyLocale() ?? DEFAULT_ADMIN_PREFERENCES.locale),
    }
  } catch {
    return { ...DEFAULT_ADMIN_PREFERENCES }
  }
}

export function saveAdminPreferences(preferences: AdminPreferences) {
  if (typeof localStorage === 'undefined') return
  localStorage.setItem(ADMIN_PREFERENCES_STORAGE_KEY, JSON.stringify(preferences))
  // 兼容仍直接读取 LOCALE_STORAGE_KEY 的代码路径
  localStorage.setItem(LOCALE_STORAGE_KEY, preferences.locale)
}

/**
 * 解析实际生效的主题：system 跟随 prefers-color-scheme（见 layouts/AdminLayout SystemThemeSync）。
 */
export function resolveEffectiveTheme(themeMode: ThemeMode): 'light' | 'dark' {
  if (themeMode === 'light' || themeMode === 'dark') return themeMode
  if (typeof window === 'undefined') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

/** 将偏好应用到 documentElement（.dark / 主色·密度 → theme.css；字号 → font.css） */
export function applyPreferencesToDocument(preferences: AdminPreferences) {
  if (typeof document === 'undefined') return

  const root = document.documentElement
  const effectiveTheme = resolveEffectiveTheme(preferences.themeMode)

  root.classList.toggle('dark', effectiveTheme === 'dark')
  root.dataset.themeMode = preferences.themeMode
  root.dataset.fontSize = preferences.fontSize
  root.dataset.density = preferences.pageDensity
  root.dataset.primaryColor = preferences.primaryColor
  root.dataset.sidebarCollapsed = preferences.sidebarCollapsed ? 'true' : 'false'
  root.style.colorScheme = effectiveTheme
}

/**
 * 清除 UI 偏好缓存（主题、字号、密度、主色、侧栏、语言）。
 * 不触及 admin_access_token 与认证相关存储。
 */
export function clearAdminPreferencesStorage() {
  if (typeof localStorage === 'undefined') return
  localStorage.removeItem(ADMIN_PREFERENCES_STORAGE_KEY)
  localStorage.removeItem(LOCALE_STORAGE_KEY)
}
