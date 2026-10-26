/** Site Core（Phase 2）真实契约类型；生产 API 不得依赖 mocks/types。 */

import type { AppearanceConfigV1 } from '@/types/appearance'

/** 站点用途标签：自由字符串（预设见 SITE_TYPE_PRESETS，亦可自订），不再是行业闭集。 */
export type SiteType = string

export type SiteStatus = 'ACTIVE' | 'DISABLED'

export const SITE_TYPE_PRESETS: string[] = ['PERSONAL', 'PORTFOLIO', 'BLOG', 'KNOWLEDGE', 'TOOL', 'OTHER']

/** 旧行业类型：仅历史站点与模板目录仍会出现，用于筛选与标签展示。 */
export const SITE_TYPE_LEGACY: string[] = [
  'CAR_REPAIR',
  'USED_CAR',
  'CAKE',
  'SALON',
  'CLINIC',
  'GYOSEISHOSHI',
]

/** @deprecated 旧名兼容：现语义为预设用途标签。 */
export const SITE_TYPE_VALUES: string[] = SITE_TYPE_PRESETS

/** 与后端 SITE_TAG_PATTERN 对齐：1–32 字符，字母/数字开头。 */
export const SITE_TAG_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_\- ]{0,31}$/

export const SITE_STATUS_VALUES: SiteStatus[] = ['ACTIVE', 'DISABLED']

export const SITE_STATUS = {
  ACTIVE: 'ACTIVE',
  DISABLED: 'DISABLED',
} as const

/** 与后端 `^[a-z][a-z0-9_-]{1,63}$` 对齐。 */
export const SITE_CODE_PATTERN = /^[a-z][a-z0-9_-]{1,63}$/

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface SiteListQuery {
  pageNum?: number
  pageSize?: number
  status?: string
  siteType?: string
}

export interface SiteListItem {
  id: number
  siteCode: string
  siteName: string
  siteType: string
  status: string
  defaultDomain: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface SiteDetail {
  id: number
  siteCode: string
  siteName: string
  siteType: string
  status: string
  defaultDomain: string | null
  description: string | null
  /** Phase 5：创建时绑定的模板；无模板创建时为 null。 */
  templateId: number | null
  version: number
  createdAt: string
  updatedAt: string
}

/** Workspace / 详情页统一使用的 Site Core 详情别名。 */
export type Site = SiteDetail

/**
 * Create 加法：`templateId` 与 `appearance` 同有或同无。
 * 向导路径必须两者都带，同事务写入 site + site_appearance。
 */
export interface CreateSiteInput {
  siteCode: string
  siteName: string
  siteType: string
  defaultDomain?: string
  description?: string
  templateId?: number
  appearance?: AppearanceConfigV1
}

export interface UpdateSiteInput {
  siteName: string
  siteType?: string
  defaultDomain?: string
  description?: string
  version: number
}

export interface UpdateSiteStatusInput {
  status: string
  version: number
}

/**
 * runtime-status 由现有 REAL API 推导；独立端点尚未提供。
 * 放在 types 仅供类型共享。
 */
export interface SitePublishChecklist {
  basicInfo: boolean
  theme: boolean
  domain: boolean
  homeContent: boolean
  aiConfig: boolean
}

export interface SiteRuntimeStatus {
  siteId: number
  /** 暂无 analytics 后端时为 null，UI 应展示「未接入」而非 0。 */
  visitCountToday: number | null
  reservationCountToday: number | null
  /** 暂无 AI 统计后端时为 null。 */
  aiConversationCountToday: number | null
  publishChecklist: SitePublishChecklist
}
