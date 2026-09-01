import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { RESERVATION_STATUS_VALUES } from '@/api/apps/reservationApi'
import {
  listOperationReservations,
  type OperationReservationRow,
} from '@/api/operation/reservationApi'
import { DataTable, type DataTableColumn } from '@/components/admin'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'

function statusClassName(status: string) {
  switch (status) {
    case 'NEW':
      return 'border border-amber-200 bg-amber-50 text-amber-700'
    case 'CONFIRMED':
      return 'border border-sky-200 bg-sky-50 text-sky-700'
    case 'DONE':
      return 'border border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'CANCELLED':
      return 'border border-slate-200 bg-slate-100 text-slate-600'
    default:
      return 'border border-slate-200 bg-slate-50 text-slate-500'
  }
}

export function OperationReservationsPage() {
  const { t } = useTranslation('operation')
  const [filters, setFilters] = useState({ pageNum: 1, pageSize: 20, status: '' })
  const [draftStatus, setDraftStatus] = useState('')

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['operation', 'reservations', filters],
    queryFn: () =>
      listOperationReservations({
        pageNum: filters.pageNum,
        pageSize: filters.pageSize,
        status: filters.status || undefined,
      }),
  })

  const records = data?.records ?? []
  const total = data?.total ?? 0

  const columns = useMemo<DataTableColumn<OperationReservationRow>[]>(
    () => [
      {
        id: 'site',
        header: t('reservations.columns.site'),
        cell: (item) => (
          <div>
            <p className="font-medium text-slate-900">{item.siteName}</p>
            <p className="mt-1 text-xs text-slate-500">{item.siteCode}</p>
          </div>
        ),
      },
      {
        id: 'customer',
        header: t('reservations.columns.customer'),
        cell: (item) => (
          <div>
            <p className="font-medium text-slate-900">{item.customerName}</p>
            <p className="mt-1 text-xs text-slate-500">{item.phone}</p>
          </div>
        ),
      },
      {
        id: 'serviceType',
        header: t('reservations.columns.serviceType'),
        cell: (item) => item.serviceType,
      },
      {
        id: 'reservedAt',
        header: t('reservations.columns.reservedAt'),
        cell: (item) => new Date(item.reservedAt).toLocaleString(),
      },
      {
        id: 'status',
        header: t('reservations.columns.status'),
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
        id: 'actions',
        header: t('reservations.columns.actions'),
        cell: (item) => (
          <Link
            to={ROUTES.apps.siteReservations(item.siteId)}
            className="text-xs font-medium text-slate-700 underline-offset-2 hover:underline"
          >
            {t('reservations.actions.manage')}
          </Link>
        ),
      },
    ],
    [t],
  )

  return (
    <section>
      <PageHeader title={t('reservations.title')} description={t('reservations.description')} />

      <p className="mb-3 text-xs text-slate-500">{t('reservations.aggregateNotice')}</p>

      <div className="mb-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="grid gap-3 md:grid-cols-3">
          <label className="space-y-1.5 text-sm">
            <span className="font-medium text-slate-700">{t('reservations.filters.status')}</span>
            <select
              value={draftStatus}
              onChange={(event) => setDraftStatus(event.target.value)}
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-slate-200"
            >
              <option value="">{t('reservations.filters.allStatuses')}</option>
              {RESERVATION_STATUS_VALUES.map((status) => (
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
                  status: draftStatus,
                }))
              }
              className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
            >
              {t('reservations.filters.search')}
            </button>
            <button
              type="button"
              onClick={() => {
                setDraftStatus('')
                setFilters({ pageNum: 1, pageSize: filters.pageSize, status: '' })
              }}
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
            >
              {t('reservations.filters.reset')}
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
        emptyMessage={t('reservations.empty')}
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
