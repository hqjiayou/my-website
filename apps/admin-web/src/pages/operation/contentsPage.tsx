import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import {
  CONTENT_STATUS_VALUES,
  CONTENT_TYPE_VALUES,
} from '@/api/apps/contentApi'
import {
  listOperationContents,
  type OperationContentRow,
} from '@/api/operation/contentApi'
import { DataTable, type DataTableColumn } from '@/components/admin'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'

function statusClassName(status: string) {
  switch (status) {
    case 'PUBLISHED':
      return 'border border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'DRAFT':
      return 'border border-amber-200 bg-amber-50 text-amber-700'
    case 'HIDDEN':
      return 'border border-slate-200 bg-slate-100 text-slate-600'
    default:
      return 'border border-slate-200 bg-slate-50 text-slate-500'
  }
}

export function OperationContentsPage() {
  const { t } = useTranslation('operation')
  const [filters, setFilters] = useState({ pageNum: 1, pageSize: 20, type: '', status: '' })
  const [draftType, setDraftType] = useState('')
  const [draftStatus, setDraftStatus] = useState('')

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['operation', 'contents', filters],
    queryFn: () =>
      listOperationContents({
        pageNum: filters.pageNum,
        pageSize: filters.pageSize,
        type: filters.type || undefined,
        status: filters.status || undefined,
      }),
  })

  const records = data?.records ?? []
  const total = data?.total ?? 0

  const columns = useMemo<DataTableColumn<OperationContentRow>[]>(
    () => [
      {
        id: 'site',
        header: t('contents.columns.site'),
        cell: (item) => (
          <div>
            <p className="font-medium text-slate-900">{item.siteName}</p>
            <p className="mt-1 text-xs text-slate-500">{item.siteCode}</p>
          </div>
        ),
      },
      {
        id: 'title',
        header: t('contents.columns.title'),
        cell: (item) => item.title,
      },
      {
        id: 'type',
        header: t('contents.columns.type'),
        cell: (item) => item.type,
      },
      {
        id: 'status',
        header: t('contents.columns.status'),
        cell: (item) => (
          <span
            className={cn(
              'inline-flex rounded-md px-2 py-1 text-xs font-medium',
              statusClassName(item.status),
            )}
          >
            {item.status}
          </span>
        ),
      },
      {
        id: 'updatedAt',
        header: t('contents.columns.updatedAt'),
        cell: (item) => new Date(item.updatedAt).toLocaleString(),
      },
      {
        id: 'actions',
        header: t('contents.columns.actions'),
        cell: (item) => (
          <Link
            to={ROUTES.apps.siteContents(item.siteId)}
            className="text-xs font-medium text-slate-700 underline-offset-2 hover:underline"
          >
            {t('contents.actions.manage')}
          </Link>
        ),
      },
    ],
    [t],
  )

  return (
    <section>
      <PageHeader title={t('contents.title')} description={t('contents.description')} />

      <p className="mb-3 text-xs text-slate-500">{t('contents.aggregateNotice')}</p>

      <div className="mb-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="grid gap-3 md:grid-cols-4">
          <label className="space-y-1.5 text-sm">
            <span className="font-medium text-slate-700">{t('contents.filters.type')}</span>
            <select
              value={draftType}
              onChange={(event) => setDraftType(event.target.value)}
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-slate-200"
            >
              <option value="">{t('contents.filters.allTypes')}</option>
              {CONTENT_TYPE_VALUES.map((type) => (
                <option key={type} value={type}>
                  {type}
                </option>
              ))}
            </select>
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="font-medium text-slate-700">{t('contents.filters.status')}</span>
            <select
              value={draftStatus}
              onChange={(event) => setDraftStatus(event.target.value)}
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-slate-200"
            >
              <option value="">{t('contents.filters.allStatuses')}</option>
              {CONTENT_STATUS_VALUES.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </label>
          <div className="flex items-end gap-2 md:col-span-2">
            <button
              type="button"
              onClick={() =>
                setFilters((prev) => ({
                  ...prev,
                  pageNum: 1,
                  type: draftType,
                  status: draftStatus,
                }))
              }
              className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
            >
              {t('contents.filters.search')}
            </button>
            <button
              type="button"
              onClick={() => {
                setDraftType('')
                setDraftStatus('')
                setFilters({ pageNum: 1, pageSize: filters.pageSize, type: '', status: '' })
              }}
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
            >
              {t('contents.filters.reset')}
            </button>
          </div>
        </div>
      </div>

      <DataTable
        columns={columns}
        data={records}
        rowKey={(item) => `${item.siteId}-${item.id}`}
        isLoading={isPending}
        isError={isError}
        errorMessage={error instanceof Error ? error.message : undefined}
        emptyMessage={t('contents.empty')}
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
