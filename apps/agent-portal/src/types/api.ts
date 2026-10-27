export interface HttpMessageBody<T> {
  code?: number | null
  message?: string
  data: T
}

export interface SiteTokenResponse {
  accessToken: string
  accessTokenExpiresIn: number
}

export interface SiteAuthUser {
  userCode: string
  username: string
  displayName: string
  avatarUrl: string | null
  status: string
}

export interface SiteAuthMe {
  user: SiteAuthUser
  roles: string[]
  permissions: string[]
  menus: SiteMenuNode[]
  site?: {
    siteId: number
    siteCode: string
    siteName: string
  } | null
}

export interface SiteMenuNode {
  menuCode: string
  menuName: string
  path: string
  icon: string
  sort: number
  children: SiteMenuNode[]
}

export interface SiteLoginInput {
  siteCode: string
  username: string
  password: string
}

export interface SiteAiConversation {
  id: number
  title: string | null
  lastMessageAt: string | null
  createdAt: string | null
}

export interface SiteAiAttachment {
  mediaType: string
  /** 文件項（kind=DOCUMENT）只有描述、無原文，data 為 null。 */
  data: string | null
  fileName?: string | null
  kind?: string | null
}

/** 本輪上傳文件（原文 base64）。站點層抽取文本後才給模型，原文不落庫。 */
export interface SiteAiDocument {
  fileName: string
  mediaType: string
  data: string
}

/** 历史消息的过程摘要（服务端自 agent_meta 回放）：工具名与成败、搜索查询词与命中数。 */
export interface SiteAiMessageProcess {
  tools: { name: string; ok: boolean }[]
  searches: { query: string; hitCount: number }[]
}

export interface SiteAiMessage {
  id: number
  role: string
  content: string
  createdAt: string | null
  attachments?: SiteAiAttachment[] | null
  /** 僅 ASSISTANT。無引用時為 null。 */
  citations?: SiteAiCitation[] | null
  /** 僅 ASSISTANT。無過程記錄時為 null。 */
  process?: SiteAiMessageProcess | null
}

export interface SiteAiChatRequest {
  message: string
  conversationId?: string
  attachments?: SiteAiAttachment[]
  documents?: SiteAiDocument[]
}

/** 本輪實際採用的知識庫引用。歷史訊息的 ASSISTANT 項也帶回這個欄位。 */
export interface SiteAiCitation {
  sourceId: string
  chunkIndex: number
  score: number
}

/** 知识搜索工具的安全摘要（TOOL_RESULT 事件携带）：只有查询词、命中数与来源标识，不含片段正文。 */
export interface SiteSearchSummary {
  query: string
  hitCount: number
  sources: SiteAiCitation[]
}

export interface SiteKnowledgeHit {
  sourceId: string
  chunkIndex: number
  score: number
  content: string
}

export interface SiteKnowledgeSearchResult {
  hits: SiteKnowledgeHit[]
  degraded: boolean
  rerankApplied?: boolean
}

export interface SiteKnowledgeDocumentListItem {
  id: number
  title: string
  updatedAt: string | null
}

export interface SiteKnowledgeDocumentDetail {
  id: number
  title: string
  content: string
  updatedAt: string | null
}

export interface SiteUserMemory {
  id: number
  content: string
  createdAt: string | null
}
