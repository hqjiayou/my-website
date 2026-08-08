import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { rbacApi } from '@/api/rbacApi'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import { PERMISSION_CODES, USER_STATUS, type UserListQuery } from '@/types/rbac'

function statusClassName(status: string) {
  return status === USER_STATUS.ACTIVE
    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
    : 'border-slate-200 bg-slate-50 text-slate-600'
}

export function UserListPage() {
  const { t } = useTranslation('users')
  const { t: tc } = useTranslation('common')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const authUser = useAuthStore((state) => state.user)
  const meUserCode = authUser?.userCode

  const canCreate = useHasPermission(PERMISSION_CODES.userCreate)
  const canUpdate = useHasPermission(PERMISSION_CODES.userUpdate)
  const canDelete = useHasPermission(PERMISSION_CODES.userDelete)
  const canStatus = useHasPermission(PERMISSION_CODES.userStatus)
  const canAssign = useHasPermission(PERMISSION_CODES.userRoleView)

  const [filters, setFilters] = useState<UserListQuery>({})
  const [draft, setDraft] = useState<UserListQuery>({})

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['users', filters],
    queryFn: () => rbacApi.listUsers(filters),
  })
  const users = data ?? []

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) => rbacApi.updateUserStatus(id, { status }),
    onSuccess: () => {
      toast.success(t('list.messages.statusUpdated'))
      void queryClient.invalidateQueries({ queryKey: ['users'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => rbacApi.deleteUser(id),
    onSuccess: () => {
      toast.success(t('list.messages.deleted'))
      void queryClient.invalidateQueries({ queryKey: ['users'] })
    },
  })

  function applyFilters() {
    setFilters({ ...draft })
  }

  function resetFilters() {
    setDraft({})
    setFilters({})
  }

  function handleDelete(id: number, username: string) {
    if (!window.confirm(t('list.messages.confirmDelete', { username }))) return
    deleteMutation.mutate(id)
  }

  function toggleStatus(id: number, status: string) {
    const nextStatus = status === USER_STATUS.ACTIVE ? USER_STATUS.DISABLED : USER_STATUS.ACTIVE
    statusMutation.mutate({ id, status: nextStatus })
  }

  return (
    <section>
      <PageHeader
        title={t('list.title')}
        description={t('list.description')}
        action={
          <PermissionGate code={PERMISSION_CODES.userCreate}>
            <button
              type="button"
              onClick={() => navigate(ROUTES.system.userCreate)}
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
            placeholder={t('list.filters.username')}
            value={draft.username ?? ''}
            onChange={(event) => setDraft((prev) => ({ ...prev, username: event.target.value || undefined }))}
          />
          <input
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            placeholder={t('list.filters.displayName')}
            value={draft.displayName ?? ''}
            onChange={(event) => setDraft((prev) => ({ ...prev, displayName: event.target.value || undefined }))}
          />
          <select
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            value={draft.status ?? ''}
            onChange={(event) => setDraft((prev) => ({ ...prev, status: event.target.value || undefined }))}
          >
            <option value="">{t('list.filters.allStatus')}</option>
            <option value={USER_STATUS.ACTIVE}>{tc('enum.status.ACTIVE')}</option>
            <option value={USER_STATUS.DISABLED}>{tc('enum.status.DISABLED')}</option>
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
                <th className="px-4 py-3">{t('list.columns.username')}</th>
                <th className="px-4 py-3">{t('list.columns.displayName')}</th>
                <th className="px-4 py-3">{t('list.columns.email')}</th>
                <th className="px-4 py-3">{t('list.columns.phone')}</th>
                <th className="px-4 py-3">{t('list.columns.status')}</th>
                <th className="px-4 py-3">{t('list.columns.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {isPending ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                    {t('list.loading')}
                  </td>
                </tr>
              ) : isError ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-rose-700">
                    <p>{error instanceof Error ? error.message : t('list.error')}</p>
                    <button type="button" onClick={() => void refetch()} className="mt-2 underline">
                      {t('list.retry')}
                    </button>
                  </td>
                </tr>
              ) : users.length === 0 ? (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-slate-500">
                    {t('list.empty')}
                  </td>
                </tr>
              ) : (
                users.map((user) => {
                  const isSelf = Boolean(meUserCode && user.userCode && meUserCode === user.userCode)
                  const protectedUser = user.protectedUser
                  const dangerLocked = isSelf || protectedUser
                  return (
                    <tr key={user.id}>
                      <td className="px-4 py-3 font-medium text-slate-900">
                        <div className="flex flex-wrap items-center gap-2">
                          <span>{user.username}</span>
                          {isSelf ? (
                            <span className="rounded-md border border-sky-200 bg-sky-50 px-1.5 py-0.5 text-xs text-sky-700">
                              {t('list.badges.self')}
                            </span>
                          ) : null}
                          {protectedUser ? (
                            <span className="rounded-md border border-amber-200 bg-amber-50 px-1.5 py-0.5 text-xs text-amber-700">
                              {t('list.badges.protected')}
                            </span>
                          ) : null}
                        </div>
                      </td>
                      <td className="px-4 py-3 text-slate-700">{user.displayName || '—'}</td>
                      <td className="px-4 py-3 text-slate-600">{user.email || '—'}</td>
                      <td className="px-4 py-3 text-slate-600">{user.phone || '—'}</td>
                      <td className="px-4 py-3">
                        <span className={cn('inline-flex rounded-md border px-2 py-1 text-xs font-medium', statusClassName(user.status))}>
                          {tc(`enum.status.${user.status}`, { defaultValue: user.status })}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex flex-wrap gap-2">
                          {canUpdate ? (
                            <button
                              type="button"
                              onClick={() => navigate(ROUTES.system.userEdit(user.id))}
                              className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs text-slate-700 hover:bg-slate-50"
                            >
                              <Pencil className="h-3.5 w-3.5" />
                              {t('list.actions.edit')}
                            </button>
                          ) : null}
                          {canStatus && !dangerLocked ? (
                            <button
                              type="button"
                              onClick={() => toggleStatus(user.id, user.status)}
                              disabled={statusMutation.isPending}
                              className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs text-slate-700 hover:bg-slate-50 disabled:opacity-60"
                            >
                              {user.status === USER_STATUS.ACTIVE ? t('list.actions.disable') : t('list.actions.enable')}
                            </button>
                          ) : null}
                          {canAssign ? (
                            <Link
                              to={ROUTES.system.userRoles(user.id)}
                              className="inline-flex items-center gap-1 rounded-md border border-slate-200 px-2 py-1 text-xs text-slate-700 hover:bg-slate-50"
                            >
                              <KeyRound className="h-3.5 w-3.5" />
                              {t('list.actions.assignRoles')}
                            </Link>
                          ) : null}
                          {canDelete && !dangerLocked ? (
                            <button
                              type="button"
                              onClick={() => handleDelete(user.id, user.username)}
                              disabled={deleteMutation.isPending}
                              className="inline-flex items-center gap-1 rounded-md border border-rose-200 px-2 py-1 text-xs text-rose-700 hover:bg-rose-50 disabled:opacity-60"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                              {t('list.actions.delete')}
                            </button>
                          ) : null}
                          {dangerLocked ? (
                            <span className="text-xs text-slate-400">
                              {isSelf ? t('list.messages.restrictedSelf') : t('list.messages.restrictedProtected')}
                            </span>
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
