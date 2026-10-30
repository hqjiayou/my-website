import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, type ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { z } from 'zod'

import { rbacApi } from '@/api/rbacApi'
import {
  adminFieldClass,
  adminFormActionsClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { AdminPageHeader } from '@/components/admin'
import { PermissionGate } from '@/router/permission'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import { PERMISSION_CODES } from '@/types/rbac'

const usernamePattern = /^[a-zA-Z][a-zA-Z0-9_]{2,127}$/

export function UserEditPage({
  userId: userIdProp,
  onClose,
}: {
  userId?: string
  onClose?: () => void
} = {}) {
  const { t } = useTranslation('users')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const params = useParams()
  const userId = userIdProp ?? params.userId
  const authUser = useAuthStore((state) => state.user)
  const meUserCode = authUser?.userCode
  const isCreate = userId == null || userId === 'new'
  const parsedId = isCreate ? null : Number(userId)
  const close = onClose ?? (() => navigate(ROUTES.system.users))
  const embedded = onClose != null

  const optionalEmail = z
    .string()
    .max(255)
    .optional()
    .refine(
      (value) => !value?.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim()),
      t('edit.validation.emailInvalid'),
    )

  const createSchema = z.object({
    username: z
      .string()
      .min(1, t('edit.validation.usernameRequired'))
      .regex(usernamePattern, t('edit.validation.usernamePattern')),
    password: z
      .string()
      .min(1, t('edit.validation.passwordRequired'))
      .min(8, t('edit.validation.passwordLength'))
      .max(128, t('edit.validation.passwordLength')),
    displayName: z.string().max(255).optional(),
    email: optionalEmail,
    phone: z.string().max(32).optional(),
  })

  const editSchema = z.object({
    displayName: z.string().max(255).optional(),
    email: optionalEmail,
    phone: z.string().max(32).optional(),
  })

  type CreateFormValues = z.infer<typeof createSchema>
  type EditFormValues = z.infer<typeof editSchema>

  const {
    data: user,
    isPending: isUserPending,
    isError: isUserError,
    error: userError,
    refetch,
  } = useQuery({
    queryKey: ['users', parsedId],
    queryFn: () => rbacApi.getUser(parsedId!),
    enabled: !isCreate && Number.isFinite(parsedId),
  })

  // self / 保护态直接消费后端字段，不用 id 解析或角色列表推导
  const isSelf = Boolean(meUserCode && user?.userCode && meUserCode === user.userCode)
  const protectedUser = user?.protectedUser === true
  const formLocked = protectedUser

  const createForm = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      username: '',
      password: '',
      displayName: '',
      email: '',
      phone: '',
    },
  })

  const editForm = useForm<EditFormValues>({
    resolver: zodResolver(editSchema),
    defaultValues: {
      displayName: '',
      email: '',
      phone: '',
    },
  })

  useEffect(() => {
    if (!user || isCreate) return
    editForm.reset({
      displayName: user.displayName ?? '',
      email: user.email ?? '',
      phone: user.phone ?? '',
    })
  }, [editForm, isCreate, user])

  const createMutation = useMutation({
    mutationFn: rbacApi.createUser,
    onSuccess: () => {
      toast.success(t('edit.messages.created'))
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      close()
    },
  })

  const updateMutation = useMutation({
    mutationFn: (values: EditFormValues) =>
      rbacApi.updateUser(parsedId!, {
        displayName: values.displayName || undefined,
        email: values.email || undefined,
        phone: values.phone || undefined,
      }),
    onSuccess: () => {
      toast.success(t('edit.messages.updated'))
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      void queryClient.invalidateQueries({ queryKey: ['users', parsedId] })
      close()
    },
  })

  if (!isCreate && !Number.isFinite(parsedId)) {
    return <p className="text-sm text-muted-foreground">{t('edit.invalidId')}</p>
  }

  if (!isCreate && isUserPending) {
    return <p className="text-sm text-muted-foreground">{t('edit.loading')}</p>
  }

  if (!isCreate && isUserError) {
    return (
      <div className="rounded-lg border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis">
        <p>{userError instanceof Error ? userError.message : t('edit.loadError')}</p>
        <button type="button" onClick={() => void refetch()} className="mt-2 underline">
          {t('edit.retry')}
        </button>
      </div>
    )
  }

  const form = (
    <>
      {formLocked ? (
        <div className="mb-4 rounded-lg border border-warning/35 bg-warning-muted px-4 py-3 text-sm text-warning-emphasis">
          {t('edit.messages.protectedHint')}
        </div>
      ) : null}
      <div>
        {isCreate ? (
          <form
            className="space-y-4"
            onSubmit={createForm.handleSubmit((values) =>
              createMutation.mutate({
                username: values.username.trim(),
                password: values.password,
                displayName: values.displayName?.trim() || undefined,
                email: values.email?.trim() || undefined,
                phone: values.phone?.trim() || undefined,
              }),
            )}
            noValidate
          >
            <Field label={t('edit.fields.username')} error={createForm.formState.errors.username?.message}>
              <input
                className={inputClassName}
                {...createForm.register('username')}
                placeholder={t('edit.placeholders.username')}
                autoComplete="off"
              />
            </Field>
            <Field label={t('edit.fields.password')} error={createForm.formState.errors.password?.message}>
              <input
                type="password"
                className={inputClassName}
                {...createForm.register('password')}
                placeholder={t('edit.placeholders.password')}
                autoComplete="new-password"
              />
            </Field>
            <Field label={t('edit.fields.displayName')} error={createForm.formState.errors.displayName?.message}>
              <input className={inputClassName} {...createForm.register('displayName')} />
            </Field>
            <Field label={t('edit.fields.email')} error={createForm.formState.errors.email?.message}>
              <input type="email" className={inputClassName} {...createForm.register('email')} />
            </Field>
            <Field label={t('edit.fields.phone')} error={createForm.formState.errors.phone?.message}>
              <input className={inputClassName} {...createForm.register('phone')} />
            </Field>
            <FormActions
              submitting={createMutation.isPending}
              onCancel={close}
              saveLabel={t('edit.actions.save')}
              savingLabel={t('edit.actions.saving')}
              cancelLabel={t('edit.actions.cancel')}
              permissionCode={PERMISSION_CODES.userCreate}
            />
          </form>
        ) : (
          <form
            className="space-y-4"
            onSubmit={editForm.handleSubmit((values) => updateMutation.mutate(values))}
            noValidate
          >
            <Field label={t('edit.fields.username')}>
              <input className={inputClassName} value={user?.username ?? ''} readOnly disabled />
              {isSelf ? (
                <span className="mt-1.5 inline-flex rounded-md border border-info/35 bg-info-muted px-1.5 py-0.5 text-xs text-info-emphasis">
                  {t('list.badges.self')}
                </span>
              ) : null}
            </Field>
            <Field label={t('edit.fields.displayName')} error={editForm.formState.errors.displayName?.message}>
              <input className={inputClassName} {...editForm.register('displayName')} disabled={formLocked} />
            </Field>
            <Field label={t('edit.fields.email')} error={editForm.formState.errors.email?.message}>
              <input
                type="email"
                className={inputClassName}
                {...editForm.register('email')}
                disabled={formLocked}
              />
            </Field>
            <Field label={t('edit.fields.phone')} error={editForm.formState.errors.phone?.message}>
              <input className={inputClassName} {...editForm.register('phone')} disabled={formLocked} />
            </Field>
            <FormActions
              submitting={updateMutation.isPending}
              onCancel={close}
              saveLabel={t('edit.actions.save')}
              savingLabel={t('edit.actions.saving')}
              cancelLabel={t('edit.actions.cancel')}
              disableSubmit={formLocked}
              permissionCode={PERMISSION_CODES.userUpdate}
            />
          </form>
        )}
      </div>
    </>
  )

  if (embedded) return form

  return (
    <section className="admin-content-form">
      <AdminPageHeader
        title={isCreate ? t('edit.createTitle') : t('edit.editTitle')}
        description={
          isCreate
            ? t('edit.createDescription')
            : t('edit.editDescription', { username: user?.username ?? '' })
        }
      />
      <div className="max-w-xl">{form}</div>
    </section>
  )
}

const inputClassName = adminFieldClass

function Field({
  label,
  error,
  children,
}: {
  label: string
  error?: string
  children: ReactNode
}) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-admin">{label}</label>
      {children}
      {error ? <p className="mt-1 text-xs text-danger-emphasis">{error}</p> : null}
    </div>
  )
}

function FormActions({
  submitting,
  onCancel,
  saveLabel,
  savingLabel,
  cancelLabel,
  disableSubmit,
  permissionCode,
}: {
  submitting: boolean
  onCancel: () => void
  saveLabel: string
  savingLabel: string
  cancelLabel: string
  disableSubmit?: boolean
  permissionCode: string
}) {
  return (
    <div className={adminFormActionsClass}>
      <button type="button" onClick={onCancel} className={adminSecondaryButtonClass}>
        {cancelLabel}
      </button>
      <PermissionGate code={permissionCode}>
        <button
          type="submit"
          disabled={submitting || disableSubmit}
          className={adminPrimaryButtonClass}
        >
          {submitting ? savingLabel : saveLabel}
        </button>
      </PermissionGate>
    </div>
  )
}
