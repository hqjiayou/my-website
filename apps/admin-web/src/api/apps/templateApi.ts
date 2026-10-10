/**
 * 模板目录 API。
 * Template（list/get/by-code/status）→ 始终 REAL `/admin/templates`。
 */
import type { PageResult } from '@/types/site'
import type {
  TemplateDetail,
  TemplateListItem,
  TemplateListQuery,
  UpdateTemplateStatusInput,
} from '@/types/template'
import type { HttpMessageBody } from '@/types/rbac'
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

export const templateApi = {
  listTemplates(query: TemplateListQuery = {}): Promise<PageResult<TemplateListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      status: query.status,
      siteType: query.siteType,
    })
    return httpClient
      .get<HttpMessageBody<PageResult<TemplateListItem>>>(`/admin/templates${qs}`)
      .then(unwrapData)
  },

  getTemplate(id: number): Promise<TemplateDetail> {
    return httpClient.get<HttpMessageBody<TemplateDetail>>(`/admin/templates/${id}`).then(unwrapData)
  },

  getTemplateByCode(code: string): Promise<TemplateDetail> {
    return httpClient
      .get<HttpMessageBody<TemplateDetail>>(`/admin/templates/by-code/${encodeURIComponent(code)}`)
      .then(unwrapData)
  },

  updateTemplateStatus(id: number, input: UpdateTemplateStatusInput): Promise<TemplateDetail> {
    return httpClient
      .patch<HttpMessageBody<TemplateDetail>, UpdateTemplateStatusInput>(
        `/admin/templates/${id}/status`,
        input,
      )
      .then(unwrapData)
  },
}

export type {
  TemplateDetail,
  TemplateListItem,
  TemplateListQuery,
  TemplateStatus,
  UpdateTemplateStatusInput,
} from '@/types/template'

export { TEMPLATE_STATUS, TEMPLATE_STATUS_VALUES } from '@/types/template'
