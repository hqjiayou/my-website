/**
 * 站点内容 API。
 * Content（list/get/create/update/status/delete）→ 始终 REAL `/admin/sites/{siteId}/contents`。
 */
import type {
  ContentDetail,
  ContentListItem,
  ContentListQuery,
  CreateContentInput,
  PageResult,
  UpdateContentInput,
  UpdateContentStatusInput,
} from '@/types/content'
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

function contentBase(siteId: number) {
  return `/admin/sites/${siteId}/contents`
}

export const contentApi = {
  listContents(siteId: number, query: ContentListQuery = {}): Promise<PageResult<ContentListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      type: query.type,
      status: query.status,
    })
    return httpClient
      .get<HttpMessageBody<PageResult<ContentListItem>>>(`${contentBase(siteId)}${qs}`)
      .then(unwrapData)
  },

  getContent(siteId: number, id: number): Promise<ContentDetail> {
    return httpClient
      .get<HttpMessageBody<ContentDetail>>(`${contentBase(siteId)}/${id}`)
      .then(unwrapData)
  },

  createContent(siteId: number, input: CreateContentInput): Promise<ContentDetail> {
    return httpClient
      .post<HttpMessageBody<ContentDetail>, CreateContentInput>(contentBase(siteId), input)
      .then(unwrapData)
  },

  updateContent(siteId: number, id: number, input: UpdateContentInput): Promise<ContentDetail> {
    return httpClient
      .put<HttpMessageBody<ContentDetail>, UpdateContentInput>(`${contentBase(siteId)}/${id}`, input)
      .then(unwrapData)
  },

  updateContentStatus(
    siteId: number,
    id: number,
    input: UpdateContentStatusInput,
  ): Promise<ContentDetail> {
    return httpClient
      .patch<HttpMessageBody<ContentDetail>, UpdateContentStatusInput>(
        `${contentBase(siteId)}/${id}/status`,
        input,
      )
      .then(unwrapData)
  },

  deleteContent(siteId: number, id: number): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null>>(`${contentBase(siteId)}/${id}`)
      .then(unwrapVoid)
  },
}

export type {
  ContentDetail,
  ContentListItem,
  ContentListQuery,
  ContentStatus,
  ContentType,
  CreateContentInput,
  UpdateContentInput,
  UpdateContentStatusInput,
} from '@/types/content'

export {
  CONTENT_STATUS,
  CONTENT_STATUS_VALUES,
  CONTENT_TYPE_VALUES,
} from '@/types/content'
