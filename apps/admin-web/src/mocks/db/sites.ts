import type { SiteRuntimeStatus } from '@/types/site'

/** 仅供 runtime-status mock；Site Core 列表/详情走 REAL API。 */
export const MOCK_SITE_RUNTIME_STATUS: SiteRuntimeStatus[] = [
  {
    siteId: 1,
    visitCountToday: 1246,
    reservationCountToday: 37,
    aiConversationCountToday: 88,
    publishChecklist: {
      basicInfo: true,
      theme: true,
      domain: true,
      homeContent: true,
      aiConfig: false,
    },
  },
  {
    siteId: 2,
    visitCountToday: 512,
    reservationCountToday: 14,
    aiConversationCountToday: 21,
    publishChecklist: {
      basicInfo: true,
      theme: true,
      domain: false,
      homeContent: true,
      aiConfig: false,
    },
  },
]
