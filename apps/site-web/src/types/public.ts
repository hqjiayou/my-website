export interface PublicSite {
  siteCode: string
  siteName: string
  siteType: string
  description: string | null
}

export interface PublicContentItem {
  id: number
  type: string
  title: string
  coverMediaId: number | null
  publishedAt: string | null
}

export interface PublicContentList {
  items: PublicContentItem[]
}

export interface PublicContentDetail {
  id: number
  type: string
  title: string
  body: string | null
  coverMediaId: number | null
  publishedAt: string | null
}
