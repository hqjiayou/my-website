import { appearanceApi } from '@/api/appearanceApi'
import { aiApi } from '@/api/apps/aiApi'
import { contentApi } from '@/api/apps/contentApi'
import { reservationApi } from '@/api/apps/reservationApi'
import { siteApi } from '@/api/apps/siteApi'
import type { SiteRuntimeStatus } from '@/types/site'

function isSameLocalDay(iso: string): boolean {
  const value = new Date(iso)
  const now = new Date()
  return (
    value.getFullYear() === now.getFullYear() &&
    value.getMonth() === now.getMonth() &&
    value.getDate() === now.getDate()
  )
}

/**
 * 由现有 REAL API 推导站点概览指标（非独立 runtime-status 端点，不读 mock 开关）。
 * 访问量 / 当日 AI 会话暂无后端，返回 null。
 * AI 配置：prompt profile 已落库（id 非空）为完成；空壳或无权限时为未完成。
 */
export async function deriveSiteRuntimeStatus(siteId: number): Promise<SiteRuntimeStatus> {
  const site = await siteApi.getSite(siteId)

  const [appearance, contents, reservations, prompt] = await Promise.all([
    appearanceApi.getAppearance(siteId).catch(() => null),
    contentApi
      .listContents(siteId, { pageNum: 1, pageSize: 50, status: 'PUBLISHED' })
      .catch(() => ({ records: [], total: 0, pageNum: 1, pageSize: 50 })),
    reservationApi
      .listReservations(siteId, { pageNum: 1, pageSize: 100 })
      .catch(() => ({ records: [], total: 0, pageNum: 1, pageSize: 100 })),
    aiApi.getPromptProfile(siteId).catch(() => null),
  ])

  const reservationCountToday = reservations.records.filter((item) =>
    isSameLocalDay(item.reservedAt),
  ).length

  return {
    siteId,
    visitCountToday: null,
    reservationCountToday,
    aiConversationCountToday: null,
    publishChecklist: {
      basicInfo: Boolean(site.siteName?.trim() && site.siteCode?.trim()),
      theme: appearance != null,
      domain: Boolean(site.defaultDomain?.trim()),
      homeContent: contents.records.length > 0,
      aiConfig: prompt?.id != null,
    },
  }
}
