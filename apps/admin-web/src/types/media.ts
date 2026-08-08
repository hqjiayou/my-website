/** Admin Media（Phase 6）真实契约类型；对齐 AdminMediaDtos。 */

import type { PageResult } from '@/types/site'

export type MediaStatus = 'UPLOADING' | 'READY' | 'FAILED'

export type MediaVisibility = 'PRIVATE'

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
}

export interface MediaListItem {
  id: number
  siteId: number
  originalFilename: string
  contentType: string
  extension: string
  fileSize: number
  status: string
  createdAt: string
  updatedAt: string
}

export interface MediaDetail {
  id: number
  siteId: number
  originalFilename: string
  contentType: string
  extension: string
  fileSize: number
  sha256: string | null
  visibility: string
  status: string
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
