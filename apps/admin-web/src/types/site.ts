/** Site Core（Phase 2）真实契约类型；生产 API 不得依赖 mocks/types。 */

import type { AppearanceConfigV1 } from '@/types/appearance'

/** 站点用途标签：自由字符串（预设见 SITE_TYPE_PRESETS，亦可自订），不再是行业闭集。 */
export type AgentType = string

/**
 * agent 类型维度（Issue #213 UoW-A，后端 `agent_kind`）：封闭二值，与上面的
 * 用途标签 `agentType` 互不相关——agentType 是创建时填的自由文本标签。
 */
export type AgentKind = 'SITE' | 'AI_ONLY'

export const AGENT_KIND_VALUES: AgentKind[] = ['SITE', 'AI_ONLY']

export type AgentStatus = 'ACTIVE' | 'DISABLED'

export const SITE_TYPE_PRESETS: string[] = ['PERSONAL', 'PORTFOLIO', 'BLOG', 'KNOWLEDGE', 'TOOL', 'OTHER']

/** @deprecated 旧名兼容：现语义为预设用途标签。 */
export const SITE_TYPE_VALUES: string[] = SITE_TYPE_PRESETS

/** 与后端 SITE_TAG_PATTERN 对齐：1–32 字符，字母/数字开头。 */
export const SITE_TAG_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_\- ]{0,31}$/

export const SITE_STATUS_VALUES: AgentStatus[] = ['ACTIVE', 'DISABLED']

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

export interface AgentListQuery {
  pageNum?: number
  pageSize?: number
  status?: string
  agentType?: string
}

export interface AgentListItem {
  id: number
  agentCode: string
  agentName: string
  agentType: string
  status: string
  defaultDomain: string | null
  version: number
  createdAt: string
  updatedAt: string
  /** 类型维度（#213）：SITE＝站点型 / AI_ONLY＝纯 AI 型。 */
  agentKind: AgentKind
  /** 订阅知识空间数（订阅表行数，含创建者行，#209 口径）。 */
  subscriptionCount: number
  /** 自有对话链两模式的成员数（#214 UoW-3 链内置化；0＝该模式未配链）。 */
  chainKeyCount: number
  chainCliCount: number
  /** Prompt 来源模板留痕（#214 UoW-4）；name 解析不到（模板已删）时为 null、id 照实。 */
  promptTemplateId: number | null
  promptTemplateName: string | null
  /** 是否已有 Prompt 本地副本（留痕/prompt/温度任一非空，与后端解析口径一致）。 */
  promptConfigured: boolean
  /** 自有默认 skill 集成员数（#214 UoW-4）。 */
  defaultSkillCount: number
}

export interface AgentDetail {
  id: number
  agentCode: string
  agentName: string
  agentType: string
  agentKind: AgentKind
  status: string
  defaultDomain: string | null
  description: string | null
  /** Prompt 来源模板留痕 id（#214 UoW-4）；本地副本本体走 ai/prompt 配置端点。 */
  promptTemplateId: number | null
  version: number
  createdAt: string
  updatedAt: string
}

/** Workspace / 详情页统一使用的 Site Core 详情别名。 */
export type Site = AgentDetail

/** Create 加法：`appearance` 可选；向导给出时同事务写入 site + site_appearance。 */
export interface CreateAgentInput {
  agentCode: string
  agentName: string
  agentType: string
  defaultDomain?: string
  description?: string
  appearance?: AppearanceConfigV1
  /** 类型维度（#213）：缺省由服务端落 SITE。 */
  agentKind?: AgentKind
}

export interface UpdateAgentInput {
  agentName: string
  agentType?: string
  defaultDomain?: string
  description?: string
  version: number
  /** 类型维度（#213）：缺省时服务端保留原值。 */
  agentKind?: AgentKind
}

export interface UpdateAgentStatusInput {
  status: string
  version: number
}

/** 类型（kind）专属改写（#213）：乐观锁口径同 status 端点。 */
export interface UpdateAgentKindInput {
  kind: AgentKind
  version: number
}

/**
 * runtime-status 由现有 REAL API 推导；独立端点尚未提供。
 * 放在 types 仅供类型共享。
 */
export interface AgentPublishChecklist {
  basicInfo: boolean
  theme: boolean
  domain: boolean
  homeContent: boolean
  aiConfig: boolean
}

export interface AgentRuntimeStatus {
  agentId: number
  /** 暂无 analytics 后端时为 null，UI 应展示「未接入」而非 0。 */
  visitCountToday: number | null
  /** 暂无 AI 统计后端时为 null。 */
  aiConversationCountToday: number | null
  publishChecklist: AgentPublishChecklist
}
