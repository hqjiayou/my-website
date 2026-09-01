/** 对齐 Admin AI Prompt / KB / Conversation / Preview DTO（FORCE REAL）。 */

export interface AiPromptProfile {
  id: number | null
  siteId: number
  systemPrompt: string | null
  temperature: number | null
  version: number | null
  updatedAt: string | null
}

export interface UpdateAiPromptProfileInput {
  systemPrompt?: string | null
  temperature?: number | null
  version?: number | null
}

export const AI_KB_STATUS = {
  ACTIVE: 'ACTIVE',
  DISABLED: 'DISABLED',
} as const

export type AiKnowledgeStatus = (typeof AI_KB_STATUS)[keyof typeof AI_KB_STATUS]

export const AI_KB_STATUS_VALUES = Object.values(AI_KB_STATUS)

export interface AiKnowledgeListItem {
  id: number
  siteId: number
  title: string
  status: AiKnowledgeStatus
  version: number
  createdAt: string
  updatedAt: string
}

export interface AiKnowledgeDetail extends AiKnowledgeListItem {
  content: string
}

export interface AiKnowledgeListQuery {
  pageNum?: number
  pageSize?: number
  status?: AiKnowledgeStatus | ''
}

export interface CreateAiKnowledgeInput {
  title: string
  content: string
}

export interface UpdateAiKnowledgeInput {
  title: string
  content: string
  status: AiKnowledgeStatus
  version: number
}

export interface AiConversationListItem {
  id: number
  siteId: number
  siteUserId: number
  title: string | null
  lastMessageAt: string | null
  createdAt: string
}

export interface AiConversationListQuery {
  pageNum?: number
  pageSize?: number
}

export interface AiMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
}

export interface AiPreviewHistoryMessage {
  role: 'USER' | 'ASSISTANT'
  content: string
}
