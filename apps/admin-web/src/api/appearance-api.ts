import { appearanceMockService } from '@/mocks/services/appearance.mock'
import type { SiteAppearanceConfig } from '@/mocks/types/appearance'
import { httpClient } from '@/utils/http/axios'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

const appearanceHttpService: typeof appearanceMockService = {
  getAppearance: (siteId: string) => httpClient.get<SiteAppearanceConfig | undefined>(`/site/appearances/${siteId}`),
  updateAppearance: (siteId: string, input: Partial<SiteAppearanceConfig>) =>
    httpClient.patch<SiteAppearanceConfig | undefined, Partial<SiteAppearanceConfig>>(`/site/appearances/${siteId}`, input),
}

export const appearanceApi = USE_MOCK ? appearanceMockService : appearanceHttpService

export type {
  ButtonStyle,
  CardStyle,
  ContainerWidth,
  CtaStyle,
  FooterStyle,
  HeaderStyle,
  HeroLayout,
  HomeSectionConfig,
  HomeSectionKey,
  ImageStyle,
  SectionSpacing,
  SiteAppearanceConfig,
  SiteColorScheme,
  SiteFontStyle,
  SiteRadiusStyle,
  SiteShadowStyle,
  TitleStyle,
} from '@/mocks/types/appearance'
