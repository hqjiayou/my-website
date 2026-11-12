import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, Pencil, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { rbacApi } from '@/api/rbacApi'
import { AdminPageHeader, DataTable, FormDialog, Spinner, StatusTag } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminDangerGhostButtonClass,
  adminFilterFieldClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { useAuthStore } from '@/stores/authStore'
import { PERMISSION_CODES, USER_STATUS, type UserDto, type UserListQuery } from '@/types/rbac'

import { UserEditPage } from './userEditPage'
import { UserRolePage } from './userRolePage'

export function UserListPage() {
  const { t } = useTranslation('users')
  const { t: tc } = useTranslation('common')
  const queryClient = useQueryClient()
  const authUser = useAuthStore((state) => state.user)
  const meUserCode = authUser?.userCode

  const canUpdate = useHasPermission(PERMISSION_CODES.userUpdate)
  const canDelete = useHasPermission(PERMISSION_CODES.userDelete)
  const canStatus = useHasPermission(PERMISSION_CODES.userStatus)
  const canAssign = useHasPermission(PERMISSION_CODES.userRoleView)

  const [filters, setFilters] = useState<UserListQuery>({})
  const [draft, setDraft] = useState<UserListQuery>({})
  const [dialog, setDialog] = useState<
    { kind: 'create' } | { kind: 'edit'; id: number } | { kind: 'roles'; id: number } | null
  >(null)
  const [deleting, setDeleting] = useState<{ id: number; username: string } | null>(null)

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
      setDeleting(null)
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
    setDeleting({ id, username })
  }

  function toggleStatus(id: number, status: string) {
    const nextStatus = status === USER_STATUS.ACTIVE ? USER_STATUS.DISABLED : USER_STATUS.ACTIVE
    statusMutation.mutate({ id, status: nextStatus })
  }

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('list.title')} description={t('list.description')} />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <input
          className={adminFilterFieldClass}
          placeholder={t('list.filters.username')}
          value={draft.username ?? ''}
          onChange={(event) => setDraft((prev) => ({ ...prev, username: event.target.value || undefined }))}
        />
        <input
          className={adminFilterFieldClass}
          placeholder={t('list.filters.displayName')}
          value={draft.displayName ?? ''}
          onChange={(event) => setDraft((prev) => ({ ...prev, displayName: event.target.value || undefined }))}
        />
        <select
          className={adminFilterFieldClass}
          value={draft.status ?? ''}
          onChange={(event) => setDraft((prev) => ({ ...prev, status: event.target.value || undefined }))}
        >
          <option value="">{t('list.filters.allStatus')}</option>
          <option value={USER_STATUS.ACTIVE}>{tc('enum.status.ACTIVE')}</option>
          <option value={USER_STATUS.DISABLED}>{tc('enum.status.DISABLED')}</option>
        </select>
        <button type="button" onClick={applyFilters} className={adminPrimaryButtonClass}>
          {t('list.filters.search')}
        </button>
        <button type="button" onClick={resetFilters} className={adminSecondaryButtonClass}>
          {t('list.filters.reset')}
        </button>
        <PermissionGate code={PERMISSION_CODES.userCreate}>
          <button type="button" onClick={() => setDialog({ kind: 'create' })} className={adminPrimaryButtonClass}>
            <Plus className="h-4 w-4" />
            {t('list.create')}
          </button>
        </PermissionGate>
      </div>

      <DataTable
        columns={[
          {
            id: 'username',
            header: t('list.columns.username'),
            cell: (user) => <UserNameCell user={user} meUserCode={meUserCode} selfLabel={t('list.badges.self')} protectedLabel={t('list.badges.protected')} />,
          },
          { id: 'displayName', header: t('list.columns.displayName'), cell: (user) => user.displayName || '—' },
          { id: 'email', header: t('list.columns.email'), cell: (user) => user.email || '—' },
          { id: 'phone', header: t('list.columns.phone'), cell: (user) => user.phone || '—' },
          { id: 'status', header: t('list.columns.status'), cell: (user) => <StatusTag status={user.status} /> },
          {
            id: 'actions',
            header: t('list.columns.actions'),
            cell: (user) => (
              <UserRowActions
                user={user}
                meUserCode={meUserCode}
                canUpdate={canUpdate}
                canStatus={canStatus}
                canAssign={canAssign}
                canDelete={canDelete}
                statusPending={statusMutation.isPending}
                deletePending={deleteMutation.isPending}
                onEdit={() => setDialog({ kind: 'edit', id: user.id })}
                onRoles={() => setDialog({ kind: 'roles', id: user.id })}
                onToggleStatus={() => toggleStatus(user.id, user.status)}
                onDelete={() => handleDelete(user.id, user.username)}
              />
            ),
          },
        ]}
        data={users}
        rowKey={(user) => user.id}
        isLoading={isPending}
        isError={isError}
        errorMessage={error instanceof Error ? error.message : t('list.error')}
        emptyMessage={t('list.empty')}
        onRetry={() => void refetch()}
      />

      {dialog?.kind === 'create' ? (
        <FormDialog
          title={t('edit.createTitle')}
          description={t('edit.createDescription')}
          closeLabel={t('edit.actions.cancel')}
          onClose={() => setDialog(null)}
        >
          <UserEditPage userId="new" onClose={() => setDialog(null)} />
        </FormDialog>
      ) : null}
      {dialog?.kind === 'edit' ? (
        <FormDialog
          title={t('edit.editTitle')}
          closeLabel={t('edit.actions.cancel')}
          onClose={() => setDialog(null)}
        >
          <UserEditPage userId={String(dialog.id)} onClose={() => setDialog(null)} />
        </FormDialog>
      ) : null}
      {dialog?.kind === 'roles' ? (
        <FormDialog
          title={t('roles.title')}
          closeLabel={t('roles.cancel')}
          onClose={() => setDialog(null)}
          wide
        >
          <UserRolePage userId={String(dialog.id)} onClose={() => setDialog(null)} />
        </FormDialog>
      ) : null}
      {deleting ? (
        <FormDialog
          title={t('list.messages.deleteConfirmTitle')}
          closeLabel={t('list.actions.cancel')}
          onClose={() => setDeleting(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('list.messages.confirmDelete', { username: deleting.username })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setDeleting(null)}
            >
              {t('list.actions.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(deleting.id)}
            >
              {deleteMutation.isPending ? <Spinner /> : null}
              {t('list.actions.delete')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </section>
  )
}

function UserNameCell({
  user,
  meUserCode,
  selfLabel,
  protectedLabel,
}: {
  user: UserDto
  meUserCode?: string
  selfLabel: string
  protectedLabel: string
}) {
  const isSelf = Boolean(meUserCode && user.userCode && meUserCode === user.userCode)
  return (
    <div className="flex flex-wrap items-center gap-2 font-medium">
      <span>{user.username}</span>
      {isSelf ? (
        <span className="rounded-md border border-info/35 bg-info-muted px-1.5 py-0.5 text-xs text-info-emphasis">{selfLabel}</span>
      ) : null}
      {user.protectedUser ? (
        <span className="rounded-md border border-warning/35 bg-warning-muted px-1.5 py-0.5 text-xs text-warning-emphasis">
          {protectedLabel}
        </span>
      ) : null}
    </div>
  )
}

function UserRowActions({
  user,
  meUserCode,
  canUpdate,
  canStatus,
  canAssign,
  canDelete,
  statusPending,
  deletePending,
  onEdit,
  onRoles,
  onToggleStatus,
  onDelete,
}: {
  user: UserDto
  meUserCode?: string
  canUpdate: boolean
  canStatus: boolean
  canAssign: boolean
  canDelete: boolean
  statusPending: boolean
  deletePending: boolean
  onEdit: () => void
  onRoles: () => void
  onToggleStatus: () => void
  onDelete: () => void
}) {
  const { t } = useTranslation('users')
  const isSelf = Boolean(meUserCode && user.userCode && meUserCode === user.userCode)
  const dangerLocked = isSelf || user.protectedUser
  return (
    <div className="flex flex-wrap gap-2">
      {canUpdate ? (
        <button type="button" onClick={onEdit} className={adminGhostButtonClass}>
          <Pencil className="h-3.5 w-3.5" />
          {t('list.actions.edit')}
        </button>
      ) : null}
      {canStatus && !dangerLocked ? (
        <button type="button" onClick={onToggleStatus} disabled={statusPending} className={adminGhostButtonClass}>
          {user.status === USER_STATUS.ACTIVE ? t('list.actions.disable') : t('list.actions.enable')}
        </button>
      ) : null}
      {canAssign ? (
        <button type="button" onClick={onRoles} className={adminGhostButtonClass}>
          <KeyRound className="h-3.5 w-3.5" />
          {t('list.actions.assignRoles')}
        </button>
      ) : null}
      {canDelete && !dangerLocked ? (
        <button type="button" onClick={onDelete} disabled={deletePending} className={adminDangerGhostButtonClass}>
          <Trash2 className="h-3.5 w-3.5" />
          {t('list.actions.delete')}
        </button>
      ) : null}
      {dangerLocked ? (
        <span className="text-xs text-admin-muted">
          {isSelf ? t('list.messages.restrictedSelf') : t('list.messages.restrictedProtected')}
        </span>
      ) : null}
    </div>
  )
}
