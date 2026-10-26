/**
 * 站点管理 API。
 * Site Core（list/get/create/update/delete/status）→ 始终 REAL `/admin/sites`。
 * runtime-status：由现有 REAL API 推导（见 siteRuntimeStatus.ts），非独立端点。
 */
import { deriveSiteRuntimeStatus } from '@/api/apps/siteRuntimeStatus'
import type {
  CreateSiteInput,
  PageResult,
  SiteDetail,
  SiteListItem,
  SiteListQuery,
  SiteRuntimeStatus,
  UpdateSiteInput,
  UpdateSiteStatusInput,
} from '@/types/site'
import { SITE_TYPE_VALUES } from '@/types/site'
import type { HttpMessageBody } from '@/types/rbac'
import { ApiError } from '@/utils/http/apiError'
import { httpClient } from '@/utils/http/axios'
import { unwrapData } from '@/utils/http/unwrap'

function toQueryString(query: Record<string, string | number | undefined>) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === '') continue
    params.set(key, String(value))
  }
  const serialized = params.toString()
  return serialized ? `?${serialized}` : ''
}

function unwrapVoid(body: HttpMessageBody<null | void>) {
  if (body.code != null && body.code !== 0) {
    throw new ApiError(body.message || 'Request failed', { code: body.code })
  }
}

const siteHttpService = {
  listSites(query: SiteListQuery = {}): Promise<PageResult<SiteListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      status: query.status,
      siteType: query.siteType,
    })
    return httpClient.get<HttpMessageBody<PageResult<SiteListItem>>>(`/admin/sites${qs}`).then(unwrapData)
  },

  getSite(id: number): Promise<SiteDetail> {
    return httpClient.get<HttpMessageBody<SiteDetail>>(`/admin/sites/${id}`).then(unwrapData)
  },

  createSite(input: CreateSiteInput): Promise<SiteDetail> {
    return httpClient.post<HttpMessageBody<SiteDetail>, CreateSiteInput>('/admin/sites', input).then(unwrapData)
  },

  updateSite(id: number, input: UpdateSiteInput): Promise<SiteDetail> {
    return httpClient.put<HttpMessageBody<SiteDetail>, UpdateSiteInput>(`/admin/sites/${id}`, input).then(unwrapData)
  },

  deleteSite(id: number): Promise<void> {
    return httpClient.delete<HttpMessageBody<null>>(`/admin/sites/${id}`).then(unwrapVoid)
  },

  updateSiteStatus(id: number, input: UpdateSiteStatusInput): Promise<SiteDetail> {
    return httpClient
      .patch<HttpMessageBody<SiteDetail>, UpdateSiteStatusInput>(`/admin/sites/${id}/status`, input)
      .then(unwrapData)
  },

  getSiteRuntimeStatus(id: number): Promise<SiteRuntimeStatus> {
    return deriveSiteRuntimeStatus(id)
  },
}

/** Site Core 与 runtime-status 推导均走真实 HTTP / REAL 数据聚合。 */
export const siteApi = {
  listSites: siteHttpService.listSites,
  getSite: siteHttpService.getSite,
  createSite: siteHttpService.createSite,
  updateSite: siteHttpService.updateSite,
  deleteSite: siteHttpService.deleteSite,
  updateSiteStatus: siteHttpService.updateSiteStatus,
  getSiteRuntimeStatus: siteHttpService.getSiteRuntimeStatus,
}

export type {
  CreateSiteInput,
  PageResult,
  SiteDetail,
  SiteListItem,
  SiteListQuery,
  SiteRuntimeStatus,
  SiteStatus,
  SiteType,
  UpdateSiteInput,
  UpdateSiteStatusInput,
} from '@/types/site'

export type { Site } from '@/types/site'

export {
  SITE_STATUS,
  SITE_STATUS_VALUES,
  SITE_TYPE_VALUES,
  SITE_TYPE_PRESETS,
  SITE_TYPE_LEGACY,
  SITE_TAG_PATTERN,
  SITE_CODE_PATTERN,
} from '@/types/site'

/** @deprecated 旧别名：现指向预设用途标签。 */
export const SITE_INDUSTRY_VALUES = SITE_TYPE_VALUES
