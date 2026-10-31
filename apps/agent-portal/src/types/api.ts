export interface HttpMessageBody<T> {
  code?: number | null
  message?: string
  data: T
}

export interface AgentTokenResponse {
  accessToken: string
  accessTokenExpiresIn: number
}

export interface AgentAuthUser {
  userCode: string
  username: string
  displayName: string
  avatarUrl: string | null
  status: string
}

export interface AgentAuthMe {
  user: AgentAuthUser
  roles: string[]
  permissions: string[]
  menus: AgentMenuNode[]
  site?: {
    agentId: number
    agentCode: string
    agentName: string
  } | null
}

export interface AgentMenuNode {
  menuCode: string
  menuName: string
  path: string
  icon: string
  sort: number
  children: AgentMenuNode[]
}

export interface AgentLoginInput {
  agentCode: string
  username: string
  password: string
}

export interface AgentAiConversation {
  id: number
  title: string | null
  lastMessageAt: string | null
  createdAt: string | null
}

export interface AgentAiShare {
  token: string
}

/** 跨對話全文搜尋的一條命中。 */
export interface AgentAiSearchHit {
  conversationId: number
  conversationTitle: string
  messageId: number
  role: string
  snippet: string
  createdAt: string | null
}

export interface SharedConversationMessage {
  role: 'USER' | 'ASSISTANT' | string
  content: string | null
  createdAt: string | null
  imageCount: number
  documentNames: string[]
}

/** 公開分享快照（分享時刻的內容，不隨原對話後續編輯變動）。 */
export interface SharedConversation {
  title: string | null
  sharedAt: string | null
  messages: SharedConversationMessage[]
}

export interface AgentAiAttachment {
  mediaType: string
  /** 文件項（kind=DOCUMENT）只有描述、無原文，data 為 null。 */
  data: string | null
  fileName?: string | null
  kind?: string | null
}

/** 本輪上傳文件（原文 base64）。站點層抽取文本後才給模型，原文不落庫。 */
export interface AgentAiDocument {
  fileName: string
  mediaType: string
  data: string
}

/** 历史消息的过程摘要（服务端自 agent_meta 回放）：工具名与成败、搜索查询词与命中数。 */
export interface AgentAiMessageProcess {
  tools: { name: string; ok: boolean }[]
  searches: { query: string; hitCount: number }[]
}

export interface AgentAiMessage {
  id: number
  role: string
  content: string
  createdAt: string | null
  attachments?: AgentAiAttachment[] | null
  /** 僅 ASSISTANT。無引用時為 null。 */
  citations?: AgentAiCitation[] | null
  /** 僅 ASSISTANT。無過程記錄時為 null。 */
  process?: AgentAiMessageProcess | null
  /** 僅 ASSISTANT。這一輪請求的入口 spec；舊消息為 null。 */
  requestedModel?: string | null
}

export interface AgentAiChatRequest {
  message: string
  conversationId?: string
  attachments?: AgentAiAttachment[]
  documents?: AgentAiDocument[]
  /** 雙模式：KEY 直連 / CLI 代理池。必填。 */
  mode?: string
  /** 下一輪點名的入口。不傳則用該模式的預設入口。 */
  modelEntryId?: number
}

/** Portal 雙模式可用性（GET /ai/model-modes）：available=false 表示該模式尚未配置入口。 */
export interface AgentAiModelEntryOption {
  id: number
  displayName: string
  modelSpec: string
}

export interface AgentAiModelModeOption {
  mode: string
  available: boolean
  label: string | null
  entries?: AgentAiModelEntryOption[]
}

export interface AgentAiModelModes {
  modes: AgentAiModelModeOption[]
}

/** 片段在原件中的位置。頁碼與表名用於跳到對應標題，不滾表內行。 */
export interface AgentAiCitationLocation {
  page?: number | null
  sheet?: string | null
  rowStart?: number | null
  rowEnd?: number | null
}

/** 本輪實際採用的知識庫引用。歷史訊息的 ASSISTANT 項也帶回這個欄位。 */
export interface AgentAiCitation {
  sourceId: string
  chunkIndex: number
  score: number
  title?: string | null
  location?: AgentAiCitationLocation | null
  cite?: number | null
}

/** 知识搜索工具的安全摘要（TOOL_RESULT 事件携带）：只有查询词、命中数与来源标识，不含片段正文。 */
export interface AgentSearchSummary {
  query: string
  hitCount: number
  sources: AgentAiCitation[]
}

export interface AgentKnowledgeHit {
  sourceId: string
  chunkIndex: number
  score: number
  content: string
}

export interface AgentKnowledgeSearchResult {
  hits: AgentKnowledgeHit[]
  degraded: boolean
  rerankApplied?: boolean
}

export interface AgentKnowledgeDocumentListItem {
  id: number
  title: string
  updatedAt: string | null
}

export interface AgentKnowledgeDocumentDetail {
  id: number
  title: string
  content: string
  updatedAt: string | null
}

export interface AgentUserMemory {
  id: number
  content: string
  createdAt: string | null
}
