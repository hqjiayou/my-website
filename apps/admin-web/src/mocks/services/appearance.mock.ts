import { MOCK_APPEARANCES } from '@/mocks/db/appearances'
import type { SiteAppearanceConfig } from '@/mocks/types/appearance'
import { mockDelay } from '@/mocks/utils/delay'

function cloneConfig(config: SiteAppearanceConfig): SiteAppearanceConfig {
  return JSON.parse(JSON.stringify(config)) as SiteAppearanceConfig
}

const appearanceStore = new Map<string, SiteAppearanceConfig>(
  MOCK_APPEARANCES.map((config) => [config.siteId, cloneConfig(config)]),
)

export const appearanceMockService = {
  async getAppearance(siteId: string) {
    await mockDelay()
    const found = appearanceStore.get(siteId)
    return found ? cloneConfig(found) : undefined
  },
  async updateAppearance(siteId: string, input: Partial<SiteAppearanceConfig>) {
    await mockDelay()
    const current = appearanceStore.get(siteId)
    if (!current) return undefined

    const merged: SiteAppearanceConfig = {
      ...current,
      ...input,
      theme: {
        ...current.theme,
        ...(input.theme ?? {}),
      },
      layout: {
        ...current.layout,
        ...(input.layout ?? {}),
      },
      components: {
        ...current.components,
        ...(input.components ?? {}),
      },
      homeSections: input.homeSections ? [...input.homeSections] : [...current.homeSections],
      updatedAt: new Date().toISOString(),
    }

    appearanceStore.set(siteId, merged)
    return cloneConfig(merged)
  },
}
