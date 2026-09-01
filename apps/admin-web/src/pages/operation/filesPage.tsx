import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { MEDIA_STATUS } from '@/api/apps/mediaApi'
import { listOperationMedia, type OperationMediaRow } from '@/api/operation/fileApi'
import { DataTable, type DataTableColumn } from '@/components/admin'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'

function formatFileSize(bytes: number) {
  if (!Number.isFinite(bytes) || bytes < 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function statusClassName(status: string) {
  if (status === MEDIA_STATUS.READY) return 'border-emerald-200 bg-emerald-50 text-emerald-700'
  if (status === MEDIA_STATUS.FAILED) return 'border-rose-200 bg-rose-50 text-rose-700'
  return 'border-amber-200 bg-amber-50 text-amber-700'
}

export function OperationFilesPage() {
  const { t } = useTranslation('operation')
  const [filters, setFilters] = useState({ pageNum: 1, pageSize: 20 })

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['operation', 'files', filters],
    queryFn: () =>
      listOperationMedia({
        pageNum: filters.pageNum,
        pageSize: filters.pageSize,
      }),
  })

  const records = data?.records ?? []
  const total = data?.total ?? 0

  const columns = useMemo<DataTableColumn<OperationMediaRow>[]>(
    () => [
      {
        id: 'site',
        header: t('files.columns.site'),
        cell: (item) => (
          <div>
            <p className="font-medium text-slate-900">{item.siteName}</p>
            <p className="mt-1 text-xs text-slate-500">{item.siteCode}</p>
          </div>
        ),
      },
      {
        id: 'filename',
        header: t('files.columns.filename'),
        cell: (item) => item.originalFilename,
      },
      {
        id: 'contentType',
        header: t('files.columns.contentType'),
        cell: (item) => item.contentType || '—',
      },
      {
        id: 'fileSize',
        header: t('files.columns.fileSize'),
        cell: (item) => formatFileSize(item.fileSize),
      },
      {
        id: 'status',
        header: t('files.columns.status'),
        cell: (item) => (
          <span
            className={cn(
              'inline-flex rounded-md border px-2 py-1 text-xs font-medium',
              statusClassName(item.status),
            )}
          >
            {item.status}
          </span>
        ),
      },
      {
        id: 'createdAt',
        header: t('files.columns.createdAt'),
        cell: (item) => new Date(item.createdAt).toLocaleString(),
      },
      {
        id: 'actions',
        header: t('files.columns.actions'),
        cell: (item) => (
          <Link
            to={ROUTES.apps.siteFiles(item.siteId)}
            className="text-xs font-medium text-slate-700 underline-offset-2 hover:underline"
          >
            {t('files.actions.manage')}
          </Link>
        ),
      },
    ],
    [t],
  )

  return (
    <section>
      <PageHeader title={t('files.title')} description={t('files.description')} />

      <p className="mb-3 text-xs text-slate-500">{t('files.aggregateNotice')}</p>

      <DataTable
        columns={columns}
        data={records}
        rowKey={(item) => `${item.siteId}-${item.id}`}
        isLoading={isPending}
        isError={isError}
        errorMessage={error instanceof Error ? error.message : undefined}
        emptyMessage={t('files.empty')}
        onRetry={() => void refetch()}
        pagination={{
          page: filters.pageNum,
          pageSize: filters.pageSize,
          total,
          onPageChange: (page) => setFilters((prev) => ({ ...prev, pageNum: page })),
        }}
      />
    </section>
  )
}
