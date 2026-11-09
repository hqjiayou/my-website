import type { AdminTableQuery, DataTableColumnDef, TableSortOrder, TableSortState } from './types'

export function defaultAdminTableQuery(pageSize = 20): AdminTableQuery {
  return { page: 1, pageSize }
}

/** 筛选/搜索变更时回到第 1 页；分页字段本身不触发重置。 */
export function withPageReset(
  prev: AdminTableQuery,
  patch: Partial<Omit<AdminTableQuery, 'page'>> & { page?: number },
): AdminTableQuery {
  const next = { ...prev, ...patch }
  const filterChanged =
    ('q' in patch && patch.q !== prev.q) ||
    ('status' in patch && patch.status !== prev.status) ||
    ('provider' in patch && patch.provider !== prev.provider) ||
    ('sort' in patch && patch.sort !== prev.sort) ||
    ('order' in patch && patch.order !== prev.order) ||
    ('pageSize' in patch && patch.pageSize !== prev.pageSize)
  if (filterChanged && patch.page == null) {
    next.page = 1
  }
  return next
}

export function toggleSortOrder(
  current: TableSortState,
  columnId: string,
): { columnId: string; order: TableSortOrder } {
  if (current.columnId !== columnId) {
    return { columnId, order: 'asc' }
  }
  return { columnId, order: current.order === 'asc' ? 'desc' : 'asc' }
}

function compareSortValues(
  left: string | number | null | undefined,
  right: string | number | null | undefined,
  order: TableSortOrder,
): number {
  const empty = (value: string | number | null | undefined) => value == null || value === ''
  if (empty(left) && empty(right)) return 0
  if (empty(left)) return 1
  if (empty(right)) return -1
  if (typeof left === 'number' && typeof right === 'number') {
    return order === 'asc' ? left - right : right - left
  }
  const a = String(left)
  const b = String(right)
  const result = a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' })
  return order === 'asc' ? result : -result
}

/**
 * 仅排序传入数组（当前页/当前已加载结果）。scope 必须是 client-page 才应调用。
 */
export function sortClientPageRows<T>(
  rows: T[],
  columns: Array<Pick<DataTableColumnDef<T>, 'id' | 'sortValue'>>,
  sort: Pick<TableSortState, 'columnId' | 'order'>,
): T[] {
  if (!sort.columnId) return rows
  const column = columns.find((item) => item.id === sort.columnId)
  if (!column?.sortValue) return rows
  const indexed = rows.map((row, index) => ({ row, index }))
  indexed.sort((a, b) => {
    const result = compareSortValues(column.sortValue!(a.row), column.sortValue!(b.row), sort.order)
    return result !== 0 ? result : a.index - b.index
  })
  return indexed.map((item) => item.row)
}

export function resolveVisibleColumns<T>(
  columns: Array<Pick<DataTableColumnDef<T>, 'id' | 'hideable' | 'defaultVisible'>>,
  hiddenIds: string[],
): string[] {
  const required = columns.filter((column) => column.hideable === false).map((column) => column.id)
  const hidden = new Set(hiddenIds)
  const visible = columns
    .filter((column) => {
      if (column.hideable === false) return true
      if (hidden.has(column.id)) return false
      return column.defaultVisible !== false
    })
    .map((column) => column.id)

  if (visible.length === 0 && columns.length > 0) {
    return [required[0] ?? columns[0]!.id]
  }
  return visible
}

/** 切换隐藏时保证至少保留一列必需列（或第一列）。 */
export function toggleColumnHidden(
  columns: Array<Pick<DataTableColumnDef<unknown>, 'id' | 'hideable'>>,
  hiddenIds: string[],
  columnId: string,
): string[] {
  const column = columns.find((item) => item.id === columnId)
  if (!column || column.hideable === false) return hiddenIds

  const hidden = new Set(hiddenIds)
  if (hidden.has(columnId)) {
    hidden.delete(columnId)
    return [...hidden]
  }

  const nextHidden = [...hidden, columnId]
  const visibleCount = columns.filter((item) => !nextHidden.includes(item.id)).length
  if (visibleCount === 0) return hiddenIds
  return nextHidden
}

export function readStoredStringArray(storageKey: string): string[] | null {
  if (typeof window === 'undefined') return null
  try {
    const raw = window.localStorage.getItem(storageKey)
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    if (!Array.isArray(parsed) || parsed.some((item) => typeof item !== 'string')) return null
    return parsed as string[]
  } catch {
    return null
  }
}

export function writeStoredStringArray(storageKey: string, values: string[]): void {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.setItem(storageKey, JSON.stringify(values))
  } catch {
    // ignore quota / private mode
  }
}

export function serializeAdminTableQuery(query: AdminTableQuery): Record<string, string> {
  const params: Record<string, string> = {
    page: String(query.page),
    pageSize: String(query.pageSize),
  }
  if (query.q) params.q = query.q
  if (query.status) params.status = query.status
  if (query.provider) params.provider = query.provider
  if (query.sort) params.sort = query.sort
  if (query.order) params.order = query.order
  return params
}

export function parseAdminTableQuery(
  search: URLSearchParams | Record<string, string | undefined>,
  defaults: AdminTableQuery = defaultAdminTableQuery(),
): AdminTableQuery {
  const get = (key: string) =>
    search instanceof URLSearchParams ? (search.get(key) ?? undefined) : search[key]
  const page = Number(get('page'))
  const pageSize = Number(get('pageSize'))
  const order = get('order')
  return {
    q: get('q') || undefined,
    status: get('status') || undefined,
    provider: get('provider') || undefined,
    sort: get('sort') || undefined,
    order: order === 'asc' || order === 'desc' ? order : defaults.order,
    page: Number.isFinite(page) && page >= 1 ? Math.floor(page) : defaults.page,
    pageSize: Number.isFinite(pageSize) && pageSize >= 1 ? Math.floor(pageSize) : defaults.pageSize,
  }
}

export function toggleKeySelection(
  selected: Array<string | number>,
  key: string | number,
  checked: boolean,
): Array<string | number> {
  const exists = selected.some((item) => item === key)
  if (checked && !exists) return [...selected, key]
  if (!checked && exists) return selected.filter((item) => item !== key)
  return selected
}

export function setPageSelection(
  selected: Array<string | number>,
  pageKeys: Array<string | number>,
  checked: boolean,
): Array<string | number> {
  const pageSet = new Set(pageKeys)
  if (checked) {
    const next = [...selected]
    for (const key of pageKeys) {
      if (!next.some((item) => item === key)) next.push(key)
    }
    return next
  }
  return selected.filter((key) => !pageSet.has(key))
}
