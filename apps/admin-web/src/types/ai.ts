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

/** 平台模型入口。接口不返回 apiKey 正文。 */
export interface AiModelEntry {
  id: number
  displayName: string
  modelSpec: string
  apiKeyConfigured: boolean
  apiKeyHint: string
  baseUrl: string | null
  siteId: number | null
  embeddingSpec: string | null
  enabled: boolean
  defaultEntry: boolean
  version: number
  updatedAt: string | null
}

export interface AiModelEntryWriteInput {
  displayName: string
  modelSpec: string
  apiKey?: string
  baseUrl?: string | null
  siteId?: number | null
  embeddingSpec?: string | null
  enabled: boolean
  defaultEntry: boolean
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
  indexStatus: string
  indexError: string | null
  indexedAt: string | null
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

export interface AiKnowledgeReembedFailure {
  siteId: number
  sourceId: string
  error: string
}

export interface AiKnowledgeReembedResult {
  sites: number
  documents: number
  dimensions: number | null
  recreatedSchema: boolean
  failed: AiKnowledgeReembedFailure[]
  status: 'SUCCESS' | 'PARTIAL'
}

export interface AiKnowledgeReembedConflict {
  siteId: number
  dimensions: number
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

/** Agent 单轮用量；token 由 Provider 回报，未回报时为 0。 */
export interface AiRunUsage {
  requests: number
  toolCalls: number
  inputTokens: number
  outputTokens: number
}

/** 工具轨迹只有名称与成败，不含参数/返回内容。 */
export interface AiToolCallTrace {
  name: string
  ok: boolean
}

export interface AiRunMeta {
  usage: AiRunUsage
  tools: AiToolCallTrace[]
  skillsApplied: string[]
}

export interface AiMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  createdAt: string
  /** 仅 ASSISTANT；历史消息或无数据时为 null / 缺省。 */
  agentMeta?: AiRunMeta | null
}

export interface AiPreviewHistoryMessage {
  role: 'USER' | 'ASSISTANT'
  content: string
}

export const AI_PREVIEW_TODO_STATUS = {
  PENDING: 'PENDING',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
} as const

export type AiPreviewTodoStatus = (typeof AI_PREVIEW_TODO_STATUS)[keyof typeof AI_PREVIEW_TODO_STATUS]

/** agent Todo 快照条目；预览不落库，由前端持有并在下一轮回传。 */
export interface AiPreviewTodoItem {
  id: string
  title: string
  status: AiPreviewTodoStatus
}

/** agent-services 技能目录条目（只元数据）。 */
export interface AiPreviewSkill {
  id: string
  description: string | null
  version: string | null
}

/** 生效的单轮运行预算；null = 不限（或 agent-services 未回报）。 */
export interface AiRuntimeLimits {
  maxRequests: number | null
  maxToolCalls: number | null
  maxTotalTokens: number | null
  toolTimeoutSeconds: number | null
  historyMaxChars: number | null
}

export interface AiRuntimeStatus {
  provider: string | null
  providerStatus: string | null
  fallbackProviders: string[]
  mcpStatus: string | null
  mcpServers: Array<{ name: string; enabled: boolean }>
  limits: AiRuntimeLimits
  retrievalStatus: string | null
  retrievalBlockedReason: string | null
}

/** reachable=false 时 status 为 null，errorCode 说明原因。 */
export interface AiRuntime {
  reachable: boolean
  errorCode: string | null
  status: AiRuntimeStatus | null
}

/** MCP 深检。reachable=false 表示 agent-services 不可达；probe.ok=false 表示某个 MCP 不通。 */
export interface AiMcpReadyServer {
  name: string
  ok: boolean
  toolCount: number | null
  error: string | null
}

export interface AiMcpReadyProbe {
  ok: boolean
  mcpStatus: string | null
  servers: AiMcpReadyServer[]
  errorCode: string | null
  message: string | null
}

export interface AiMcpReady {
  reachable: boolean
  errorCode: string | null
  probe: AiMcpReadyProbe | null
}

export interface AiPreviewChatInput {
  message: string
  history?: AiPreviewHistoryMessage[]
  skills?: string[]
  todos?: AiPreviewTodoItem[]
}

/** 预览 SSE 中与 Agent 行为相关的事件（文本增量由 assistant-ui 消息承载）。 */
export type AiPreviewAgentEvent =
  | { type: 'start'; skillsApplied: string[]; skillsMissing: string[] }
  | { type: 'toolStart'; toolCallId: string; toolName: string }
  | { type: 'toolResult'; toolCallId: string; toolName: string; ok: boolean }
  | { type: 'done'; todos: AiPreviewTodoItem[]; usage: AiRunUsage | null }
  /** message：Java 推流前失败时为本地化文案；推流中为 agent-services 原始诊断（仅后台可见） */
  | { type: 'error'; errorCode: string; message?: string }
