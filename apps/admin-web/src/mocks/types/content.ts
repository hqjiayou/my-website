export type ContentType = 'hero' | 'service' | 'case' | 'faq' | 'news' | 'about' | 'cta'

export type ContentStatus = 'published' | 'draft' | 'hidden'

export interface ContentItem {
  id: string
  siteId: string
  type: ContentType
  title: string
  subtitle?: string
  description: string
  status: ContentStatus
  sortOrder: number
  imageUrl?: string
  tags?: string[]
  createdAt: string
  updatedAt: string
}

export interface CreateContentInput {
  siteId: string
  type: ContentType
  title: string
  subtitle?: string
  description: string
  status: ContentStatus
  sortOrder?: number
  imageUrl?: string
  tags?: string[]
}
