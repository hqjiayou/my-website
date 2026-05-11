import { siteMockService, type UpdateSiteInput } from '@/mocks/services/site.mock'
import { SITE_INDUSTRY_VALUES } from '@/mocks/types/site'
import type { CreateSiteInput, Site, SiteIndustry, SiteRuntimeStatus, SiteStatus } from '@/mocks/types/site'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

function notImplemented(name: string): never {
  throw new Error(`${name} is not implemented yet`)
}

export const siteApi = USE_MOCK
  ? siteMockService
  : {
      listSites: async () => notImplemented('site-service listSites'),
      getSite: async (_siteId: string) => notImplemented('site-service getSite'),
      getSiteRuntimeStatus: async (_siteId: string) => notImplemented('site-service getSiteRuntimeStatus'),
      createSite: async (_input: CreateSiteInput) => notImplemented('site-service createSite'),
      updateSite: async (_siteId: string, _input: UpdateSiteInput) => notImplemented('site-service updateSite'),
    }

export type { CreateSiteInput, Site, SiteIndustry, SiteRuntimeStatus, SiteStatus, UpdateSiteInput }
export { SITE_INDUSTRY_VALUES }
