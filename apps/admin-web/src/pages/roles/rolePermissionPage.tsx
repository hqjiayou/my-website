import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { rbacApi } from '@/api/rbacApi'
import { adminFormActionsClass, adminPrimaryButtonClass, adminSecondaryButtonClass } from '@/components/admin/controls'
import { AdminPageHeader } from '@/components/admin'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'
import { isSuperAdminRole, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES, ROLE_STATUS, type MenuNodeDto } from '@/types/rbac'

export function RolePermissionPage({
  roleId: roleIdProp,
  onClose,
}: {
  roleId?: string
  onClose?: () => void
} = {}) {
  const { t } = useTranslation('roles')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const params = useParams()
  const roleId = roleIdProp ?? params.roleId
  const parsedRoleId = Number(roleId)
  const close = onClose ?? (() => navigate(ROUTES.system.roles))
  const embedded = onClose != null
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
    data: menus,
    isPending: isMenusPending,
    isError: isMenusError,
    error: menusError,
    refetch: refetchMenus,
  } = useQuery({
    queryKey: ['menus'],
    queryFn: () => rbacApi.listMenus(),
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

  const pages = useMemo(() => leafMenus(menus ?? []), [menus])
  const protectedRole = role ? isSuperAdminRole(role.roleCode) : false

  if (!Number.isFinite(parsedRoleId)) {
    return <p className="text-sm text-admin-muted">{t('permissions.invalidId')}</p>
  }

  if (isRoleError || isMenusError || isRolePermissionsError) {
    return (
      <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
        <p>
          {(roleError instanceof Error && roleError.message) ||
            (menusError instanceof Error && menusError.message) ||
            (rolePermissionsError instanceof Error && rolePermissionsError.message) ||
            t('permissions.loadError')}
        </p>
        <button
          type="button"
          onClick={() => {
            void refetchRole()
            void refetchMenus()
            void refetchRolePermissions()
          }}
          className="mt-2 underline"
        >
          {t('permissions.retry')}
        </button>
      </div>
    )
  }

  if (isRolePending || isMenusPending || isRolePermissionsPending || !role || !rolePermissions) {
    return <p className="text-sm text-admin-muted">{t('permissions.loading')}</p>
  }

  return (
    <RoleMenuEditor
      key={rolePermissions.permissions.join('\0')}
      pages={pages}
      initialCodes={rolePermissions.permissions}
      protectedRole={protectedRole}
      canSave={canSave}
      parsedRoleId={parsedRoleId}
      queryClient={queryClient}
      onClose={close}
      embedded={embedded}
      roleName={role.roleName}
      roleCode={role.roleCode}
    />
  )
}

function RoleMenuEditor({
  pages,
  initialCodes,
  protectedRole,
  canSave,
  parsedRoleId,
  queryClient,
  onClose,
  embedded,
  roleName,
  roleCode,
}: {
  pages: MenuPage[]
  initialCodes: string[]
  protectedRole: boolean
  canSave: boolean
  parsedRoleId: number
  queryClient: ReturnType<typeof useQueryClient>
  onClose: () => void
  embedded: boolean
  roleName: string
  roleCode: string
}) {
  const { t } = useTranslation('roles')
  const pageCodes = useMemo(() => new Set(pages.map((page) => page.code)), [pages])
  const [selectedCodes, setSelectedCodes] = useState(
    () => new Set(initialCodes.filter((code) => pageCodes.has(code))),
  )

  const saveMutation = useMutation({
    mutationFn: () =>
      rbacApi.replaceRolePermissions(parsedRoleId, {
        permissionCodes: [...selectedCodes],
      }),
    onSuccess: () => {
      toast.success(t('permissions.messages.saved'))
      void queryClient.invalidateQueries({ queryKey: ['roles', parsedRoleId, 'permissions'] })
      void queryClient.invalidateQueries({ queryKey: ['menus'] })
      if (embedded) onClose()
    },
    onError: (error: Error) => toast.error(error.message),
  })

  function toggleCode(code: string) {
    if (protectedRole) return
    setSelectedCodes((prev) => {
      const next = new Set(prev)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return next
    })
  }

  return (
    <section>
      {embedded ? (
        <p className="mb-4 text-sm text-admin-muted">
          {t('permissions.description', { roleName, roleCode })}
        </p>
      ) : (
        <AdminPageHeader
          title={t('permissions.title')}
          description={t('permissions.description', { roleName, roleCode })}
        />
      )}

      {protectedRole ? (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          {t('permissions.protectedHint')}
        </div>
      ) : null}

      {pages.length === 0 ? (
        <p className="text-sm text-admin-muted">{t('permissions.emptyGroup')}</p>
      ) : (
        <div className="grid gap-2 sm:grid-cols-2">
          {pages.map((page) => (
            <label
              key={page.code}
              className={cn(
                'flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2 transition',
                selectedCodes.has(page.code)
                  ? 'border-[rgb(var(--primary))] bg-admin-surface-muted'
                  : 'border-admin bg-admin-surface hover:bg-admin-surface-muted',
                protectedRole && 'cursor-not-allowed opacity-60',
              )}
            >
              <input
                type="checkbox"
                className="admin-check mt-1"
                checked={selectedCodes.has(page.code)}
                disabled={protectedRole}
                onChange={() => toggleCode(page.code)}
              />
              <span>
                <span className="block text-sm font-medium text-admin">{page.name}</span>
                <span className="mt-0.5 block text-xs text-admin-muted">{page.path}</span>
              </span>
            </label>
          ))}
        </div>
      )}

      <div className={adminFormActionsClass}>
        <button type="button" onClick={onClose} className={adminSecondaryButtonClass}>
          {t('permissions.actions.cancel')}
        </button>
        {canSave && !protectedRole ? (
          <button
            type="button"
            disabled={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
            className={adminPrimaryButtonClass}
          >
            {saveMutation.isPending ? t('permissions.actions.saving') : t('permissions.actions.save')}
          </button>
        ) : null}
      </div>
    </section>
  )
}

function leafMenus(nodes: MenuNodeDto[], acc: MenuPage[] = []): MenuPage[] {
  for (const node of nodes) {
    if (node.status === ROLE_STATUS.DISABLED) continue
    if (node.path) {
      acc.push({ code: node.menuCode, name: node.menuName, path: node.path })
    }
    if (node.children?.length) leafMenus(node.children, acc)
  }
  return acc
}

type MenuPage = {
  code: string
  name: string
  path: string
}
