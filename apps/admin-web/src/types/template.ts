/** Template 目录（Phase 4）真实契约类型；生产 API 不得依赖 mocks/types。 */

export type TemplateStatus = 'ENABLED' | 'DRAFT' | 'DISABLED'

export const TEMPLATE_STATUS_VALUES: TemplateStatus[] = ['ENABLED', 'DRAFT', 'DISABLED']

export const TEMPLATE_STATUS = {
  ENABLED: 'ENABLED',
  DRAFT: 'DRAFT',
  DISABLED: 'DISABLED',
} as const

export interface TemplateListQuery {
  pageNum?: number
  pageSize?: number
  status?: string
  siteType?: string
}

/** 对齐 AdminTemplateListItemDto。 */
export interface TemplateListItem {
  id: number
  code: string
  name: string
  siteType: string
  status: string
  previewImageUrl: string | null
  defaultPrimaryColor: string | null
  sortOrder: number
  version: number
  createdAt: string
  updatedAt: string
}

/** 对齐 AdminTemplateDetailDto。 */
export interface TemplateDetail {
  id: number
  code: string
  name: string
  siteType: string
  status: string
  description: string | null
  previewImageUrl: string | null
  defaultPrimaryColor: string | null
  sortOrder: number
  version: number
  createdAt: string
  updatedAt: string
}

export interface UpdateTemplateStatusInput {
  status: string
  version: number
}
