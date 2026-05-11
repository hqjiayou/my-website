import { MOCK_SITES, MOCK_SITE_RUNTIME_STATUS } from '@/mocks/db/sites'
import type { CreateSiteInput, Site, SiteRuntimeStatus } from '@/mocks/types/site'
import { createMockId } from '@/mocks/utils/id'
import { mockDelay } from '@/mocks/utils/delay'

export type UpdateSiteInput = Partial<Omit<Site, 'id' | 'createdAt'>>

const sitesStore: Site[] = [...MOCK_SITES]
const siteRuntimeStatusStore: SiteRuntimeStatus[] = [...MOCK_SITE_RUNTIME_STATUS]

export const siteMockService = {
  async listSites() {
    await mockDelay()
    return [...sitesStore]
  },
  async getSite(siteId: string) {
    await mockDelay()
    return sitesStore.find((site) => site.id === siteId) ?? null
  },
  async getSiteRuntimeStatus(siteId: string) {
    await mockDelay()
    return siteRuntimeStatusStore.find((item) => item.siteId === siteId) ?? null
  },
  async createSite(input: CreateSiteInput) {
    await mockDelay()
    const now = new Date().toISOString()
    const created: Site = {
      id: createMockId('site'),
      status: 'draft',
      ...input,
      createdAt: now,
      updatedAt: now,
    }
    sitesStore.unshift(created)
    siteRuntimeStatusStore.unshift({
      siteId: created.id,
      visitCountToday: 0,
      reservationCountToday: 0,
      aiConversationCountToday: 0,
      publishChecklist: {
        basicInfo: false,
        theme: false,
        domain: false,
        homeContent: false,
        aiConfig: false,
      },
    })
    return created
  },
  async updateSite(siteId: string, input: UpdateSiteInput) {
    await mockDelay()
    const target = sitesStore.find((site) => site.id === siteId)
    if (!target) return null
    Object.assign(target, input, { updatedAt: new Date().toISOString() })
    return { ...target }
  },
}
