/** Appearance schema v1 真实契约；生产 API 不得依赖 mocks/types。 */

export type SiteFontStyle = 'modern' | 'classic' | 'soft' | 'japanese'
export type ButtonStyle = 'rounded' | 'pill' | 'square'
export type HeroLayout = 'left-text-right-visual' | 'centered' | 'full-bleed'

export const APPEARANCE_SCHEMA_VERSION_V1 = 1 as const

/** 与后端 `^#[0-9A-Fa-f]{6}$` 对齐。 */
export const PRIMARY_COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/

export const FONT_STYLE_VALUES: SiteFontStyle[] = ['modern', 'classic', 'soft', 'japanese']
export const BUTTON_STYLE_VALUES: ButtonStyle[] = ['rounded', 'pill', 'square']
export const HERO_LAYOUT_VALUES: HeroLayout[] = [
  'left-text-right-visual',
  'centered',
  'full-bleed',
]

/** 对齐 AdminAppearanceConfigV1Dto（闭集四字段）。 */
export interface AppearanceConfigV1 {
  primaryColor: string
  fontStyle: SiteFontStyle
  buttonStyle: ButtonStyle
  heroLayout: HeroLayout
}

/** 对齐 AdminAppearanceDetailDto。 */
export interface AppearanceDetail {
  schemaVersion: number
  config: AppearanceConfigV1
  version: number
  updatedAt: string
}

/** 对齐 AdminAppearancePutRequest（整份替换）。 */
export interface AppearancePutRequest {
  schemaVersion: typeof APPEARANCE_SCHEMA_VERSION_V1
  config: AppearanceConfigV1
  version: number
}

export function createDefaultAppearanceConfig(
  primaryColor = '#2563EB',
): AppearanceConfigV1 {
  return {
    primaryColor,
    fontStyle: 'modern',
    buttonStyle: 'rounded',
    heroLayout: 'left-text-right-visual',
  }
}
