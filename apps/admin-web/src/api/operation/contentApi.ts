/**
 * 运营「全局内容」中心。
 *
 * 无专用跨站后端；通过 fan-out 站点级 REAL API 聚合（`/admin/sites/{siteId}/contents`）。
 */
import { contentApi } from '@/api/apps/contentApi'
import type { ContentListItem } from '@/types/content'
import type { PageResult } from '@/types/site'

import { listAllSitesForAggregation, paginateClient } from './aggregateSites'

/** 运营全局内容中心已接入（客户端跨站聚合 REAL API）。 */
export const OPERATION_CONTENTS_CONNECTED = true

export interface OperationContentRow extends ContentListItem {
  siteId: number
  siteCode: string
  siteName: string
}

export interface OperationContentListQuery {
  pageNum?: number
  pageSize?: number
  type?: string
  status?: string
}

const PER_SITE_PAGE_SIZE = 100

export async function listOperationContents(
  query: OperationContentListQuery = {},
): Promise<PageResult<OperationContentRow>> {
  const pageNum = query.pageNum ?? 1
  const pageSize = query.pageSize ?? 20
  const sites = await listAllSitesForAggregation()

  const rows: OperationContentRow[] = []
  await Promise.all(
    sites.map(async (site) => {
      const page = await contentApi.listContents(site.id, {
        pageNum: 1,
        pageSize: PER_SITE_PAGE_SIZE,
        type: query.type,
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

  rows.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
  return paginateClient(rows, pageNum, pageSize)
}
