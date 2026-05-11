import { analyticsMockService } from '@/mocks/services/analytics.mock'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

function notImplemented(name: string): never {
  throw new Error(`${name} is not implemented yet`)
}

export const analyticsApi = USE_MOCK
  ? analyticsMockService
  : {
      listAnalyticsSummaries: async () => notImplemented('site-service listAnalyticsSummaries'),
      getAnalyticsSummary: async (_siteId: string) => notImplemented('site-service getAnalyticsSummary'),
    }
