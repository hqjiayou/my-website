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
import { isSuperAdminRole, PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES, ROLE_STATUS, type RoleDto, type RoleListQuery } from '@/types/rbac'

import { RoleEditPage } from './roleEditPage'
import { RolePermissionPage } from './rolePermissionPage'

export function RoleListPage() {
  const { t } = useTranslation('roles')
  const { t: tc } = useTranslation('common')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.roleUpdate)
  const canDelete = useHasPermission(PERMISSION_CODES.roleDelete)
  const canAssign = useHasPermission(PERMISSION_CODES.rolePermissionView)

  const [filters, setFilters] = useState<RoleListQuery>({})
  const [draft, setDraft] = useState<RoleListQuery>({})
  const [dialog, setDialog] = useState<
    { kind: 'create' } | { kind: 'edit'; id: number } | { kind: 'permissions'; id: number } | null
  >(null)
  const [deleting, setDeleting] = useState<{ id: number; roleCode: string } | null>(null)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['roles', filters],
    queryFn: () => rbacApi.listRoles(filters),
  })

  const roles = data ?? []

  const deleteMutation = useMutation({
    mutationFn: (id: number) => rbacApi.deleteRole(id),
    onSuccess: () => {
      setDeleting(null)
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
    setDeleting({ id, roleCode })
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
    <section className="admin-content-data">
      <AdminPageHeader title={t('list.title')} description={t('list.description')} />

      <div className="mb-4 flex flex-wrap items-center gap-2">
        <input
          className={adminFilterFieldClass}
          placeholder={t('list.filters.roleCode')}
          value={draft.roleCode ?? ''}
          onChange={(event) => setDraft((prev) => ({ ...prev, roleCode: event.target.value || undefined }))}
        />
        <input
          className={adminFilterFieldClass}
          placeholder={t('list.filters.roleName')}
          value={draft.roleName ?? ''}
          onChange={(event) => setDraft((prev) => ({ ...prev, roleName: event.target.value || undefined }))}
        />
        <select
          className={adminFilterFieldClass}
          value={draft.status ?? ''}
          onChange={(event) => setDraft((prev) => ({ ...prev, status: event.target.value || undefined }))}
        >
          <option value="">{t('list.filters.allStatus')}</option>
          <option value={ROLE_STATUS.ACTIVE}>{tc('enum.status.ACTIVE')}</option>
          <option value={ROLE_STATUS.DISABLED}>{tc('enum.status.DISABLED')}</option>
        </select>
        <button type="button" onClick={applyFilters} className={adminPrimaryButtonClass}>
          {t('list.filters.search')}
        </button>
        <button type="button" onClick={resetFilters} className={adminSecondaryButtonClass}>
          {t('list.filters.reset')}
        </button>
        <PermissionGate code={PERMISSION_CODES.roleCreate}>
          <button type="button" onClick={() => setDialog({ kind: 'create' })} className={adminPrimaryButtonClass}>
            <Plus className="h-4 w-4" />
            {t('list.create')}
          </button>
        </PermissionGate>
      </div>

      <DataTable
        columns={[
          { id: 'roleCode', header: t('list.columns.roleCode'), cell: (role) => <span className="font-medium">{role.roleCode}</span> },
          { id: 'roleName', header: t('list.columns.roleName'), cell: (role) => role.roleName },
          { id: 'status', header: t('list.columns.status'), cell: (role) => <StatusTag status={role.status} /> },
          { id: 'description', header: t('list.columns.description'), cell: (role) => role.description || '—' },
          {
            id: 'actions',
            header: t('list.columns.actions'),
            cell: (role) => (
              <RoleRowActions
                role={role}
                canUpdate={canUpdate}
                canAssign={canAssign}
                canDelete={canDelete}
                onEdit={() => setDialog({ kind: 'edit', id: role.id })}
                onPermissions={() => setDialog({ kind: 'permissions', id: role.id })}
                onToggleStatus={() => toggleStatus(role)}
                onDelete={() => handleDelete(role.id, role.roleCode)}
              />
            ),
          },
        ]}
        data={roles}
        rowKey={(role) => role.id}
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
          <RoleEditPage roleId="new" onClose={() => setDialog(null)} />
        </FormDialog>
      ) : null}
      {dialog?.kind === 'edit' ? (
        <FormDialog
          title={t('edit.editTitle')}
          closeLabel={t('edit.actions.cancel')}
          onClose={() => setDialog(null)}
        >
          <RoleEditPage roleId={String(dialog.id)} onClose={() => setDialog(null)} />
        </FormDialog>
      ) : null}
      {dialog?.kind === 'permissions' ? (
        <FormDialog
          title={t('permissions.title')}
          closeLabel={t('permissions.actions.cancel')}
          onClose={() => setDialog(null)}
          wide
        >
          <RolePermissionPage roleId={String(dialog.id)} onClose={() => setDialog(null)} />
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
            {t('list.messages.confirmDelete', { roleCode: deleting.roleCode })}
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

function RoleRowActions({
  role,
  canUpdate,
  canAssign,
  canDelete,
  onEdit,
  onPermissions,
  onToggleStatus,
  onDelete,
}: {
  role: RoleDto
  canUpdate: boolean
  canAssign: boolean
  canDelete: boolean
  onEdit: () => void
  onPermissions: () => void
  onToggleStatus: () => void
  onDelete: () => void
}) {
  const { t } = useTranslation('roles')
  const protectedRole = isSuperAdminRole(role.roleCode)
  if (protectedRole) {
    return <span className="text-xs text-admin-muted">{t('list.badges.protected')}</span>
  }
  return (
    <div className="flex flex-wrap gap-2">
      {canUpdate ? (
        <button type="button" onClick={onEdit} className={adminGhostButtonClass}>
          <Pencil className="h-3.5 w-3.5" />
          {t('list.actions.edit')}
        </button>
      ) : null}
      {canUpdate ? (
        <button type="button" onClick={onToggleStatus} className={adminGhostButtonClass}>
          {role.status === ROLE_STATUS.ACTIVE ? t('list.actions.disable') : t('list.actions.enable')}
        </button>
      ) : null}
      {canAssign ? (
        <button type="button" onClick={onPermissions} className={adminGhostButtonClass}>
          <KeyRound className="h-3.5 w-3.5" />
          {t('list.actions.assignPermissions')}
        </button>
      ) : null}
      {canDelete ? (
        <button type="button" onClick={onDelete} className={adminDangerGhostButtonClass}>
          <Trash2 className="h-3.5 w-3.5" />
          {t('list.actions.delete')}
        </button>
      ) : null}
    </div>
  )
}
