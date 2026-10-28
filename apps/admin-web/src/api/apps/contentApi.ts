/**
 * 站点内容 API。
 * Content（list/get/create/update/status/delete）→ 始终 REAL `/admin/agents/{agentId}/contents`。
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

function contentBase(agentId: number) {
  return `/admin/agents/${agentId}/contents`
}

export const contentApi = {
  listContents(agentId: number, query: ContentListQuery = {}): Promise<PageResult<ContentListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      type: query.type,
      status: query.status,
    })
    return httpClient
      .get<HttpMessageBody<PageResult<ContentListItem>>>(`${contentBase(agentId)}${qs}`)
      .then(unwrapData)
  },

  getContent(agentId: number, id: number): Promise<ContentDetail> {
    return httpClient
      .get<HttpMessageBody<ContentDetail>>(`${contentBase(agentId)}/${id}`)
      .then(unwrapData)
  },

  createContent(agentId: number, input: CreateContentInput): Promise<ContentDetail> {
    return httpClient
      .post<HttpMessageBody<ContentDetail>, CreateContentInput>(contentBase(agentId), input)
      .then(unwrapData)
  },

  updateContent(agentId: number, id: number, input: UpdateContentInput): Promise<ContentDetail> {
    return httpClient
      .put<HttpMessageBody<ContentDetail>, UpdateContentInput>(`${contentBase(agentId)}/${id}`, input)
      .then(unwrapData)
  },

  updateContentStatus(
    agentId: number,
    id: number,
    input: UpdateContentStatusInput,
  ): Promise<ContentDetail> {
    return httpClient
      .patch<HttpMessageBody<ContentDetail>, UpdateContentStatusInput>(
        `${contentBase(agentId)}/${id}/status`,
        input,
      )
      .then(unwrapData)
  },

  deleteContent(agentId: number, id: number): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null>>(`${contentBase(agentId)}/${id}`)
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
