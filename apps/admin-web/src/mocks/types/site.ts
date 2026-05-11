export type SiteStatus = 'active' | 'draft' | 'inactive'

export type SiteIndustry =
  | 'car-repair'
  | 'used-car'
  | 'cake'
  | 'salon'
  | 'clinic'
  | 'gyoseishoshi'

export const SITE_STATUS_VALUES: SiteStatus[] = ['active', 'draft', 'inactive']

export const SITE_INDUSTRY_VALUES: SiteIndustry[] = [
  'car-repair',
  'used-car',
  'cake',
  'salon',
  'clinic',
  'gyoseishoshi',
]

export interface Site {
  id: string
  name: string
  code: string
  industry: SiteIndustry
  templateCode: string
  domain: string
  status: SiteStatus
  description: string
  primaryColor: string
  phone: string
  address: string
  businessHours: string
  createdAt: string
  updatedAt: string
}

export interface CreateSiteInput {
  name: string
  code: string
  industry: SiteIndustry
  templateCode: string
  domain: string
  description: string
  primaryColor: string
  phone: string
  address: string
  businessHours: string
}

export interface SitePublishChecklist {
  basicInfo: boolean
  theme: boolean
  domain: boolean
  homeContent: boolean
  aiConfig: boolean
}

export interface SiteRuntimeStatus {
  siteId: string
  visitCountToday: number
  reservationCountToday: number
  aiConversationCountToday: number
  publishChecklist: SitePublishChecklist
}
