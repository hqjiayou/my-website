import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { rbacApi } from '@/api/rbacApi'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { isSuperAdminRole, useHasPermission } from '@/router/permission'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import { PERMISSION_CODES, USER_STATUS, type RoleDto } from '@/types/rbac'

export function UserRolePage() {
  const { t } = useTranslation('users')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { userId } = useParams()
  const parsedUserId = Number(userId)
  const canSave = useHasPermission(PERMISSION_CODES.userRoleUpdate)
  const authUser = useAuthStore((state) => state.user)
  const meUserCode = authUser?.userCode

  const {
    data: allRoles = [],
    isPending: isRolesPending,
    isError: isRolesError,
    error: rolesError,
    refetch: refetchRoles,
  } = useQuery({
    queryKey: ['roles'],
    queryFn: () => rbacApi.listRoles(),
  })

  const {
    data: user,
    isPending: isUserPending,
    isError: isUserError,
    error: userError,
    refetch: refetchUser,
  } = useQuery({
    queryKey: ['users', parsedUserId],
    queryFn: () => rbacApi.getUser(parsedUserId),
    enabled: Number.isFinite(parsedUserId),
  })

  const {
    data: userRoles,
    isPending: isUserRolesPending,
    isError: isUserRolesError,
    error: userRolesError,
    refetch: refetchUserRoles,
  } = useQuery({
    queryKey: ['users', parsedUserId, 'roles'],
    queryFn: () => rbacApi.getUserRoles(parsedUserId),
    enabled: Number.isFinite(parsedUserId),
  })

  const assignableRoles = useMemo(
    () => allRoles.filter((role) => !isSuperAdminRole(role.roleCode) && role.status === USER_STATUS.ACTIVE),
    [allRoles],
  )

  // self / 保护态消费详情字段，不用角色 binding 推导
  const isSelf = Boolean(meUserCode && user?.userCode && meUserCode === user.userCode)
  const protectedUser = user?.protectedUser === true
  const readOnly = isSelf || protectedUser

  if (!Number.isFinite(parsedUserId)) {
    return <p className="text-sm text-slate-500">{t('roles.invalidId')}</p>
  }

  if (isRolesError || isUserError || isUserRolesError) {
    return (
      <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
        <p>
          {(rolesError instanceof Error && rolesError.message) ||
            (userError instanceof Error && userError.message) ||
            (userRolesError instanceof Error && userRolesError.message) ||
            t('roles.loadError')}
        </p>
        <button
          type="button"
          onClick={() => {
            void refetchRoles()
            void refetchUser()
            void refetchUserRoles()
          }}
          className="mt-2 underline"
        >
          {t('roles.retry')}
        </button>
      </div>
    )
  }

  if (isRolesPending || isUserPending || isUserRolesPending || !user || !userRoles) {
    return <p className="text-sm text-slate-500">{t('roles.loading')}</p>
  }

  return (
    <UserRoleEditor
      key={userRoles.roles.map((role) => role.roleCode).join('\0')}
      username={user.username}
      userCode={user.userCode}
      assignableRoles={assignableRoles}
      initialCodes={userRoles.roles.map((role) => role.roleCode).filter((code) => !isSuperAdminRole(code))}
      readOnly={readOnly}
      restrictionHint={
        isSelf ? t('roles.messages.restrictedSelf') : protectedUser ? t('roles.messages.restrictedProtected') : null
      }
      canSave={canSave}
      parsedUserId={parsedUserId}
      queryClient={queryClient}
      navigate={navigate}
    />
  )
}

function UserRoleEditor({
  username,
  userCode,
  assignableRoles,
  initialCodes,
  readOnly,
  restrictionHint,
  canSave,
  parsedUserId,
  queryClient,
  navigate,
}: {
  username: string
  userCode: string
  assignableRoles: RoleDto[]
  initialCodes: string[]
  readOnly: boolean
  restrictionHint: string | null
  canSave: boolean
  parsedUserId: number
  queryClient: ReturnType<typeof useQueryClient>
  navigate: ReturnType<typeof useNavigate>
}) {
  const { t } = useTranslation('users')
  const [selectedCodes, setSelectedCodes] = useState(() => new Set(initialCodes))
  const [localError, setLocalError] = useState<string | null>(null)

  const saveMutation = useMutation({
    mutationFn: () =>
      rbacApi.replaceUserRoles(parsedUserId, {
        roleCodes: [...selectedCodes],
      }),
    onSuccess: () => {
      toast.success(t('roles.messages.saved'))
      void queryClient.invalidateQueries({ queryKey: ['users', parsedUserId, 'roles'] })
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      void queryClient.invalidateQueries({ queryKey: ['users', parsedUserId] })
    },
  })

  function toggleCode(code: string) {
    if (readOnly || isSuperAdminRole(code)) return
    setLocalError(null)
    setSelectedCodes((prev) => {
      const next = new Set(prev)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return next
    })
  }

  function toggleAll(checked: boolean) {
    if (readOnly) return
    setLocalError(null)
    setSelectedCodes(() => {
      if (!checked) return new Set()
      return new Set(assignableRoles.map((role) => role.roleCode))
    })
  }

  function handleSave() {
    if (selectedCodes.size === 0) {
      setLocalError(t('roles.atLeastOne'))
      return
    }
    saveMutation.mutate()
  }

  const allSelected =
    assignableRoles.length > 0 && assignableRoles.every((role) => selectedCodes.has(role.roleCode))

  return (
    <section>
      <PageHeader
        title={t('roles.title')}
        description={t('roles.description', { username, userCode })}
        action={
          <Link
            to={ROUTES.system.users}
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
          >
            {t('roles.back')}
          </Link>
        }
      />

      {restrictionHint ? (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          {restrictionHint}
        </div>
      ) : (
        <p className="mb-4 text-sm text-slate-500">{t('roles.cannotAssignSuperAdmin')}</p>
      )}

      <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-base font-semibold text-slate-900">{t('roles.title')}</h2>
            <p className="mt-1 text-sm text-slate-500">{t('roles.descriptionFallback')}</p>
          </div>
          {!readOnly && assignableRoles.length > 0 ? (
            <button
              type="button"
              onClick={() => toggleAll(!allSelected)}
              className="text-sm text-slate-600 underline-offset-2 hover:underline"
            >
              {allSelected ? t('roles.clearAll') : t('roles.selectAll')}
            </button>
          ) : null}
        </div>

        {assignableRoles.length === 0 ? (
          <p className="text-sm text-slate-500">{t('roles.emptyRoles')}</p>
        ) : (
          <div className="grid gap-2 sm:grid-cols-2">
            {assignableRoles.map((role) => (
              <label
                key={role.id}
                className={cn(
                  'flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2 transition',
                  selectedCodes.has(role.roleCode)
                    ? 'border-slate-900 bg-slate-50'
                    : 'border-slate-200 hover:bg-slate-50',
                  readOnly && 'cursor-not-allowed opacity-60',
                )}
              >
                <input
                  type="checkbox"
                  className="mt-1"
                  checked={selectedCodes.has(role.roleCode)}
                  disabled={readOnly}
                  onChange={() => toggleCode(role.roleCode)}
                />
                <span>
                  <span className="block text-sm font-medium text-slate-900">{role.roleName}</span>
                  <span className="mt-0.5 block text-xs text-slate-500">{role.roleCode}</span>
                </span>
              </label>
            ))}
          </div>
        )}
        {localError ? <p className="mt-3 text-xs text-red-600">{localError}</p> : null}
      </div>

      <div className="mt-6 flex gap-2">
        {canSave && !readOnly ? (
          <button
            type="button"
            disabled={saveMutation.isPending}
            onClick={handleSave}
            className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {saveMutation.isPending ? t('roles.saving') : t('roles.save')}
          </button>
        ) : null}
        <button
          type="button"
          onClick={() => navigate(ROUTES.system.users)}
          className="rounded-lg border border-slate-200 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
        >
          {t('roles.cancel')}
        </button>
      </div>
    </section>
  )
}
