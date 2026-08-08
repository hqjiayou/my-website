import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { rbacApi } from '@/api/rbacApi'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { groupPermissionsByResourceType, isSuperAdminRole, useHasPermission } from '@/router/permission'
import { ROUTES } from '@/router/config/routePaths'
import { PERMISSION_CODES, type PermissionDto } from '@/types/rbac'

export function RolePermissionPage() {
  const { t } = useTranslation('roles')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { roleId } = useParams()
  const parsedRoleId = Number(roleId)
  const canSave = useHasPermission(PERMISSION_CODES.rolePermissionUpdate)

  const {
    data: role,
    isPending: isRolePending,
    isError: isRoleError,
    error: roleError,
    refetch: refetchRole,
  } = useQuery({
    queryKey: ['roles', parsedRoleId],
    queryFn: () => rbacApi.getRole(parsedRoleId),
    enabled: Number.isFinite(parsedRoleId),
  })

  const {
    data: allPermissions,
    isPending: isPermissionsPending,
    isError: isPermissionsError,
    error: permissionsError,
    refetch: refetchPermissions,
  } = useQuery({
    queryKey: ['permissions'],
    queryFn: () => rbacApi.listPermissions(),
  })

  const {
    data: rolePermissions,
    isPending: isRolePermissionsPending,
    isError: isRolePermissionsError,
    error: rolePermissionsError,
    refetch: refetchRolePermissions,
  } = useQuery({
    queryKey: ['roles', parsedRoleId, 'permissions'],
    queryFn: () => rbacApi.getRolePermissions(parsedRoleId),
    enabled: Number.isFinite(parsedRoleId),
  })

  const grouped = useMemo(
    () => groupPermissionsByResourceType(allPermissions ?? []),
    [allPermissions],
  )
  const protectedRole = role ? isSuperAdminRole(role.roleCode) : false

  if (!Number.isFinite(parsedRoleId)) {
    return <p className="text-sm text-slate-500">{t('permissions.invalidId')}</p>
  }

  if (isRoleError || isPermissionsError || isRolePermissionsError) {
    return (
      <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
        <p>
          {(roleError instanceof Error && roleError.message) ||
            (permissionsError instanceof Error && permissionsError.message) ||
            (rolePermissionsError instanceof Error && rolePermissionsError.message) ||
            t('permissions.loadError')}
        </p>
        <button
          type="button"
          onClick={() => {
            void refetchRole()
            void refetchPermissions()
            void refetchRolePermissions()
          }}
          className="mt-2 underline"
        >
          {t('permissions.retry')}
        </button>
      </div>
    )
  }

  if (isRolePending || isPermissionsPending || isRolePermissionsPending || !role || !rolePermissions) {
    return <p className="text-sm text-slate-500">{t('permissions.loading')}</p>
  }

  return (
    <RolePermissionEditor
      key={rolePermissions.permissions.join('\0')}
      role={role}
      grouped={grouped}
      initialCodes={rolePermissions.permissions}
      protectedRole={protectedRole}
      canSave={canSave}
      parsedRoleId={parsedRoleId}
      queryClient={queryClient}
      navigate={navigate}
    />
  )
}

function RolePermissionEditor({
  role,
  grouped,
  initialCodes,
  protectedRole,
  canSave,
  parsedRoleId,
  queryClient,
  navigate,
}: {
  role: NonNullable<Awaited<ReturnType<typeof rbacApi.getRole>>>
  grouped: ReturnType<typeof groupPermissionsByResourceType>
  initialCodes: string[]
  protectedRole: boolean
  canSave: boolean
  parsedRoleId: number
  queryClient: ReturnType<typeof useQueryClient>
  navigate: ReturnType<typeof useNavigate>
}) {
  const { t } = useTranslation('roles')
  const [selectedCodes, setSelectedCodes] = useState(() => new Set(initialCodes))

  const saveMutation = useMutation({
    mutationFn: () =>
      rbacApi.replaceRolePermissions(parsedRoleId, {
        permissionCodes: [...selectedCodes],
      }),
    onSuccess: () => {
      toast.success(t('permissions.messages.saved'))
      void queryClient.invalidateQueries({ queryKey: ['roles', parsedRoleId, 'permissions'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  function toggleCode(code: string) {
    setSelectedCodes((prev) => {
      const next = new Set(prev)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return next
    })
  }

  function toggleGroup(items: PermissionDto[], checked: boolean) {
    setSelectedCodes((prev) => {
      const next = new Set(prev)
      for (const item of items) {
        if (checked) next.add(item.permissionCode)
        else next.delete(item.permissionCode)
      }
      return next
    })
  }

  return (
    <section>
      <PageHeader
        title={t('permissions.title')}
        description={
          role
            ? t('permissions.description', { roleName: role.roleName, roleCode: role.roleCode })
            : t('permissions.descriptionFallback')
        }
        action={
          <Link
            to={ROUTES.system.roles}
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
          >
            {t('permissions.backToList')}
          </Link>
        }
      />

      {protectedRole ? (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          {t('permissions.protectedHint')}
        </div>
      ) : null}

      <div className="space-y-4">
        <PermissionGroupPanel
          title={t('permissions.groups.menuTitle')}
          description={t('permissions.groups.menuDescription')}
          items={grouped.menu}
          selectedCodes={selectedCodes}
          disabled={protectedRole}
          onToggle={toggleCode}
          onToggleAll={toggleGroup}
        />
        <PermissionGroupPanel
          title={t('permissions.groups.actionTitle')}
          description={t('permissions.groups.actionDescription')}
          items={grouped.action}
          selectedCodes={selectedCodes}
          disabled={protectedRole}
          onToggle={toggleCode}
          onToggleAll={toggleGroup}
        />
        {grouped.other.length > 0 ? (
          <PermissionGroupPanel
            title={t('permissions.groups.otherTitle')}
            description={t('permissions.groups.otherDescription')}
            items={grouped.other}
            selectedCodes={selectedCodes}
            disabled={protectedRole}
            onToggle={toggleCode}
            onToggleAll={toggleGroup}
          />
        ) : null}
      </div>

      <div className="mt-6 flex gap-2">
        {canSave && !protectedRole ? (
          <button
            type="button"
            disabled={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {saveMutation.isPending ? t('permissions.actions.saving') : t('permissions.actions.save')}
          </button>
        ) : null}
        <button
          type="button"
          onClick={() => navigate(ROUTES.system.roles)}
          className="rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
        >
          {t('permissions.actions.cancel')}
        </button>
      </div>
    </section>
  )
}

function PermissionGroupPanel({
  title,
  description,
  items,
  selectedCodes,
  disabled,
  onToggle,
  onToggleAll,
}: {
  title: string
  description: string
  items: PermissionDto[]
  selectedCodes: Set<string>
  disabled: boolean
  onToggle: (code: string) => void
  onToggleAll: (items: PermissionDto[], checked: boolean) => void
}) {
  const { t } = useTranslation('roles')
  const allSelected = items.length > 0 && items.every((item) => selectedCodes.has(item.permissionCode))
  const someSelected = items.some((item) => selectedCodes.has(item.permissionCode))

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-base font-semibold text-slate-900">{title}</h2>
          <p className="mt-1 text-sm text-slate-500">{description}</p>
        </div>
        {!disabled && items.length > 0 ? (
          <button
            type="button"
            onClick={() => onToggleAll(items, !allSelected)}
            className="text-sm text-slate-600 underline-offset-2 hover:underline"
          >
            {allSelected ? t('permissions.deselectAll') : someSelected ? t('permissions.selectAll') : t('permissions.selectAll')}
          </button>
        ) : null}
      </div>

      {items.length === 0 ? (
        <p className="text-sm text-slate-500">{t('permissions.emptyGroup')}</p>
      ) : (
        <div className="grid gap-2 sm:grid-cols-2">
          {items.map((item) => (
            <label
              key={item.id}
              className={cn(
                'flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2 transition',
                selectedCodes.has(item.permissionCode)
                  ? 'border-slate-900 bg-slate-50'
                  : 'border-slate-200 hover:bg-slate-50',
                disabled && 'cursor-not-allowed opacity-60',
              )}
            >
              <input
                type="checkbox"
                className="mt-1"
                checked={selectedCodes.has(item.permissionCode)}
                disabled={disabled}
                onChange={() => onToggle(item.permissionCode)}
              />
              <span>
                <span className="block text-sm font-medium text-slate-900">{item.permissionName}</span>
                <span className="mt-0.5 block text-xs text-slate-500">{item.permissionCode}</span>
              </span>
            </label>
          ))}
        </div>
      )}
    </div>
  )
}
