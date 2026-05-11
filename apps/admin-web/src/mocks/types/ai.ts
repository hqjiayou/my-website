export interface AiConfig {
  id: string
  siteId: string
  model: string
  temperature: number
  enabled: boolean
  updatedAt: string
}

export interface KnowledgeBase {
  id: string
  siteId: string
  name: string
  documentCount: number
  status: 'ready' | 'indexing'
  updatedAt: string
}

export interface AiConversation {
  id: string
  siteId: string
  visitorName: string
  question: string
  status: 'open' | 'resolved'
  createdAt: string
}

export interface AiPrompt {
  id: string
  scene: string
  title: string
  content: string
  updatedAt: string
}
