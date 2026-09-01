/**
 * 运营「全局预约」中心。
 *
 * 无专用跨站后端；通过 fan-out 站点级 REAL API 聚合（`/admin/sites/{siteId}/reservations`）。
 */
import { reservationApi } from '@/api/apps/reservationApi'
import type { ReservationListItem } from '@/types/reservation'
import type { PageResult } from '@/types/site'

import { listAllSitesForAggregation, paginateClient } from './aggregateSites'

/** 运营全局预约中心已接入（客户端跨站聚合 REAL API）。 */
export const OPERATION_RESERVATIONS_CONNECTED = true

export interface OperationReservationRow extends ReservationListItem {
  siteId: number
  siteCode: string
  siteName: string
}

export interface OperationReservationListQuery {
  pageNum?: number
  pageSize?: number
  status?: string
}

const PER_SITE_PAGE_SIZE = 100

export async function listOperationReservations(
  query: OperationReservationListQuery = {},
): Promise<PageResult<OperationReservationRow>> {
  const pageNum = query.pageNum ?? 1
  const pageSize = query.pageSize ?? 20
  const sites = await listAllSitesForAggregation()

  const rows: OperationReservationRow[] = []
  await Promise.all(
    sites.map(async (site) => {
      const page = await reservationApi.listReservations(site.id, {
        pageNum: 1,
        pageSize: PER_SITE_PAGE_SIZE,
        status: query.status,
      })
      for (const item of page.records) {
        rows.push({
          ...item,
          siteId: site.id,
          siteCode: site.siteCode,
          siteName: site.siteName,
        })
      }
    }),
  )

  rows.sort((a, b) => new Date(b.reservedAt).getTime() - new Date(a.reservedAt).getTime())
  return paginateClient(rows, pageNum, pageSize)
}
