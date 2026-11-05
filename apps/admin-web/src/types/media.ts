/** Admin Media（Phase 6）真实契约类型；对齐 AdminMediaDtos。 */

import type { PageResult } from '@/types/site'

export type MediaStatus = 'UPLOADING' | 'READY' | 'FAILED'

export type MediaVisibility = 'PRIVATE'

/** 可见范围（#212 平台化）：AGENT=仅来源站与引用站 / PLATFORM=平台可见。 */
export type MediaScope = 'AGENT' | 'PLATFORM'

export const MEDIA_SCOPE = {
  AGENT: 'AGENT',
  PLATFORM: 'PLATFORM',
} as const

/** 列表视图：library=本站库（默认）/ platform=平台库。 */
export type MediaListView = 'library' | 'platform'

export const MEDIA_STATUS = {
  UPLOADING: 'UPLOADING',
  READY: 'READY',
  FAILED: 'FAILED',
} as const

export const MEDIA_STATUS_VALUES: MediaStatus[] = ['UPLOADING', 'READY', 'FAILED']

/** 与后端 AdminMediaConstants.ALLOWED_CONTENT_TYPES 对齐。 */
export const MEDIA_ALLOWED_CONTENT_TYPES = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'application/pdf',
] as const

export type MediaAllowedContentType = (typeof MEDIA_ALLOWED_CONTENT_TYPES)[number]

export const MEDIA_ACCEPT =
  'image/jpeg,image/png,image/webp,application/pdf,.jpg,.jpeg,.png,.webp,.pdf'

export interface MediaListQuery {
  pageNum?: number
  pageSize?: number
  contentTypePrefix?: string
  view?: MediaListView
}

export interface MediaListItem {
  id: number
  /** 上传来源站 id（留痕，非归属）。 */
  agentId: number
  originalFilename: string
  contentType: string
  extension: string
  fileSize: number
  status: string
  scope: MediaScope
  /** 是否经跨站引用可见（本站库中的引用项 / 平台库中已引用项）。 */
  referenced: boolean
  createdAt: string
  updatedAt: string
}

export interface MediaDetail {
  id: number
  agentId: number
  originalFilename: string
  contentType: string
  extension: string
  fileSize: number
  sha256: string | null
  visibility: string
  status: string
  scope: MediaScope
  /** 被引用站数。 */
  referenceCount: number
  createdAt: string
  updatedAt: string
}

/** 预览短时 URL；字段名对齐 AdminMediaPreviewDto.previewUrl。 */
export interface MediaPreview {
  id: number
  previewUrl: string
  expiresAt: string
}

export type { PageResult }
