import { AlertCircle, RefreshCw } from 'lucide-react'
import { useMemo, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

import { adminFilterFieldClass, adminSecondaryButtonClass } from './controls'
import { DataTableEmptyState } from './table/DataTableEmptyState'
import { DataTableSortHeader } from './table/DataTableSortHeader'
import { sortClientPageRows, toggleSortOrder } from './table/tableHelpers'
import type {
  DataTableColumnDef,
  TableEmptyKind,
  TableSortOrder,
  TableSortScope,
} from './table/types'

/** @deprecated 使用 DataTableColumnDef；保留别名以兼容现有页面 import。 */
export type DataTableColumn<T> = DataTableColumnDef<T>

export interface DataTablePagination {
  page: number
  pageSize: number
  total: number
  onPageChange: (page: number) => void
  pageSizeOptions?: number[]
  onPageSizeChange?: (pageSize: number) => void
}

export interface DataTableSortProps {
  columnId: string | null
  order: TableSortOrder
  scope: TableSortScope
  onChange: (next: { columnId: string; order: TableSortOrder }) => void
}

export interface DataTableSelectionProps<T> {
  selectedKeys: Array<string | number>
  onChange: (keys: Array<string | number>) => void
  isRowSelectable?: (row: T) => boolean
}

export interface DataTableProps<T> {
  columns: DataTableColumnDef<T>[]
  data: T[]
  rowKey: (row: T) => string | number
  isLoading?: boolean
  isError?: boolean
  errorMessage?: string
  emptyMessage?: string
  emptyKind?: TableEmptyKind
  emptyDescription?: string
  emptyAction?: ReactNode
  onClearFilters?: () => void
  onRetry?: () => void
  pagination?: DataTablePagination
  sort?: DataTableSortProps
  selection?: DataTableSelectionProps<T>
  className?: string
  /** 行点击（复选框与按钮区域应 stopPropagation）。 */
  onRowClick?: (row: T) => void
}

const pageButtonClass =
  'inline-flex h-7 min-w-7 items-center justify-center rounded-md px-2 text-xs font-medium text-admin transition hover:bg-primary/7 disabled:opacity-50'

/**
 * 能力底座表体：旧调用（仅 columns/data/pagination/loading/error/empty）保持行为不变。
 * 排序/选择/空态种类通过可选 props 启用；业务筛选与权限留在页面层。
 */
export function DataTable<T>({
  columns,
  data,
  rowKey,
  isLoading = false,
  isError = false,
  errorMessage,
  emptyMessage,
  emptyKind = 'empty',
  emptyDescription,
  emptyAction,
  onClearFilters,
  onRetry,
  pagination,
  sort,
  selection,
  className,
  onRowClick,
}: DataTableProps<T>) {
  const { t } = useTranslation('common')
  const showSelection = selection != null
  const colSpan = columns.length + (showSelection ? 1 : 0)
  const totalPages = pagination ? Math.max(1, Math.ceil(pagination.total / pagination.pageSize)) : 1
  const skeletonRows = 4

  const displayRows = useMemo(() => {
    if (!sort || sort.scope !== 'client-page' || !sort.columnId) return data
    return sortClientPageRows(data, columns, sort)
  }, [columns, data, sort])

  const selectablePageKeys = selection
    ? displayRows
        .filter((row) => selection.isRowSelectable?.(row) ?? true)
        .map((row) => rowKey(row))
    : []
  const allPageSelected =
    selectablePageKeys.length > 0 &&
    selectablePageKeys.every((key) => selection?.selectedKeys.some((item) => item === key))

  return (
    <div className={cn('overflow-hidden rounded-xl border border-admin bg-admin-surface', className)}>
      <div className="overflow-x-auto">
        <table className="min-w-full text-left text-sm">
          <thead className="bg-admin-surface-muted text-xs font-medium uppercase tracking-wide text-admin-muted">
            <tr>
              {showSelection ? (
                <th className="h-10 w-10 px-4">
                  <input
                    type="checkbox"
                    className="h-4 w-4 accent-[rgb(var(--primary))]"
                    aria-label={t('table.selectPage')}
                    checked={allPageSelected}
                    disabled={selectablePageKeys.length === 0 || isLoading || isError}
                    onChange={(event) => {
                      if (!selection) return
                      const checked = event.target.checked
                      if (checked) {
                        const next = [...selection.selectedKeys]
                        for (const key of selectablePageKeys) {
                          if (!next.some((item) => item === key)) next.push(key)
                        }
                        selection.onChange(next)
                      } else {
                        const pageSet = new Set(selectablePageKeys)
                        selection.onChange(selection.selectedKeys.filter((key) => !pageSet.has(key)))
                      }
                    }}
                  />
                </th>
              ) : null}
              {columns.map((column) => {
                const sortable = Boolean(column.sortable && sort)
                const active = sort?.columnId === column.id
                const ariaSort = !sortable
                  ? undefined
                  : active
                    ? sort!.order === 'asc'
                      ? 'ascending'
                      : 'descending'
                    : 'none'
                return (
                  <th
                    key={column.id}
                    className={cn('h-10 px-4', column.headerClassName)}
                    aria-sort={ariaSort}
                  >
                    {sortable && sort ? (
                      <DataTableSortHeader
                        label={column.header}
                        active={Boolean(active)}
                        order={sort.order}
                        scope={sort.scope}
                        onToggle={() => {
                          const next = toggleSortOrder(
                            { columnId: sort.columnId, order: sort.order, scope: sort.scope },
                            column.id,
                          )
                          sort.onChange(next)
                        }}
                      />
                    ) : (
                      column.header
                    )}
                  </th>
                )
              })}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {isLoading ? (
              Array.from({ length: skeletonRows }, (_, index) => (
                <tr key={`skeleton-${index}`} className="admin-table-row">
                  {showSelection ? (
                    <td className="px-4">
                      <div className="admin-skeleton h-3 w-3" />
                    </td>
                  ) : null}
                  {columns.map((column) => (
                    <td key={column.id} className="px-4">
                      <div className="admin-skeleton h-3 w-full max-w-40" />
                    </td>
                  ))}
                </tr>
              ))
            ) : isError ? (
              <tr>
                <td colSpan={colSpan} className="px-4 py-10">
                  <div className="flex flex-col items-center justify-center gap-2 text-center">
                    <AlertCircle className="h-6 w-6 text-admin-danger" />
                    <p className="text-sm text-admin">{errorMessage ?? t('table.error')}</p>
                    {onRetry ? (
                      <button type="button" onClick={onRetry} className={adminSecondaryButtonClass}>
                        <RefreshCw className="h-4 w-4" />
                        {t('table.retry')}
                      </button>
                    ) : null}
                  </div>
                </td>
              </tr>
            ) : displayRows.length === 0 ? (
              <tr>
                <td colSpan={colSpan}>
                  <DataTableEmptyState
                    kind={emptyKind}
                    title={emptyMessage}
                    description={emptyDescription}
                    action={emptyAction}
                    onClearFilters={onClearFilters}
                  />
                </td>
              </tr>
            ) : (
              displayRows.map((row) => {
                const key = rowKey(row)
                const selectable = selection?.isRowSelectable?.(row) ?? true
                const selected = selection?.selectedKeys.some((item) => item === key) ?? false
                return (
                  <tr
                    key={key}
                    className={cn('admin-table-hover', selected && 'bg-primary/8', onRowClick && 'cursor-pointer')}
                    onClick={onRowClick ? () => onRowClick(row) : undefined}
                  >
                    {showSelection ? (
                      <td className="px-4 align-middle" onClick={(event) => event.stopPropagation()}>
                        <input
                          type="checkbox"
                          className="h-4 w-4 accent-[rgb(var(--primary))]"
                          aria-label={t('table.selectRow')}
                          checked={selected}
                          disabled={!selectable}
                          title={!selectable ? t('table.rowNotSelectable') : undefined}
                          onChange={(event) => {
                            if (!selection || !selectable) return
                            const checked = event.target.checked
                            if (checked) {
                              selection.onChange(
                                selection.selectedKeys.some((item) => item === key)
                                  ? selection.selectedKeys
                                  : [...selection.selectedKeys, key],
                              )
                            } else {
                              selection.onChange(selection.selectedKeys.filter((item) => item !== key))
                            }
                          }}
                        />
                      </td>
                    ) : null}
                    {columns.map((column) => (
                      <td
                        key={column.id}
                        className={cn('px-4 align-middle text-admin admin-table-row', column.className)}
                      >
                        {column.cell(row)}
                      </td>
                    ))}
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>

      {pagination && (pagination.total > pagination.pageSize || pagination.onPageSizeChange) ? (
        <div className="flex flex-wrap items-center justify-between gap-4 border-t border-admin px-4 py-3">
          <span className="text-xs text-admin-muted">{t('table.paginationTotal', { total: pagination.total })}</span>
          <div className="flex flex-wrap items-center gap-2">
            {pagination.pageSizeOptions && pagination.onPageSizeChange ? (
              <label className="flex items-center gap-1.5 text-xs text-admin-muted">
                <span>{t('table.pageSize')}</span>
                <select
                  className={cn(adminFilterFieldClass, 'w-auto min-w-16')}
                  value={pagination.pageSize}
                  aria-label={t('table.pageSize')}
                  onChange={(event) => pagination.onPageSizeChange?.(Number(event.target.value))}
                >
                  {pagination.pageSizeOptions.map((size) => (
                    <option key={size} value={size}>
                      {size}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            {pagination.total > pagination.pageSize ? (
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  disabled={pagination.page <= 1}
                  onClick={() => pagination.onPageChange(pagination.page - 1)}
                  className={pageButtonClass}
                >
                  {t('table.prev')}
                </button>
                <span className="inline-flex h-7 min-w-7 items-center justify-center rounded-md bg-admin-primary px-2 text-xs font-medium text-admin-on-primary tabular-nums">
                  {pagination.page}
                </span>
                <span className="px-1 text-xs text-admin-muted tabular-nums">/ {totalPages}</span>
                <button
                  type="button"
                  disabled={pagination.page >= totalPages}
                  onClick={() => pagination.onPageChange(pagination.page + 1)}
                  className={pageButtonClass}
                >
                  {t('table.next')}
                </button>
              </div>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  )
}
