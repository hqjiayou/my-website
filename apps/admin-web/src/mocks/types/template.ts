export type TemplateStatus = 'enabled' | 'disabled' | 'draft'

export interface SiteTemplate {
  id: string
  code: string
  name: string
  industry: string
  description: string
  status: TemplateStatus
  previewImage?: string
  defaultPrimaryColor: string
  supportedPages: string[]
  supportedBlocks: string[]
  targetSiteTypes: string[]
  createdAt: string
  updatedAt: string
}
