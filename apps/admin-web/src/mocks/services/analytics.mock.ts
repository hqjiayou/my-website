import { MOCK_ANALYTICS_SUMMARIES } from '@/mocks/db/analytics'
import { mockDelay } from '@/mocks/utils/delay'

export const analyticsMockService = {
  async listAnalyticsSummaries() {
    await mockDelay()
    return [...MOCK_ANALYTICS_SUMMARIES]
  },
  async getAnalyticsSummary(siteId: string) {
    await mockDelay()
    return MOCK_ANALYTICS_SUMMARIES.find((item) => item.siteId === siteId) ?? null
  },
}
