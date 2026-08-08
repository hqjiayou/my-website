/** Admin Content（Phase 7）真实契约类型；对齐 AdminContentDtos。 */

import type { PageResult } from '@/types/site'

export type ContentType = 'hero' | 'service' | 'case' | 'faq' | 'news' | 'about' | 'cta'

export type ContentStatus = 'DRAFT' | 'PUBLISHED' | 'HIDDEN'

export const CONTENT_TYPE_VALUES: ContentType[] = [
  'hero',
  'service',
  'case',
  'faq',
  'news',
  'about',
  'cta',
]

export const CONTENT_STATUS = {
  DRAFT: 'DRAFT',
  PUBLISHED: 'PUBLISHED',
  HIDDEN: 'HIDDEN',
} as const

export const CONTENT_STATUS_VALUES: ContentStatus[] = ['DRAFT', 'PUBLISHED', 'HIDDEN']

export interface ContentListQuery {
  pageNum?: number
  pageSize?: number
  type?: string
  status?: string
}

export interface ContentListItem {
  id: number
  siteId: number
  type: string
  title: string
  body: string | null
  status: string
  sortOrder: number
  coverMediaId: number | null
  publishedAt: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface ContentDetail {
  id: number
  siteId: number
  type: string
  title: string
  body: string | null
  status: string
  sortOrder: number
  coverMediaId: number | null
  publishedAt: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface CreateContentInput {
  type: string
  title: string
  body?: string | null
  status: string
  sortOrder?: number | null
  coverMediaId?: number | null
}

/** PUT 正文更新；status 仅走 PATCH .../status，勿放入本 DTO。 */
export interface UpdateContentInput {
  type: string
  title: string
  body?: string | null
  sortOrder?: number | null
  coverMediaId?: number | null
  version: number
}

export interface UpdateContentStatusInput {
  status: string
  version: number
}

export type { PageResult }
