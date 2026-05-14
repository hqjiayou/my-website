export type SiteColorScheme = 'light' | 'warm' | 'dark-lite'
export type SiteFontStyle = 'modern' | 'classic' | 'soft' | 'japanese'
export type SiteRadiusStyle = 'sharp' | 'soft' | 'rounded'
export type SiteShadowStyle = 'flat' | 'soft' | 'elevated'

export type HeaderStyle = 'solid' | 'transparent' | 'minimal'
export type FooterStyle = 'simple' | 'multi-column'
export type ContainerWidth = 'standard' | 'wide' | 'compact'
export type SectionSpacing = 'compact' | 'normal' | 'spacious'
export type HeroLayout = 'left-text-right-visual' | 'centered' | 'full-bleed'

export type ButtonStyle = 'rounded' | 'pill' | 'square'
export type CardStyle = 'bordered' | 'shadow' | 'flat'
export type ImageStyle = 'rounded' | 'soft' | 'square'
export type TitleStyle = 'plain' | 'with-line' | 'accent-mark'
export type CtaStyle = 'solid' | 'gradient-lite' | 'outlined'

export type HomeSectionKey =
  | 'hero'
  | 'trust'
  | 'services'
  | 'cases'
  | 'process'
  | 'aiConsultation'
  | 'faq'
  | 'reservationCta'
  | 'storeInfo'

export interface HomeSectionConfig {
  key: HomeSectionKey
  enabled: boolean
  order: number
  variant: string
}

export interface SiteAppearanceConfig {
  siteId: string
  theme: {
    colorScheme: SiteColorScheme
    primaryColor: string
    secondaryColor: string
    accentColor: string
    backgroundColor: string
    surfaceColor: string
    textColor: string
    mutedTextColor: string
    fontStyle: SiteFontStyle
    radiusStyle: SiteRadiusStyle
    shadowStyle: SiteShadowStyle
  }
  layout: {
    headerStyle: HeaderStyle
    footerStyle: FooterStyle
    containerWidth: ContainerWidth
    sectionSpacing: SectionSpacing
    heroLayout: HeroLayout
  }
  components: {
    buttonStyle: ButtonStyle
    cardStyle: CardStyle
    imageStyle: ImageStyle
    titleStyle: TitleStyle
    ctaStyle: CtaStyle
  }
  homeSections: HomeSectionConfig[]
  updatedAt: string
}
