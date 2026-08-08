import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'
import { toast } from 'sonner'

import {
  RESERVATION_STATUS,
  RESERVATION_STATUS_VALUES,
  getReservationStatusOptions,
  reservationApi,
  type CreateReservationInput,
  type ReservationListItem,
  type ReservationListQuery,
} from '@/api'
import { DataTable, type DataTableColumn } from '@/components/admin'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import type { SiteWorkspaceOutletContext } from './siteWorkspaceLayout'

interface CreateFormState {
  customerName: string
  phone: string
  serviceType: string
  reservedAt: string
  note: string
}

function emptyCreateForm(): CreateFormState {
  return {
    customerName: '',
    phone: '',
    serviceType: '',
    reservedAt: '',
    note: '',
  }
}

function statusClassName(status: string) {
  switch (status) {
    case RESERVATION_STATUS.NEW:
      return 'border border-amber-200 bg-amber-50 text-amber-700'
    case RESERVATION_STATUS.CONFIRMED:
      return 'border border-sky-200 bg-sky-50 text-sky-700'
    case RESERVATION_STATUS.DONE:
      return 'border border-emerald-200 bg-emerald-50 text-emerald-700'
    case RESERVATION_STATUS.CANCELLED:
      return 'border border-slate-200 bg-slate-100 text-slate-600'
    default:
      return 'border border-slate-200 bg-slate-50 text-slate-500'
  }
}

/** datetime-local → ISO OffsetDateTime（UTC Z），对齐后端 OffsetDateTime。 */
function toOffsetDateTime(localValue: string): string | null {
  const trimmed = localValue.trim()
  if (!trimmed) return null
  const date = new Date(trimmed)
  if (Number.isNaN(date.getTime())) return null
  return date.toISOString()
}

export function SiteReservationsPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<SiteWorkspaceOutletContext>()
  const queryClient = useQueryClient()

  const canCreate = useHasPermission(PERMISSION_CODES.reservationCreate)
  const canUpdate = useHasPermission(PERMISSION_CODES.reservationUpdate)
  const canDelete = useHasPermission(PERMISSION_CODES.reservationDelete)

  const [filters, setFilters] = useState<ReservationListQuery>({ pageNum: 1, pageSize: 20 })
  const [draftStatus, setDraftStatus] = useState('')
  const [createForm, setCreateForm] = useState<CreateFormState | null>(null)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', site.id, 'reservations', filters],
    queryFn: () => reservationApi.listReservations(site.id, filters),
  })

  const records = data?.records ?? []
  const total = data?.total ?? 0

  const invalidateReservations = () =>
    void queryClient.invalidateQueries({ queryKey: ['sites', site.id, 'reservations'] })

  const createMutation = useMutation({
    mutationFn: (input: CreateReservationInput) => reservationApi.createReservation(site.id, input),
    onSuccess: () => {
      toast.success(t('reservationManagement.messages.created'))
      setCreateForm(null)
      invalidateReservations()
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status, version }: { id: number; status: string; version: number }) =>
      reservationApi.updateReservationStatus(site.id, id, { status, version }),
    onSuccess: () => {
      toast.success(t('reservationManagement.messages.statusUpdated'))
      invalidateReservations()
    },
    onError: (err: Error) => {
      toast.error(err.message)
      invalidateReservations()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => reservationApi.deleteReservation(site.id, id),
    onSuccess: () => {
      toast.success(t('reservationManagement.messages.deleted'))
      invalidateReservations()
    },
    onError: (err: Error) => toast.error(err.message),
  })

  function submitCreate() {
    if (!createForm) return
    const customerName = createForm.customerName.trim()
    const phone = createForm.phone.trim()
    const serviceType = createForm.serviceType.trim()
    const reservedAt = toOffsetDateTime(createForm.reservedAt)
    const note = createForm.note.trim()

    if (!customerName) {
      toast.error(t('reservationManagement.messages.customerNameRequired'))
      return
    }
    if (!phone) {
      toast.error(t('reservationManagement.messages.phoneRequired'))
      return
    }
    if (!serviceType) {
      toast.error(t('reservationManagement.messages.serviceTypeRequired'))
      return
    }
    if (!reservedAt) {
      toast.error(t('reservationManagement.messages.reservedAtRequired'))
      return
    }

    createMutation.mutate({
      customerName,
      phone,
      serviceType,
      reservedAt,
      note: note || null,
    })
  }

  function applyFilters() {
    setFilters((prev) => ({
      ...prev,
      pageNum: 1,
      status: draftStatus || undefined,
    }))
  }

  function resetFilters() {
    setDraftStatus('')
    setFilters({ pageNum: 1, pageSize: filters.pageSize ?? 20 })
  }

  const columns = useMemo<DataTableColumn<ReservationListItem>[]>(
    () => [
      {
        id: 'customer',
        header: t('reservationManagement.columns.customer'),
        cell: (item) => (
          <div>
            <p className="font-medium text-slate-900">{item.customerName}</p>
            <p className="mt-1 text-xs text-slate-500">{item.phone}</p>
          </div>
        ),
      },
      {
        id: 'serviceType',
        header: t('reservationManagement.columns.serviceType'),
        cell: (item) => item.serviceType,
      },
      {
        id: 'reservedAt',
        header: t('reservationManagement.columns.reservedAt'),
        cell: (item) => new Date(item.reservedAt).toLocaleString(),
      },
      {
        id: 'status',
        header: t('reservationManagement.columns.status'),
        cell: (item) => {
          const options = getReservationStatusOptions(item.status)
          const canChange = canUpdate && options.length > 1

          if (canChange) {
            return (
              <select
                value={item.status}
                disabled={statusMutation.isPending}
                onChange={(event) => {
                  const next = event.target.value
                  if (next === item.status) return
                  statusMutation.mutate({ id: item.id, status: next, version: item.version })
                }}
                className={cn(
                  'rounded-md border px-2 py-1 text-xs font-medium outline-none',
                  statusClassName(item.status),
                )}
              >
                {options.map((status) => (
                  <option key={status} value={status}>
                    {t(`reservationManagement.statusLabels.${status}`)}
                  </option>
                ))}
              </select>
            )
          }

          return (
            <span
              className={cn(
                'inline-flex rounded-md px-2 py-1 text-xs font-medium',
                statusClassName(item.status),
              )}
            >
              {t(`reservationManagement.statusLabels.${item.status}`, {
                defaultValue: item.status,
              })}
            </span>
          )
        },
      },
      {
        id: 'createdAt',
        header: t('reservationManagement.columns.createdAt'),
        cell: (item) => new Date(item.createdAt).toLocaleString(),
      },
      {
        id: 'actions',
        header: t('reservationManagement.columns.actions'),
        cell: (item) =>
          canDelete ? (
            <button
              type="button"
              disabled={deleteMutation.isPending}
              onClick={() => {
                if (
                  !window.confirm(
                    t('reservationManagement.messages.confirmDelete', {
                      name: item.customerName,
                    }),
                  )
                ) {
                  return
                }
                deleteMutation.mutate(item.id)
              }}
              className="rounded-md border border-rose-200 px-2.5 py-1.5 text-xs font-medium text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {t('reservationManagement.actions.delete')}
            </button>
          ) : (
            <span className="text-xs text-slate-400">—</span>
          ),
      },
    ],
    [canDelete, canUpdate, deleteMutation, statusMutation, t],
  )

  return (
    <section>
      <PageHeader
        title={t('workspace.nav.reservations')}
        description={t('reservationManagement.description')}
        action={
          <PermissionGate code={PERMISSION_CODES.reservationCreate}>
            <button
              type="button"
              onClick={() => setCreateForm(emptyCreateForm())}
              className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-slate-800"
            >
              <Plus className="h-4 w-4" />
              {t('reservationManagement.actions.create')}
            </button>
          </PermissionGate>
        }
      />

      <div className="mb-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="grid gap-3 md:grid-cols-3">
          <label className="space-y-1.5 text-sm">
            <span className="font-medium text-slate-700">
              {t('reservationManagement.filters.status')}
            </span>
            <select
              value={draftStatus}
              onChange={(event) => setDraftStatus(event.target.value)}
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-slate-200"
            >
              <option value="">{t('reservationManagement.filters.allStatuses')}</option>
              {RESERVATION_STATUS_VALUES.map((status) => (
                <option key={status} value={status}>
                  {t(`reservationManagement.statusLabels.${status}`)}
                </option>
              ))}
            </select>
          </label>
          <div className="flex items-end gap-2 md:col-span-2">
            <button
              type="button"
              onClick={applyFilters}
              className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
            >
              {t('reservationManagement.filters.search')}
            </button>
            <button
              type="button"
              onClick={resetFilters}
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
            >
              {t('reservationManagement.filters.reset')}
            </button>
          </div>
        </div>
      </div>

      {createForm ? (
        <article className="mb-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-3 flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold text-slate-900">
              {t('reservationManagement.editor.createTitle')}
            </h2>
            <button
              type="button"
              onClick={() => setCreateForm(null)}
              className="rounded-md border border-slate-200 p-1.5 text-slate-500 transition hover:bg-slate-50"
              aria-label={t('reservationManagement.actions.cancel')}
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-slate-700">
                {t('reservationManagement.fields.customerName')}
              </span>
              <input
                value={createForm.customerName}
                onChange={(event) =>
                  setCreateForm((prev) =>
                    prev ? { ...prev, customerName: event.target.value } : prev,
                  )
                }
                maxLength={128}
                className="w-full rounded-lg border border-slate-200 px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-slate-200"
              />
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-slate-700">
                {t('reservationManagement.fields.phone')}
              </span>
              <input
                value={createForm.phone}
                onChange={(event) =>
                  setCreateForm((prev) => (prev ? { ...prev, phone: event.target.value } : prev))
                }
                maxLength={32}
                className="w-full rounded-lg border border-slate-200 px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-slate-200"
              />
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-slate-700">
                {t('reservationManagement.fields.serviceType')}
              </span>
              <input
                value={createForm.serviceType}
                onChange={(event) =>
                  setCreateForm((prev) =>
                    prev ? { ...prev, serviceType: event.target.value } : prev,
                  )
                }
                maxLength={128}
                className="w-full rounded-lg border border-slate-200 px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-slate-200"
              />
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-slate-700">
                {t('reservationManagement.fields.reservedAt')}
              </span>
              <input
                type="datetime-local"
                value={createForm.reservedAt}
                onChange={(event) =>
                  setCreateForm((prev) =>
                    prev ? { ...prev, reservedAt: event.target.value } : prev,
                  )
                }
                className="w-full rounded-lg border border-slate-200 px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-slate-200"
              />
            </label>

            <label className="space-y-1.5 text-sm md:col-span-2">
              <span className="font-medium text-slate-700">
                {t('reservationManagement.fields.note')}
              </span>
              <textarea
                value={createForm.note}
                onChange={(event) =>
                  setCreateForm((prev) => (prev ? { ...prev, note: event.target.value } : prev))
                }
                rows={3}
                maxLength={20000}
                className="w-full rounded-lg border border-slate-200 px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-slate-200"
              />
            </label>
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              disabled={createMutation.isPending || !canCreate}
              onClick={submitCreate}
              className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {createMutation.isPending
                ? t('reservationManagement.actions.saving')
                : t('reservationManagement.actions.save')}
            </button>
            <button
              type="button"
              onClick={() => setCreateForm(null)}
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
            >
              {t('reservationManagement.actions.cancel')}
            </button>
          </div>
        </article>
      ) : null}

      <DataTable
        columns={columns}
        data={records}
        rowKey={(item) => item.id}
        isLoading={isPending}
        isError={isError}
        errorMessage={error instanceof Error ? error.message : undefined}
        emptyMessage={t('reservationManagement.empty')}
        onRetry={() => void refetch()}
        pagination={{
          page: filters.pageNum ?? 1,
          pageSize: filters.pageSize ?? 20,
          total,
          onPageChange: (page) => setFilters((prev) => ({ ...prev, pageNum: page })),
        }}
      />
    </section>
  )
}
