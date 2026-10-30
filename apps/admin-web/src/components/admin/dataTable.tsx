import { AlertCircle, Inbox, RefreshCw } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

import { adminSecondaryButtonClass } from './controls'

export interface DataTableColumn<T> {
  id: string
  header: ReactNode
  cell: (row: T) => ReactNode
  className?: string
  headerClassName?: string
}

export interface DataTablePagination {
  page: number
  pageSize: number
  total: number
  onPageChange: (page: number) => void
}

interface DataTableProps<T> {
  columns: DataTableColumn<T>[]
  data: T[]
  rowKey: (row: T) => string | number
  isLoading?: boolean
  isError?: boolean
  errorMessage?: string
  emptyMessage?: string
  onRetry?: () => void
  pagination?: DataTablePagination
  className?: string
}

const pageButtonClass =
  'inline-flex h-7 min-w-7 items-center justify-center rounded-md px-2 text-xs font-medium text-admin transition hover:bg-primary/7 disabled:opacity-50'

export function DataTable<T>({
  columns,
  data,
  rowKey,
  isLoading = false,
  isError = false,
  errorMessage,
  emptyMessage,
  onRetry,
  pagination,
  className,
}: DataTableProps<T>) {
  const { t } = useTranslation('common')
  const colSpan = columns.length
  const totalPages = pagination ? Math.max(1, Math.ceil(pagination.total / pagination.pageSize)) : 1
  const skeletonRows = 4

  return (
    <div className={cn('overflow-hidden rounded-xl border border-admin bg-admin-surface', className)}>
      <div className="overflow-x-auto">
        <table className="min-w-full text-left text-sm">
          <thead className="bg-admin-surface-muted text-xs font-medium uppercase tracking-wide text-admin-muted">
            <tr>
              {columns.map((column) => (
                <th key={column.id} className={cn('h-10 px-4', column.headerClassName)}>
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {isLoading ? (
              Array.from({ length: skeletonRows }, (_, index) => (
                <tr key={`skeleton-${index}`} className="admin-table-row">
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
            ) : data.length === 0 ? (
              <tr>
                <td colSpan={colSpan} className="px-4 py-10">
                  <div className="flex flex-col items-center justify-center gap-2 text-center">
                    <span className="admin-empty-mark">
                      <Inbox className="h-6 w-6" />
                    </span>
                    <p className="text-sm font-medium text-admin">{emptyMessage ?? t('table.empty')}</p>
                  </div>
                </td>
              </tr>
            ) : (
              data.map((row) => (
                <tr key={rowKey(row)} className="admin-table-hover">
                  {columns.map((column) => (
                    <td key={column.id} className={cn('px-4 align-middle text-admin admin-table-row', column.className)}>
                      {column.cell(row)}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {pagination && pagination.total > pagination.pageSize ? (
        <div className="flex items-center justify-between gap-4 border-t border-admin px-4 py-3">
          <span className="text-xs text-admin-muted">{t('table.paginationTotal', { total: pagination.total })}</span>
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
        </div>
      ) : null}
    </div>
  )
}
