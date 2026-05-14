/**
 * 功能注解：数据分析 API（分析汇总列表与单站点汇总详情）。
 */
import { analyticsMockService } from '@/mocks/services/analytics.mock'
import type { AnalyticsSummary } from '@/mocks/types/analytics'
import { httpClient } from '@/utils/http/axios'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

const analyticsHttpService: typeof analyticsMockService = {
  listAnalyticsSummaries: () => httpClient.get<AnalyticsSummary[]>('/site/analytics/summaries'),
  getAnalyticsSummary: (siteId: string) => httpClient.get<AnalyticsSummary | null>(`/site/analytics/summaries/${siteId}`),
}

export const analyticsApi = USE_MOCK ? analyticsMockService : analyticsHttpService
