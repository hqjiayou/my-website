/**
 * 功能注解：站点管理相关 API（站点列表、详情、运行状态、创建与更新）。
 */
import { siteMockService, type UpdateSiteInput } from '@/mocks/services/site.mock'
import { SITE_INDUSTRY_VALUES } from '@/mocks/types/site'
import type { CreateSiteInput, Site, SiteIndustry, SiteRuntimeStatus, SiteStatus } from '@/mocks/types/site'
import { httpClient } from '@/utils/http/axios'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

const siteHttpService: typeof siteMockService = {
  listSites: () => httpClient.get<Site[]>('/site/sites'),
  getSite: (siteId: string) => httpClient.get<Site | null>(`/site/sites/${siteId}`),
  getSiteRuntimeStatus: (siteId: string) => httpClient.get<SiteRuntimeStatus | null>(`/site/sites/${siteId}/runtime-status`),
  createSite: (input: CreateSiteInput) => httpClient.post<Site, CreateSiteInput>('/site/sites', input),
  updateSite: (siteId: string, input: UpdateSiteInput) =>
    httpClient.patch<Site | null, UpdateSiteInput>(`/site/sites/${siteId}`, input),
}

export const siteApi = USE_MOCK ? siteMockService : siteHttpService

export type { CreateSiteInput, Site, SiteIndustry, SiteRuntimeStatus, SiteStatus, UpdateSiteInput }
export { SITE_INDUSTRY_VALUES }
