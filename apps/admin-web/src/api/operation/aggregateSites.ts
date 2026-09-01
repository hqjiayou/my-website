import { siteApi } from '@/api/apps/siteApi'
import type { PageResult, SiteListItem } from '@/types/site'

const MAX_SITE_PAGES = 10
const SITE_PAGE_SIZE = 100

/** 跨站聚合：拉取当前账号可见的全部站点（有页数上限，避免无限 fan-out）。 */
export async function listAllSitesForAggregation(): Promise<SiteListItem[]> {
  const sites: SiteListItem[] = []
  for (let pageNum = 1; pageNum <= MAX_SITE_PAGES; pageNum++) {
    const page = await siteApi.listSites({ pageNum, pageSize: SITE_PAGE_SIZE })
    sites.push(...page.records)
    if (sites.length >= page.total || page.records.length === 0) break
  }
  return sites
}

export function paginateClient<T>(
  items: T[],
  pageNum: number,
  pageSize: number,
): PageResult<T> {
  const total = items.length
  const start = (pageNum - 1) * pageSize
  return {
    records: items.slice(start, start + pageSize),
    total,
    pageNum,
    pageSize,
  }
}
