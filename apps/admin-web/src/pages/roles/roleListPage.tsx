import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { rbacApi } from '@/api/rbacApi'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { isSuperAdminRole, PermissionGate, useHasPermission } from '@/router/permission'
import { ROUTES } from '@/router/config/routePaths'
import { PERMISSION_CODES, ROLE_STATUS, type RoleListQuery } from '@/types/rbac'

function statusClassName(status: string) {
  return status === ROLE_STATUS.ACTIVE
    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
    : 'border-slate-200 bg-slate-50 text-slate-600'
}

export function RoleListPage() {
  const { t } = useTranslation('roles')
  const { t: tc } = useTranslation('common')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const canCreate = useHasPermission(PERMISSION_CODES.roleCreate)
  const canUpdate = useHasPermission(PERMISSION_CODES.roleUpdate)
  const canDelete = useHasPermission(PERMISSION_CODES.roleDelete)
  const canAssign = useHasPermission(PERMISSION_CODES.rolePermissionView)

  const [filters, setFilters] = useState<RoleListQuery>({})
  const [draft, setDraft] = useState<RoleListQuery>({})

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['roles', filters],
    queryFn: () => rbacApi.listRoles(filters),
  })

  const roles = data ?? []

  const deleteMutation = useMutation({
    mutationFn: (id: number) => rbacApi.deleteRole(id),
    onSuccess: () => {
      toast.success(t('list.messages.deleted'))
      void queryClient.invalidateQueries({ queryKey: ['roles'] })
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const statusMutation = useMutation({
    mutationFn: ({
      id,
      roleName,
      description,
      status,
    }: {
      id: number
      roleName: string
      description?: string
      status: string
    }) => rbacApi.updateRole(id, { roleName, description, status }),
    onSuccess: () => {
      toast.success(t('list.messages.statusUpdated'))
      void queryClient.invalidateQueries({ queryKey: ['roles'] })
    },
    onError: (err: Error) => toast.error(err.message),
  })

  function applyFilters() {
    setFilters({ ...draft })
  }

  function resetFilters() {
    setDraft({})
    setFilters({})
  }

  function handleDelete(id: number, roleCode: string) {
    if (isSuperAdminRole(roleCode)) return
    if (!window.confirm(t('list.messages.confirmDelete', { roleCode }))) return
    deleteMutation.mutate(id)
  }

  function toggleStatus(role: {
    id: number
    roleCode: string
    roleName: string
    description: string | null
    status: string
  }) {
    if (isSuperAdminRole(role.roleCode)) return
    const nextStatus = role.status === ROLE_STATUS.ACTIVE ? ROLE_STATUS.DISABLED : ROLE_STATUS.ACTIVE
    statusMutation.mutate({
      id: role.id,
      roleName: role.roleName,
      description: role.description ?? undefined,
      status: nextStatus,
    })
  }

  return (
    <section>
      <PageHeader
        title={t('list.title')}
        description={t('list.description')}
        action={
          <PermissionGate code={PERMISSION_CODES.roleCreate}>
            <button
              type="button"
              onClick={() => navigate(ROUTES.system.roleCreate)}
              className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-slate-800"
            >
              <Plus className="h-4 w-4" />
              {t('list.create')}
            </button>
          </PermissionGate>
        }
      />

      <div className="mb-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="grid gap-3 md:grid-cols-4">
          <input
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            placeholder={t('list.filters.roleCode')}
            value={draft.roleCode ?? ''}
            onChange={(event) => setDraft((prev) => ({ ...prev, roleCode: event.target.value || undefined }))}
          />
          <input
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            placeholder={t('list.filters.roleName')}
            value={draft.roleName ?? ''}
            onChange={(event) => setDraft((prev) => ({ ...prev, roleName: event.target.value || undefined }))}
          />
          <select
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            value={draft.status ?? ''}
            onChange={(event) => setDraft((prev) => ({ ...prev, status: event.target.value || undefined }))}
          >
            <option value="">{t('list.filters.allStatus')}</option>
            <option value={ROLE_STATUS.ACTIVE}>{tc('enum.status.ACTIVE')}</option>
            <option value={ROLE_STATUS.DISABLED}>{tc('enum.status.DISABLED')}</option>
          </select>
          <div className="flex gap-2">
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

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">{t('list.columns.roleCode')}</th>
                <th className="px-4 py-3">{t('list.columns.roleName')}</th>
                <th className="px-4 py-3">{t('list.columns.status')}</th>
                <th className="px-4 py-3">{t('list.columns.description')}</th>
                <th className="px-4 py-3">{t('list.columns.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {isPending ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                    {t('list.loading')}
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-rose-700">
                    <p>{error instanceof Error ? error.message : t('list.error')}</p>
                    <button type="button" onClick={() => void refetch()} className="mt-2 underline">
                      {t('list.retry')}
                    </button>
                  </td>
                </tr>
              ) : roles.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-4 py-8 text-center text-slate-500">
                    {t('list.empty')}
                  </td>
                </tr>
              ) : (
                roles.map((role) => {
                  const protectedRole = isSuperAdminRole(role.roleCode)
                  return (
                    <tr key={role.id}>
                      <td className="px-4 py-3 font-medium text-slate-900">{role.roleCode}</td>
                      <td className="px-4 py-3 text-slate-700">{role.roleName}</td>
                      <td className="px-4 py-3">
                        <span
                          className={cn(
                            'inline-flex rounded-md border px-2 py-1 text-xs font-medium',
                            statusClassName(role.status),
                          )}
                        >
                          {tc(`enum.status.${role.status}`, { defaultValue: role.status })}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-slate-600">{role.description || '—'}</td>
                      <td className="px-4 py-3">
                        <div className="flex flex-wrap gap-2">
                          {canUpdate && !protectedRole ? (
                            <button
                              type="button"
                              onClick={() => navigate(ROUTES.system.roleEdit(role.id))}
                              className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs text-slate-700 hover:bg-slate-50"
                            >
                              <Pencil className="h-3.5 w-3.5" />
                              {t('list.actions.edit')}
                            </button>
                          ) : null}
                          {canUpdate && !protectedRole ? (
                            <button
                              type="button"
                              onClick={() => toggleStatus(role)}
                              className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs text-slate-700 hover:bg-slate-50"
                            >
                              {role.status === ROLE_STATUS.ACTIVE
                                ? t('list.actions.disable')
                                : t('list.actions.enable')}
                            </button>
                          ) : null}
                          {canAssign && !protectedRole ? (
                            <Link
                              to={ROUTES.system.rolePermissions(role.id)}
                              className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs text-slate-700 hover:bg-slate-50"
                            >
                              <KeyRound className="h-3.5 w-3.5" />
                              {t('list.actions.assignPermissions')}
                            </Link>
                          ) : null}
                          {canDelete && !protectedRole ? (
                            <button
                              type="button"
                              onClick={() => handleDelete(role.id, role.roleCode)}
                              className="inline-flex items-center gap-1 rounded-md border border-rose-200 px-2 py-1 text-xs text-rose-700 hover:bg-rose-50"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                              {t('list.actions.delete')}
                            </button>
                          ) : null}
                          {protectedRole ? (
                            <span className="text-xs text-slate-400">{t('list.badges.protected')}</span>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
      </div>

      {canCreate ? <p className="mt-3 text-xs text-slate-500">{t('list.permissionHint')}</p> : null}
    </section>
  )
}
