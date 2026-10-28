import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import {
  agentApi,
  SITE_STATUS,
  SITE_TYPE_PRESETS,
  type AgentListItem,
  type AgentListQuery,
} from '@/api'
import { DataTable, type DataTableColumn } from '@/components/admin'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { ROUTES } from '@/router/config/routePaths'
import { PERMISSION_CODES } from '@/types/rbac'

function statusClassName(status: string) {
  return status === SITE_STATUS.ACTIVE
    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
    : 'border-slate-200 bg-slate-50 text-slate-600'
}

export function SitesPage() {
  const { t } = useTranslation('sites')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const canCreate = useHasPermission(PERMISSION_CODES.agentCreate)
  const canDelete = useHasPermission(PERMISSION_CODES.agentDelete)
  const canStatus = useHasPermission(PERMISSION_CODES.agentStatus)

  const [filters, setFilters] = useState<AgentListQuery>({ pageNum: 1, pageSize: 20 })
  const [draft, setDraft] = useState<{ status?: string; agentType?: string }>({})

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', filters],
    queryFn: () => agentApi.listSites(filters),
  })

  const records = data?.records ?? []
  const total = data?.total ?? 0

  const statusMutation = useMutation({
    mutationFn: ({ id, status, version }: { id: number; status: string; version: number }) =>
      agentApi.updateAgentStatus(id, { status, version }),
    onSuccess: () => {
      toast.success(t('list.messages.statusUpdated'))
      void queryClient.invalidateQueries({ queryKey: ['sites'] })
    },
    onError: (err: Error, variables) => {
      toast.error(err.message)
      // 乐观锁 / 任意 status 失败后刷新 version，避免连点沿用旧 version 持续 409
      void queryClient.invalidateQueries({ queryKey: ['sites'] })
      void queryClient.invalidateQueries({ queryKey: ['sites', variables.id] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => agentApi.deleteSite(id),
    onSuccess: () => {
      toast.success(t('list.messages.deleted'))
      void queryClient.invalidateQueries({ queryKey: ['sites'] })
    },
    onError: (err: Error) => toast.error(err.message),
  })

  function applyFilters() {
    setFilters((prev) => ({
      ...prev,
      pageNum: 1,
      status: draft.status,
      agentType: draft.agentType,
    }))
  }

  function resetFilters() {
    setDraft({})
    setFilters({ pageNum: 1, pageSize: filters.pageSize ?? 20 })
  }

  const columns = useMemo<DataTableColumn<AgentListItem>[]>(
    () => [
      {
        id: 'agentName',
        header: t('list.columns.agentName'),
        cell: (site) => (
          <div>
            <p className="font-medium text-slate-900">{site.agentName}</p>
            <p className="mt-1 text-xs text-slate-500">{site.agentCode}</p>
          </div>
        ),
      },
      {
        id: 'agentType',
        header: t('list.columns.agentType'),
        cell: (site) => t(`agentType.${site.agentType}`, { defaultValue: site.agentType }),
      },
      {
        id: 'domain',
        header: t('list.columns.domain'),
        cell: (site) => site.defaultDomain || '—',
      },
      {
        id: 'status',
        header: t('list.columns.status'),
        cell: (site) => (
          <span className={cn('inline-flex rounded-md border px-2 py-1 text-xs font-medium', statusClassName(site.status))}>
            {t(`status.${site.status}`, { defaultValue: site.status })}
          </span>
        ),
      },
      {
        id: 'updatedAt',
        header: t('list.columns.updatedAt'),
        cell: (site) => new Date(site.updatedAt).toLocaleDateString(),
      },
      {
        id: 'actions',
        header: t('list.columns.actions'),
        cell: (site) => (
          <div className="flex flex-wrap gap-2">
            <Link
              to={ROUTES.apps.agent(site.id)}
              className="rounded-md border border-blue-200 bg-blue-50 px-2.5 py-1.5 text-xs font-medium text-blue-700 transition hover:bg-blue-100"
            >
              {t('list.actions.openWorkspace')}
            </Link>
            <Link
              to={ROUTES.apps.agentBasic(site.id)}
              className="rounded-md border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
            >
              {t('list.actions.basicInfo')}
            </Link>
            {canStatus ? (
              <button
                type="button"
                onClick={() =>
                  statusMutation.mutate({
                    id: site.id,
                    status: site.status === SITE_STATUS.ACTIVE ? SITE_STATUS.DISABLED : SITE_STATUS.ACTIVE,
                    version: site.version,
                  })
                }
                className="rounded-md border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
              >
                {site.status === SITE_STATUS.ACTIVE ? t('list.actions.disable') : t('list.actions.enable')}
              </button>
            ) : null}
            {canDelete ? (
              <button
                type="button"
                onClick={() => {
                  if (!window.confirm(t('list.messages.confirmDelete', { name: site.agentName }))) return
                  deleteMutation.mutate(site.id)
                }}
                className="rounded-md border border-rose-200 px-2.5 py-1.5 text-xs font-medium text-rose-700 transition hover:bg-rose-50"
              >
                {t('list.actions.delete')}
              </button>
            ) : null}
          </div>
        ),
      },
    ],
    [canDelete, canStatus, deleteMutation, statusMutation, t],
  )

  return (
    <section>
      <PageHeader
        title={t('list.title')}
        description={t('list.description')}
        action={
          <PermissionGate code={PERMISSION_CODES.agentCreate}>
            <button
              type="button"
              onClick={() => navigate(ROUTES.apps.agentCreate)}
              className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-slate-800"
            >
              <Plus className="h-4 w-4" />
              <span>{t('list.createSite')}</span>
            </button>
          </PermissionGate>
        }
      />

      <div className="mb-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="grid gap-3 md:grid-cols-4">
          <select
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            value={draft.status ?? ''}
            onChange={(event) => setDraft((prev) => ({ ...prev, status: event.target.value || undefined }))}
          >
            <option value="">{t('list.filters.allStatus')}</option>
            <option value={SITE_STATUS.ACTIVE}>{t('status.ACTIVE')}</option>
            <option value={SITE_STATUS.DISABLED}>{t('status.DISABLED')}</option>
          </select>
          <select
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            value={draft.agentType ?? ''}
            onChange={(event) => setDraft((prev) => ({ ...prev, agentType: event.target.value || undefined }))}
          >
            <option value="">{t('list.filters.allAgentType')}</option>
            {SITE_TYPE_PRESETS.map((value) => (
              <option key={value} value={value}>
                {t(`agentType.${value}`, { defaultValue: value })}
              </option>
            ))}
          </select>
          <div className="flex gap-2 md:col-span-2">
            <button
              type="button"
              onClick={applyFilters}
              className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
            >
              {t('list.filters.search')}
            </button>
            <button
              type="button"
              onClick={resetFilters}
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
            >
              {t('list.filters.reset')}
            </button>
          </div>
        </div>
      </div>

      <DataTable
        columns={columns}
        data={records}
        rowKey={(site) => site.id}
        isLoading={isPending}
        isError={isError}
        errorMessage={error instanceof Error ? error.message : undefined}
        emptyMessage={t('list.empty')}
        onRetry={() => void refetch()}
        pagination={{
          page: filters.pageNum ?? 1,
          pageSize: filters.pageSize ?? 20,
          total,
          onPageChange: (page) => setFilters((prev) => ({ ...prev, pageNum: page })),
        }}
      />

      {!canCreate ? null : <p className="mt-3 text-xs text-slate-500">{t('list.permissionHint')}</p>}
    </section>
  )
}
