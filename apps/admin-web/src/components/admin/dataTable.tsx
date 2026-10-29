import { AlertCircle, RefreshCw } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

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

  return (
    <div className={cn('overflow-hidden rounded-xl border border-admin bg-admin-surface shadow-sm', className)}>
      <div className="overflow-x-auto">
        <table className="min-w-full text-left text-sm">
          <thead className="bg-admin-surface-muted text-xs uppercase tracking-wide text-admin-muted">
            <tr>
              {columns.map((column) => (
                <th key={column.id} className={cn('px-4 py-3', column.headerClassName)}>
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-[rgb(var(--border))]">
            {isLoading ? (
              <tr>
                <td colSpan={colSpan} className="px-4 py-10 text-center text-admin-muted">
                  {t('table.loading')}
                </td>
              </tr>
            ) : isError ? (
              <tr>
                <td colSpan={colSpan} className="px-4 py-10">
                  <div className="flex flex-col items-center justify-center gap-2 text-center text-admin-muted">
                    <AlertCircle className="h-6 w-6 text-rose-500" />
                    <p className="text-sm">{errorMessage ?? t('table.error')}</p>
                    {onRetry ? (
                      <button
                        type="button"
                        onClick={onRetry}
                        className="inline-flex items-center gap-1.5 rounded-lg border border-admin px-3 py-1.5 text-xs font-medium text-admin transition hover:bg-admin-surface-muted"
                      >
                        <RefreshCw className="h-3.5 w-3.5" />
                        {t('table.retry')}
                      </button>
                    ) : null}
                  </div>
                </td>
              </tr>
            ) : data.length === 0 ? (
              <tr>
                <td colSpan={colSpan} className="px-4 py-10 text-center text-admin-muted">
                  {emptyMessage ?? t('table.empty')}
                </td>
              </tr>
            ) : (
              data.map((row) => (
                <tr key={rowKey(row)}>
                  {columns.map((column) => (
                    <td key={column.id} className={cn('px-4 align-top text-admin admin-table-row', column.className)}>
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
        <div className="flex items-center justify-between border-t border-admin px-4 py-3 text-sm text-admin-muted">
          <span>{t('table.paginationTotal', { total: pagination.total })}</span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={pagination.page <= 1}
              onClick={() => pagination.onPageChange(pagination.page - 1)}
              className="rounded-md border border-admin px-2.5 py-1 text-xs font-medium text-admin disabled:opacity-50"
            >
              {t('table.prev')}
            </button>
            <span className="text-xs text-admin-muted">
              {pagination.page} / {totalPages}
            </span>
            <button
              type="button"
              disabled={pagination.page >= totalPages}
              onClick={() => pagination.onPageChange(pagination.page + 1)}
              className="rounded-md border border-admin px-2.5 py-1 text-xs font-medium text-admin disabled:opacity-50"
            >
              {t('table.next')}
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}
