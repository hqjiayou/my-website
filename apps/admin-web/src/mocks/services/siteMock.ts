/**
 * 仅保留 runtime-status mock。
 * Site Core CRUD 已 FORCE REAL，禁止再通过本服务提供假站点列表。
 */
import { MOCK_SITE_RUNTIME_STATUS } from '@/mocks/db/sites'
import type { SiteRuntimeStatus } from '@/types/site'
import { mockDelay } from '@/mocks/utils/delay'

const siteRuntimeStatusStore: SiteRuntimeStatus[] = [...MOCK_SITE_RUNTIME_STATUS]

export const siteMockService = {
  async getSiteRuntimeStatus(id: number): Promise<SiteRuntimeStatus | null> {
    await mockDelay()
    return siteRuntimeStatusStore.find((item) => item.siteId === id) ?? null
  },
}
