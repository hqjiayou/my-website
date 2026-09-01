/**
 * 运营「文件中心」API。
 *
 * 无专用跨站后端；通过 fan-out 站点级 REAL API 聚合（`/admin/sites/{siteId}/media`）。
 */
import { mediaApi } from '@/api/apps/mediaApi'
import type { MediaListItem } from '@/types/media'
import type { PageResult } from '@/types/site'

import { listAllSitesForAggregation, paginateClient } from './aggregateSites'

/** 运营全局文件中心已接入（客户端跨站聚合 REAL API）。 */
export const OPERATION_FILES_CONNECTED = true

export interface OperationMediaRow extends MediaListItem {
  siteId: number
  siteCode: string
  siteName: string
}

export interface OperationMediaListQuery {
  pageNum?: number
  pageSize?: number
  contentTypePrefix?: string
}

const PER_SITE_PAGE_SIZE = 100

export async function listOperationMedia(
  query: OperationMediaListQuery = {},
): Promise<PageResult<OperationMediaRow>> {
  const pageNum = query.pageNum ?? 1
  const pageSize = query.pageSize ?? 20
  const sites = await listAllSitesForAggregation()

  const rows: OperationMediaRow[] = []
  await Promise.all(
    sites.map(async (site) => {
      const page = await mediaApi.listMedia(site.id, {
        pageNum: 1,
        pageSize: PER_SITE_PAGE_SIZE,
        contentTypePrefix: query.contentTypePrefix,
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

  rows.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
  return paginateClient(rows, pageNum, pageSize)
}

/** 站点媒体能力与 mediaApi 同源（始终 REAL）。 */
export const fileApi = {
  listMedia: mediaApi.listMedia,
  getMedia: mediaApi.getMedia,
  uploadMedia: mediaApi.uploadMedia,
  previewMedia: mediaApi.previewMedia,
  deleteMedia: mediaApi.deleteMedia,
}
