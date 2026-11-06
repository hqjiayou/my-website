/** 对齐 Admin AI Prompt / KB / Conversation / Preview DTO（FORCE REAL）。 */

/** proxy-services 进程状态。不含管理密钥与客户端 key。 */
export interface ProxyServicesStatus {
  name: string
  baseUrl: string
  reachable: boolean
  managementConfigured: boolean
  accounts: ProxyServicesAccount[]
  /** 代理进程里正在生效的按账号 pacing。拿不到时为 null。某一项为 0 表示该闸关闭。 */
  accountPacing: ProxyServicesAccountPacing | null
}

export interface ProxyServicesAccountPacing {
  minIntervalMs: number
  minIntervalJitterMs: number
  maxInFlight: number
  quotaUsedPercent: number
  /** 只为间隔闸等待的上限。0 表示不等，撞间隔立刻不合格。 */
  maxWaitMs: number
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
  /** 来源标注：平台化前所属 agent id（Issue #210 UoW-6）；平台原生入口为 null。原 agentId 作用域已退役。 */
  sourceAgentId: number | null
  embeddingSpec: string | null
  enabled: boolean
  defaultEntry: boolean
  version: number
  updatedAt: string | null
  /** 双模式归属：KEY 直连 / CLI 代理池。旧数据后端缺省 KEY。 */
  mode: string
  /** 网关路由别名；null 表示未设置。服务端校验 `^[a-z0-9][a-z0-9_-]{1,63}$`（会转小写）。 */
  alias: string | null
  /** 派生行标记（Issue #214 UoW-2）：derivedSource 非空＝由来源自动派生，管理面只读。 */
  derived: boolean
  /** 派生来源：'PROXY'＝CLI 代理派生；人工登记为 null。 */
  derivedSource: string | null
}

/** CLI 派生条目同步报告（Issue #214 UoW-2）。status: OK / PROXY_UNAVAILABLE / NO_CLIENT_KEY。 */
export interface AiCliEntrySyncReport {
  status: string
  proxyReady: boolean
  created: number
  reactivated: number
  deactivated: number
  unchanged: number
  skippedNoClientKey: number
}

export interface AiModelEntryWriteInput {
  displayName: string
  modelSpec: string
  apiKey?: string
  baseUrl?: string | null
  embeddingSpec?: string | null
  enabled: boolean
  defaultEntry: boolean
  version?: number | null
  mode?: string
  alias?: string | null
}

/**
 * 嵌入类登记行（Issue #126 UoW-1/UoW-3）：登记只存入口引用，embeddingSpec 与
 * className（规范化类键）由服务端读时从被引用入口解析；入口已删时三者为 null，
 * 页面须如实呈现异常态并允许移除。字段与后端 AdminEmbeddingClassDto 逐项对齐。
 */
export interface AiEmbeddingClass {
  id: number
  baseId: number | null
  entryId: number
  entryDisplayName: string | null
  embeddingSpec: string | null
  className: string | null
  enabled: boolean
  sortOrder: number
}

/** 整表保存的单项：数组序即类优先级；enabled 缺省后端视为 true，前端总是显式传。 */
export interface AiEmbeddingClassSaveItem {
  entryId: number
  enabled: boolean
}

export type AiRuntimeConfigPushStatus = 'APPLIED' | 'BLOCKED' | 'FAILED'

/**
 * agent-services 运行配置视图（Issue #146）：DSN 只回脱敏形、Gemini key 只回
 * configured＋末 4 位 hint，任何情况下不含明文。字段与后端 AdminAiRuntimeConfigDto 逐项对齐。
 */
export interface AiRuntimeConfig {
  retrievalDatabaseUrlConfigured: boolean
  retrievalDatabaseUrlMasked: string | null
  aiEmbeddingModel: string | null
  aiEmbeddingDimensions: number | null
  geminiApiKeyConfigured: boolean
  geminiApiKeyHint: string | null
  configVersion: number
  lastPushStatus: AiRuntimeConfigPushStatus | null
  lastPushMessage: string | null
  lastPushedAt: string | null
  version: number | null
  updatedAt: string | null
}

/** 保存运行配置：密钥字段缺省/留空＝保留原值；aiEmbeddingModel 空＝清空、aiEmbeddingDimensions 为 null＝清空。 */
export interface AiRuntimeConfigUpdateInput {
  retrievalDatabaseUrl?: string | null
  aiEmbeddingModel?: string | null
  aiEmbeddingDimensions?: number | null
  geminiApiKey?: string | null
}

/** MCP 条目里密钥型键值对的回显形（Issue #192）：只回 configured＋末 4 位 hint，永不回明文。 */
export interface AiMcpSecretKv {
  key: string
  configured: boolean
  valueHint: string | null
}

/**
 * MCP 服务器条目视图（Issue #192）：url 只回 configured＋hint（可能内嵌凭证，按密钥处理）；
 * args 不是密钥、原样回。字段与后端 McpServerEntryDto 逐项对齐。
 */
export interface AiMcpServerEntry {
  name: string
  transport: string | null
  urlConfigured: boolean
  urlHint: string | null
  enabled: boolean
  toolPrefix: string | null
  initTimeoutSeconds: number | null
  command: string | null
  args: string[]
  cwd: string | null
  headers: AiMcpSecretKv[]
  env: AiMcpSecretKv[]
}

/** MCP 全局注册表视图：条目列表＋与运行配置同口径的版本/推送状态（共用同一行与推送管线）。 */
export interface AiMcpConfig {
  servers: AiMcpServerEntry[]
  configVersion: number
  lastPushStatus: AiRuntimeConfigPushStatus | null
  lastPushMessage: string | null
  lastPushedAt: string | null
  version: number | null
  updatedAt: string | null
}

/** 密钥型键值对的提交形：value 留空＝保留原值；clear=true＝删除该键（优先于 value）。 */
export interface AiMcpSecretKvInput {
  key: string
  value?: string | null
  clear?: boolean
}

/**
 * MCP 条目提交形：非密钥字段整体替换（null＝清掉）；url 留空＝保留原值；
 * headers/env 为 null＝保留既有整表，非 null＝列表即最终键集合。
 */
export interface AiMcpServerEntryInput {
  name: string
  transport: string | null
  url?: string | null
  enabled?: boolean | null
  toolPrefix?: string | null
  initTimeoutSeconds?: number | null
  command?: string | null
  args?: string[] | null
  cwd?: string | null
  headers?: AiMcpSecretKvInput[] | null
  env?: AiMcpSecretKvInput[] | null
}

/** MCP per-agent 选择三态：INHERIT＝用全局启用集、OFF＝明确不用、CUSTOM＝名字子集（与全局启用集取交集）。 */
export type AiMcpSelectionMode = 'INHERIT' | 'OFF' | 'CUSTOM'

export interface AiMcpSelection {
  mode: AiMcpSelectionMode
  serverNames: string[]
}

/**
 * 对话链预设（Issue #210 UoW-7）：平台级命名链快照，entryIds 有序＝链的成员与顺序。
 * agent 引用一条预设即决定其对话链；用途 v1 只有 CHAT。
 */
export interface AiChainPreset {
  id: number
  name: string
  purpose: string
  entryIds: number[]
  version: number
  createdAt: string
  updatedAt: string
}

export interface AiChainPresetWriteInput {
  name: string
  entryIds: number[]
  version?: number
}

/** agent 当前引用的链预设；chainPresetId 为 null＝未引用（对话链为空）。 */
export interface AiAgentChainPreset {
  agentId: number
  chainPresetId: number | null
}

/**
 * AI 配置预设（Issue #211 UoW-8 后端、#213 UoW-C 管理页）：命名预设＝一组
 * systemPrompt 覆盖＋temperature＋默认 skill 集，agent 以 configPresetId 引用一份。
 * 删除为软删：引用它的 agent 解析时回退为无配置（服务端既有口径）。
 */
export interface AiConfigPreset {
  id: number
  name: string
  systemPrompt: string | null
  temperature: number | null
  defaultSkillIds: string[]
  version: number
  updatedAt: string
}

export interface AiConfigPresetCreateInput {
  name: string
  systemPrompt?: string | null
  temperature?: number | null
  defaultSkillIds?: string[]
}

export interface AiConfigPresetUpdateInput {
  name: string
  systemPrompt?: string | null
  temperature?: number | null
  defaultSkillIds?: string[]
  version: number
}

/** 经验条目状态（与后端 agent_experience_lesson.status 状态机一致，Issue #184）。 */
export type AiExperienceLessonStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'DISABLED'

/**
 * 经验审核列表行（Issue #184 UoW-3）：lesson 只带服务端摘要，全文走详情。
 * 来源只回表里实存的 sourceAgentId；提炼信号不落条目表，列表不展示信号类型。
 */
export interface AiExperienceLessonListItem {
  id: number
  title: string
  lessonSummary: string | null
  sourceAgentId: number | null
  status: AiExperienceLessonStatus
  lessonNo: number | null
  createdAt: string
  reviewedAt: string | null
}

/** 经验条目详情：结构化四字段全文＋来源三 id（手动新建时三 id 皆 null）。 */
export interface AiExperienceLesson {
  id: number
  title: string
  symptom: string | null
  lesson: string
  evidence: string | null
  sourceAgentId: number | null
  sourceConversationId: number | null
  sourceMessageId: number | null
  status: AiExperienceLessonStatus
  lessonNo: number | null
  createdAt: string
  updatedAt: string | null
  reviewedAt: string | null
}

/** 手动新建候选：title/lesson 必填，来源三 id 由后端固定留空。 */
export interface AiExperienceLessonCreateInput {
  title: string
  symptom?: string | null
  lesson: string
  evidence?: string | null
}

/** 批准修订：字段缺省＝不改、沿用候选原文；批准存改写后的版本。 */
export interface AiExperienceLessonApproveInput {
  title?: string | null
  symptom?: string | null
  lesson?: string | null
  evidence?: string | null
}

/** 一批提炼的汇总（与后端 AdminAiExperienceExtractionResultDto 逐项对齐）。 */
export interface AiExperienceExtractionResult {
  windowDays: number
  scannedTurns: number
  extractionCalls: number
  producedCandidates: number
  failedTurns: number
}

/** AI 用量来源（与后端 AgentUsageService 的 SOURCE_* 常量一致）。 */
export type AiUsageSource = 'PORTAL' | 'ADMIN_PREVIEW' | 'GATEWAY_V1'

export type AiUsageStatus = 'SUCCESS' | 'UPSTREAM_ERROR'

/**
 * AI 用量聚合报告（Issue #140 UoW-2），字段与后端 AdminAiUsageDtos 逐项对齐：
 * daily 按 UTC 日升序、byAgent/byModel 按调用数降序、recent 按时间倒序（条数受 limit 夹取）。
 */
export interface AiUsageReport {
  daily: AiUsageDaily[]
  byAgent: AiUsageByAgent[]
  byModel: AiUsageByModel[]
  recent: AiUsageRecent[]
}

/** 按 UTC 日聚合；unknownTokenCalls 是 tokens 记 NULL 的调用数（未知不并入 0 静默吞掉）。 */
export interface AiUsageDaily {
  date: string
  calls: number
  promptTokens: number
  completionTokens: number
  unknownTokenCalls: number
}

export interface AiUsageByAgent {
  agentId: number | null
  calls: number
  promptTokens: number
  completionTokens: number
}

/** 按入口聚合；alias 只有 GATEWAY_V1 行有值。 */
export interface AiUsageByModel {
  entryId: number | null
  alias: string | null
  calls: number
  promptTokens: number
  completionTokens: number
}

export interface AiUsageRecent {
  id: number
  createdAt: string
  source: AiUsageSource
  agentId: number | null
  entryId: number | null
  alias: string | null
  mode: string | null
  /** 上游未回 usage 时为 null（未知），不得按 0 呈现。 */
  promptTokens: number | null
  completionTokens: number | null
  latencyMs: number | null
  status: AiUsageStatus
  gatewayAccessKeyId: number | null
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
  /** 最近一次成功索引时所用的 embedding spec；为 null 表示未索引过或用了进程默认嵌入模型。 */
  indexEmbeddingSpec: string | null
  /** 已索引但所用 embedding 模型与当前 selection 不一致（存量索引已过期，需重索引/重嵌修复）。 */
  indexStale: boolean
  sourceFormat: string | null
  originalFilename: string | null
  fileSizeBytes: number | null
  hasOriginal: boolean
  version: number
  createdAt: string
  updatedAt: string
  /** 未求值公式格数量。大于 0 时正文里这些格子是空的。 */
  unevaluatedFormulaCount?: number
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
  /** 已索引文档中，所用 embedding 模型与当前 selection 不一致的数量。 */
  staleDocuments: number
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

/** PORTAL＝站点用户正式对话；ADMIN_PREVIEW＝后台试聊落库。 */
export type AiConversationSource = 'PORTAL' | 'ADMIN_PREVIEW'

export interface AiConversationListItem {
  id: number
  agentId: number
  /** 预览会话为 null。 */
  agentUserId: number | null
  source: AiConversationSource
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

export interface AiCitationLocation {
  page?: number | null
  sheet?: string | null
  rowStart?: number | null
  rowEnd?: number | null
}

export interface AiCitation {
  sourceId: string
  chunkIndex: number
  score: number
  title?: string | null
  location?: AiCitationLocation | null
  cite?: number | null
}

export interface AiRunMeta {
  usage: AiRunUsage
  tools: AiToolCallTrace[]
  skillsApplied: string[]
  /** 本轮实际作答的模型名（执行层回报）。旧消息没有；旧消息的该字段曾是请求值。 */
  model?: string | null
  /** 本轮请求的主用入口 spec；与 model 不同即发生过备援切换。旧消息没有。 */
  requestedModel?: string | null
  /** 本轮模式（CLI / KEY）。旧消息没有。 */
  mode?: string | null
  /** 本轮引用。旧消息没有。后台只展示，不打开阅读器。 */
  citations?: AiCitation[] | null
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

/**
 * 对话预览回填：与 previewStream 走同一个 previewMode + selectionChain 判定路径。
 * mode 缺省时服务端按默认判定选出模式；modelSpec 为该模式链首 spec，无链为 null；
 * available 即链是否非空（含默认入口 key 解密失败导致整链为空的场景）。
 */
export interface AiPreviewModelEntry {
  id: number
  displayName: string
  modelSpec: string
}

export interface AiPreviewModelBackfill {
  mode: 'CLI' | 'KEY'
  modelSpec: string | null
  available: boolean
  entries?: AiPreviewModelEntry[]
}

export interface AiPreviewChatInput {
  message: string
  history?: AiPreviewHistoryMessage[]
  /** 含空数组：明确不注入 skill。缺省字段才会走服务端默认 skill。 */
  skills?: string[]
  todos?: AiPreviewTodoItem[]
  mode?: 'CLI' | 'KEY'
  /** 这一轮点名的入口。不传则用该模式链首。 */
  modelEntryId?: number
  /** 续写已有预览会话；空则 DONE 成功后新建，SSE CONVERSATION 回传 id。 */
  conversationId?: number
  /** 浏览器 IANA 时区。缺省时执行层回退 UTC。只用于钟点。 */
  timezone?: string
  /** 浏览器或手机定位权限授予后的纬度。须与经度一起传。 */
  latitude?: number
  /** 浏览器或手机定位权限授予后的经度。 */
  longitude?: number
  /** 定位精度，单位米。 */
  accuracyMeters?: number
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
  | {
      type: 'done'
      todos: AiPreviewTodoItem[]
      usage: AiRunUsage | null
      model: string | null
      /** 本轮引用（DONE 才有）：正文 [cN] 标记按 cite 对应，呈现层据此挂角标。 */
      citations: AiCitation[]
    }
  /** message：Java 推流前失败时为本地化文案；推流中为 agent-services 原始诊断（仅后台可见） */
  | { type: 'error'; errorCode: string; message?: string }
  /** DONE 成功落库后回传；用于续写同一预览会话。 */
  | { type: 'conversation'; conversationId: number }

/**
 * 回复内卡片信封（Issue #179）：VISUALIZATION 事件 data 原文，与 agent-services
 * visualization.card_envelope 对齐——{ kind, payload, title? }（空 title 不写出，
 * 天气卡恒无 title）。adapter 校验＋清洗后才进消息流，字段口径以 Python
 * build_weather_card / build_chart_card 的写出为准：数值缺失即字段不写出，
 * 故可选字段一律 optional；time/date 是 payload 时区下的本地时刻字符串，
 * 呈现层原样用、不做二次时区换算。
 */
export interface AiWeatherCurrent {
  /** 本地时刻 ISO 字符串（如 2026-11-03T17:00）。 */
  time: string
  temperatureC?: number
  apparentTemperatureC?: number
  humidityPercent?: number
  windSpeedKmh?: number
  precipitationMm?: number
  weatherCode?: number
  /** Python 按 WMO 码表给的天气状况文本（英文），呈现层原样显示、不另译。 */
  weatherText?: string
}

export interface AiWeatherHourlyPoint {
  time: string
  temperatureC: number
  precipitationProbabilityPercent?: number
  weatherCode?: number
}

export interface AiWeatherDailyPoint {
  /** 本地日期字符串（YYYY-MM-DD）。 */
  date: string
  temperatureMaxC: number
  temperatureMinC: number
  precipitationProbabilityMaxPercent?: number
  weatherCode?: number
  weatherText?: string
}

export interface AiWeatherCardPayload {
  latitude?: number
  longitude?: number
  timezone?: string
  current: AiWeatherCurrent
  hourly: AiWeatherHourlyPoint[]
  daily: AiWeatherDailyPoint[]
}

export type AiChartType = 'line' | 'bar' | 'area' | 'pie'

export interface AiChartPoint {
  label: string
  value: number
}

export interface AiChartSeries {
  name: string
  points: AiChartPoint[]
}

/** chart payload 不含 title（在信封层）；上限系列 ≤4、每系列点 ≤50 由执行层校验保证。 */
export interface AiChartCardPayload {
  type: AiChartType
  series: AiChartSeries[]
}

export type AiVisualizationEnvelope =
  | { kind: 'weather'; title?: string; payload: AiWeatherCardPayload }
  | { kind: 'chart'; title?: string; payload: AiChartCardPayload }
