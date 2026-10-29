/** 对齐 Admin AI Prompt / KB / Conversation / Preview DTO（FORCE REAL）。 */

export interface AiPromptProfile {
  id: number | null
  agentId: number
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

/** proxy-services 进程状态。不含管理密钥与客户端 key。 */
export interface ProxyServicesStatus {
  name: string
  baseUrl: string
  reachable: boolean
  managementConfigured: boolean
  accounts: ProxyServicesAccount[]
}

export interface ProxyServicesAccount {
  name: string
  provider: string
  disabled: boolean
  unavailable: boolean
  /** 上游账号邮箱。没有时为 null。 */
  email: string | null
  /** 这次进程启动后的成功次数。 */
  success: number
  /** 这次进程启动后的失败次数。 */
  failed: number
  /** 上游观察到的额度已用百分比。没有观察时为 null。 */
  usedPercent: number | null
  /** 模型名 → 已用百分比。没有观察的模型不出现。 */
  modelUsedPercent: Record<string, number>
}

/** CLIProxyAPI access.api-keys。调用代理的客户端 key，不是上游模型密钥。 */
export interface ProxyServicesApiKeys {
  apiKeys: string[]
}

export interface ProxyServicesApiKeyCreated {
  apiKey: string
}

export type ProxyLoginProvider = 'codex' | 'claude' | 'antigravity' | 'meta'

export interface ProxyServicesLogin {
  provider: ProxyLoginProvider
  url: string
  state: string
}

export interface ProxyServicesLoginStatus {
  status: 'wait' | 'ok' | 'error'
  error: string
}

export interface ProxyServicesUsage {
  apiBaseUrl: string
  callable: boolean
  accounts: ProxyServicesPublishedAccount[]
}

export interface ProxyServicesPublishedAccount {
  name: string
  provider: string
  disabled: boolean
  unavailable: boolean
  modelIds: string[]
}

/** 平台模型入口。接口不返回 apiKey 正文。 */
export interface AiModelEntry {
  id: number
  displayName: string
  modelSpec: string
  apiKeyConfigured: boolean
  apiKeyHint: string
  baseUrl: string | null
  agentId: number | null
  embeddingSpec: string | null
  enabled: boolean
  defaultEntry: boolean
  version: number
  updatedAt: string | null
  /** 双模式归属：KEY 直连 / CLI 代理池。旧数据后端缺省 KEY。 */
  mode: string
  /** 网关路由别名；null 表示未设置。服务端校验 `^[a-z0-9][a-z0-9_-]{1,63}$`（会转小写）。 */
  alias: string | null
}

export interface AiModelEntryWriteInput {
  displayName: string
  modelSpec: string
  apiKey?: string
  baseUrl?: string | null
  agentId?: number | null
  embeddingSpec?: string | null
  enabled: boolean
  defaultEntry: boolean
  version?: number | null
  mode?: string
  alias?: string | null
}

/** 网关访问 Key。列表/详情不含明文与哈希。 */
export interface GatewayAccessKey {
  id: number
  name: string
  keyHint: string
  expiresAt: string | null
  revokedAt: string | null
  createdAt: string
  version: number
}

export interface GatewayAccessKeyIssueInput {
  name: string
  expiresAt?: string | null
}

/** 明文 apiKey 仅在签发响应中出现一次，不落入任何持久化状态。 */
export interface GatewayAccessKeyIssueResponse extends GatewayAccessKey {
  apiKey: string
}

export const AI_KB_STATUS = {
  ACTIVE: 'ACTIVE',
  DISABLED: 'DISABLED',
} as const

export type AiKnowledgeStatus = (typeof AI_KB_STATUS)[keyof typeof AI_KB_STATUS]

export const AI_KB_STATUS_VALUES = Object.values(AI_KB_STATUS)

export interface AiKnowledgeListItem {
  id: number
  agentId: number
  baseId: number | null
  owned: boolean
  title: string
  status: AiKnowledgeStatus
  indexStatus: string
  indexError: string | null
  indexedAt: string | null
  sourceFormat: string | null
  originalFilename: string | null
  fileSizeBytes: number | null
  hasOriginal: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface AiKnowledgeAgentStat {
  agentId: number
  agentName: string | null
  totalDocuments: number
  indexedDocuments: number
  failedDocuments: number
  originalBytes: number
}

export interface AiKnowledgeFailure {
  documentId: number
  agentId: number
  agentName: string | null
  title: string
  indexError: string | null
  updatedAt: string | null
}

export interface AiKnowledgeOverview {
  totalDocuments: number
  indexedDocuments: number
  failedDocuments: number
  pendingDocuments: number
  originalBytes: number
  sites: AiKnowledgeAgentStat[]
  recentFailures: AiKnowledgeFailure[]
}

export interface AiKnowledgeDetail extends AiKnowledgeListItem {
  content: string
}

export interface AiKnowledgeArchiveFailure {
  fileName: string
  reason: string
}

export interface AiKnowledgeArchiveResult {
  createdCount: number
  documents: AiKnowledgeDetail[]
  failures: AiKnowledgeArchiveFailure[]
}

export interface AiKnowledgeListQuery {
  pageNum?: number
  pageSize?: number
  status?: AiKnowledgeStatus | ''
  baseId?: number
}

export interface CreateAiKnowledgeInput {
  title: string
  content: string
  baseId?: number | null
}

export interface AiKnowledgeBase {
  id: number
  agentId: number
  ownerAgentName: string | null
  name: string
  isDefault: boolean
  owned: boolean
  documentCount: number
  linkedAgentIds: number[]
  createdAt: string | null
  updatedAt: string | null
}

export interface UpdateAiKnowledgeInput {
  title: string
  content: string
  status: AiKnowledgeStatus
  version: number
}

export interface AiKnowledgeReembedFailure {
  agentId: number
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
  agentId: number
  dimensions: number
}

export interface AiConversationListItem {
  id: number
  agentId: number
  agentUserId: number
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
  /** 本轮请求的模型 spec。旧消息没有。 */
  model?: string | null
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

/** 三个平台 harness 文件是否在。旧版 agent-services 未回报时可能缺省。 */
export interface AiRuntimeHarness {
  background: boolean
  guardrails: boolean
  toolGuidance: boolean
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
  harness?: AiRuntimeHarness | null
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
  /** 含空数组：明确不注入 skill。缺省字段才会走服务端默认 skill。 */
  skills?: string[]
  todos?: AiPreviewTodoItem[]
  mode?: 'CLI' | 'KEY'
}

/** 知识搜索工具的安全摘要：查询词与命中数（片段正文不进预览事件）。 */
export interface AiSearchSummary {
  query: string
  hitCount: number
}

/** 预览 SSE 中与 Agent 行为相关的事件（文本增量由 assistant-ui 消息承载）。 */
export type AiPreviewAgentEvent =
  | { type: 'start'; skillsApplied: string[]; skillsMissing: string[] }
  | { type: 'thinking'; delta: string }
  | { type: 'toolStart'; toolCallId: string; toolName: string }
  | { type: 'toolResult'; toolCallId: string; toolName: string; ok: boolean; summary?: AiSearchSummary }
  | { type: 'done'; todos: AiPreviewTodoItem[]; usage: AiRunUsage | null; model: string | null }
  /** message：Java 推流前失败时为本地化文案；推流中为 agent-services 原始诊断（仅后台可见） */
  | { type: 'error'; errorCode: string; message?: string }
