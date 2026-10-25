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

export interface SiteAiMessage {
  id: number
  role: string
  content: string
  createdAt: string | null
}

export interface SiteAiChatRequest {
  message: string
  conversationId?: string
}

/** 本輪實際採用的知識庫引用。歷史訊息介面不回這個欄位。 */
export interface SiteAiCitation {
  sourceId: string
  chunkIndex: number
  score: number
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
}
