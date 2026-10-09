/**
 * 站点媒体 API。
 * Media（list/upload/get/preview/delete）→ 始终 REAL `/admin/sites/{siteId}/media`。
 */
import type {
  MediaDetail,
  MediaListItem,
  MediaListQuery,
  MediaPreview,
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

function mediaBase(siteId: number) {
  return `/admin/sites/${siteId}/media`
}

export const mediaApi = {
  listMedia(siteId: number, query: MediaListQuery = {}): Promise<PageResult<MediaListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      contentTypePrefix: query.contentTypePrefix,
    })
    return httpClient
      .get<HttpMessageBody<PageResult<MediaListItem>>>(`${mediaBase(siteId)}${qs}`)
      .then(unwrapData)
  },

  getMedia(siteId: number, id: number): Promise<MediaDetail> {
    return httpClient
      .get<HttpMessageBody<MediaDetail>>(`${mediaBase(siteId)}/${id}`)
      .then(unwrapData)
  },

  uploadMedia(siteId: number, file: File): Promise<MediaDetail> {
    const form = new FormData()
    form.append('file', file)
    return httpClient
      .post<HttpMessageBody<MediaDetail>, FormData>(mediaBase(siteId), form, {
        timeout: UPLOAD_TIMEOUT_MS,
      })
      .then(unwrapData)
  },

  previewMedia(siteId: number, id: number): Promise<MediaPreview> {
    return httpClient
      .get<HttpMessageBody<MediaPreview>>(`${mediaBase(siteId)}/${id}/preview`)
      .then(unwrapData)
  },

  deleteMedia(siteId: number, id: number): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null>>(`${mediaBase(siteId)}/${id}`)
      .then(unwrapVoid)
  },
}

export type {
  MediaDetail,
  MediaListItem,
  MediaListQuery,
  MediaPreview,
  MediaStatus,
} from '@/types/media'

export {
  MEDIA_ACCEPT,
  MEDIA_ALLOWED_CONTENT_TYPES,
  MEDIA_STATUS,
  MEDIA_STATUS_VALUES,
} from '@/types/media'
