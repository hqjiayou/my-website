/**
 * 站点媒体 API。
 * Media（list/upload/get/preview/delete）→ 始终 REAL `/admin/agents/{agentId}/media`。
 */
import type {
  MediaDetail,
  MediaListItem,
  MediaListQuery,
  MediaPreview,
  MediaScope,
} from '@/types/media'
import type { PageResult } from '@/types/site'
import type { HttpMessageBody } from '@/types/rbac'
import { ApiError } from '@/utils/http/apiError'
import { httpClient } from '@/utils/http/axios'
import { unwrapData } from '@/utils/http/unwrap'

const UPLOAD_TIMEOUT_MS = 60_000

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

function mediaBase(agentId: number) {
  return `/admin/agents/${agentId}/media`
}

export const mediaApi = {
  listMedia(agentId: number, query: MediaListQuery = {}): Promise<PageResult<MediaListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      contentTypePrefix: query.contentTypePrefix,
      view: query.view,
    })
    return httpClient
      .get<HttpMessageBody<PageResult<MediaListItem>>>(`${mediaBase(agentId)}${qs}`)
      .then(unwrapData)
  },

  getMedia(agentId: number, id: number): Promise<MediaDetail> {
    return httpClient
      .get<HttpMessageBody<MediaDetail>>(`${mediaBase(agentId)}/${id}`)
      .then(unwrapData)
  },

  uploadMedia(agentId: number, file: File): Promise<MediaDetail> {
    const form = new FormData()
    form.append('file', file)
    return httpClient
      .post<HttpMessageBody<MediaDetail>, FormData>(mediaBase(agentId), form, {
        timeout: UPLOAD_TIMEOUT_MS,
      })
      .then(unwrapData)
  },

  previewMedia(agentId: number, id: number): Promise<MediaPreview> {
    return httpClient
      .get<HttpMessageBody<MediaPreview>>(`${mediaBase(agentId)}/${id}/preview`)
      .then(unwrapData)
  },

  deleteMedia(agentId: number, id: number): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null>>(`${mediaBase(agentId)}/${id}`)
      .then(unwrapVoid)
  },

  /** 引用平台媒体到本站（不复制对象；幂等）。 */
  referenceMedia(agentId: number, id: number): Promise<void> {
    return httpClient
      .post<HttpMessageBody<null>, Record<string, never>>(`${mediaBase(agentId)}/${id}/reference`, {})
      .then(unwrapVoid)
  },

  /** 取消本站对该媒体的引用。 */
  unreferenceMedia(agentId: number, id: number): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null>>(`${mediaBase(agentId)}/${id}/reference`)
      .then(unwrapVoid)
  },

  /** 设置可见范围（仅来源站）：AGENT / PLATFORM。 */
  setMediaScope(agentId: number, id: number, scope: MediaScope): Promise<MediaDetail> {
    return httpClient
      .put<HttpMessageBody<MediaDetail>, { scope: MediaScope }>(`${mediaBase(agentId)}/${id}/scope`, {
        scope,
      })
      .then(unwrapData)
  },
}

export type {
  MediaDetail,
  MediaListItem,
  MediaListQuery,
  MediaListView,
  MediaPreview,
  MediaScope,
  MediaStatus,
} from '@/types/media'

export {
  MEDIA_ACCEPT,
  MEDIA_ALLOWED_CONTENT_TYPES,
  MEDIA_SCOPE,
  MEDIA_STATUS,
  MEDIA_STATUS_VALUES,
} from '@/types/media'
