import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { MEDIA_STATUS } from '@/api/apps/mediaApi'
import { listOperationMedia, type OperationMediaRow } from '@/api/operation/fileApi'
import { AdminPageHeader, DataTable, type DataTableColumn } from '@/components/admin'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'

function formatFileSize(bytes: number) {
  if (!Number.isFinite(bytes) || bytes < 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function statusClassName(status: string) {
  if (status === MEDIA_STATUS.READY) return 'border-success/35 bg-success-muted text-success-emphasis'
  if (status === MEDIA_STATUS.FAILED) return 'border-danger/35 bg-danger-muted text-danger-emphasis'
  return 'border-warning/35 bg-warning-muted text-warning-emphasis'
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
            <p className="font-medium text-foreground">{item.agentName}</p>
            <p className="mt-1 text-xs text-muted-foreground">{item.agentCode}</p>
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
            to={ROUTES.apps.agentFiles(item.agentId)}
            className="text-xs font-medium text-foreground underline-offset-2 hover:underline"
          >
            {t('files.actions.manage')}
          </Link>
        ),
      },
    ],
    [t],
  )

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('files.title')} description={t('files.description')} />

      <p className="mb-3 text-xs text-muted-foreground">{t('files.aggregateNotice')}</p>

      <DataTable
        columns={columns}
        data={records}
        rowKey={(item) => `${item.agentId}-${item.id}`}
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
