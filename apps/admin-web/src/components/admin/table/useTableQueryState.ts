import { useCallback, useMemo, useState } from 'react'

import {
  defaultAdminTableQuery,
  parseAdminTableQuery,
  serializeAdminTableQuery,
  withPageReset,
} from './tableHelpers'
import type { AdminTableQuery, TableSortOrder } from './types'

export function useTableQueryState(options?: {
  initial?: Partial<AdminTableQuery>
  /** 若提供，用 URLSearchParams 初始化（只读一次）。 */
  searchParams?: URLSearchParams
}) {
  const [query, setQueryState] = useState<AdminTableQuery>(() => {
    const defaults = defaultAdminTableQuery(options?.initial?.pageSize ?? 20)
    const fromUrl = options?.searchParams
      ? parseAdminTableQuery(options.searchParams, { ...defaults, ...options.initial })
      : { ...defaults, ...options?.initial }
    return fromUrl
  })

  const setQuery = useCallback((patch: Partial<AdminTableQuery>) => {
    setQueryState((prev) => withPageReset(prev, patch))
  }, [])

  const setPage = useCallback((page: number) => {
    setQueryState((prev) => ({ ...prev, page }))
  }, [])

  const setPageSize = useCallback((pageSize: number) => {
    setQueryState((prev) => withPageReset(prev, { pageSize }))
  }, [])

  const setSort = useCallback((sort: string | undefined, order: TableSortOrder | undefined) => {
    setQueryState((prev) => withPageReset(prev, { sort, order }))
  }, [])

  const resetFilters = useCallback(() => {
    setQueryState((prev) => ({
      ...defaultAdminTableQuery(prev.pageSize),
      pageSize: prev.pageSize,
    }))
  }, [])

  const asSearchParams = useMemo(() => serializeAdminTableQuery(query), [query])

  return {
    query,
    setQuery,
    setPage,
    setPageSize,
    setSort,
    resetFilters,
    asSearchParams,
  }
}
