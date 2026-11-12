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
import { AdminPageHeader, Spinner } from '@/components/admin'
import { ROUTES } from '@/router/config/routePaths'
import { ROLE_STATUS } from '@/types/rbac'

const roleCodePattern = /^[a-z][a-z0-9_]*$/

export function RoleEditPage({
  roleId: roleIdProp,
  onClose,
}: {
  roleId?: string
  onClose?: () => void
} = {}) {
  const { t } = useTranslation('roles')
  const { t: tc } = useTranslation('common')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const params = useParams()
  const roleId = roleIdProp ?? params.roleId
  const isCreate = roleId == null || roleId === 'new'
  const parsedId = isCreate ? null : Number(roleId)
  const close = onClose ?? (() => navigate(ROUTES.system.roles))
  const embedded = onClose != null

  const createSchema = z.object({
    roleCode: z
      .string()
      .min(1, t('edit.validation.roleCodeRequired'))
      .regex(roleCodePattern, t('edit.validation.roleCodePattern')),
    roleName: z.string().min(1, t('edit.validation.roleNameRequired')).max(255),
    description: z.string().max(2000).optional(),
    status: z.enum([ROLE_STATUS.ACTIVE, ROLE_STATUS.DISABLED]),
  })

  const editSchema = z.object({
    roleName: z.string().min(1, t('edit.validation.roleNameRequired')).max(255),
    description: z.string().max(2000).optional(),
    status: z.enum([ROLE_STATUS.ACTIVE, ROLE_STATUS.DISABLED]),
  })

  type CreateFormValues = z.infer<typeof createSchema>
  type EditFormValues = z.infer<typeof editSchema>

  const {
    data: role,
    isPending: isRolePending,
    isError: isRoleError,
    error: roleError,
    refetch,
  } = useQuery({
    queryKey: ['roles', parsedId],
    queryFn: () => rbacApi.getRole(parsedId!),
    enabled: !isCreate && Number.isFinite(parsedId),
  })

  const createForm = useForm<CreateFormValues>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      roleCode: '',
      roleName: '',
      description: '',
      status: ROLE_STATUS.ACTIVE,
    },
  })

  const editForm = useForm<EditFormValues>({
    resolver: zodResolver(editSchema),
    defaultValues: {
      roleName: '',
      description: '',
      status: ROLE_STATUS.ACTIVE,
    },
  })

  useEffect(() => {
    if (!role || isCreate) return
    editForm.reset({
      roleName: role.roleName,
      description: role.description ?? '',
      status: role.status as CreateFormValues['status'],
    })
  }, [editForm, isCreate, role])

  const createMutation = useMutation({
    mutationFn: rbacApi.createRole,
    onSuccess: () => {
      toast.success(t('edit.messages.created'))
      void queryClient.invalidateQueries({ queryKey: ['roles'] })
      close()
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const updateMutation = useMutation({
    mutationFn: (values: EditFormValues) => rbacApi.updateRole(parsedId!, values),
    onSuccess: () => {
      toast.success(t('edit.messages.updated'))
      void queryClient.invalidateQueries({ queryKey: ['roles'] })
      void queryClient.invalidateQueries({ queryKey: ['roles', parsedId] })
      close()
    },
    onError: (error: Error) => toast.error(error.message),
  })

  if (!isCreate && !Number.isFinite(parsedId)) {
    return <p className="text-sm text-muted-foreground">{t('edit.invalidId')}</p>
  }

  if (!isCreate && isRolePending) {
    return <p className="text-sm text-muted-foreground">{t('edit.loading')}</p>
  }

  if (!isCreate && isRoleError) {
    return (
      <div className="rounded-lg border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis">
        <p>{roleError instanceof Error ? roleError.message : t('edit.loadError')}</p>
        <button type="button" onClick={() => void refetch()} className="mt-2 underline">
          {t('edit.retry')}
        </button>
      </div>
    )
  }

  const form = (
    <>
      {isCreate ? (
          <form
            className="space-y-4"
            onSubmit={createForm.handleSubmit((values) => createMutation.mutate(values))}
            noValidate
          >
            <Field label={t('edit.fields.roleCode')} error={createForm.formState.errors.roleCode?.message}>
              <input
                className={inputClassName}
                {...createForm.register('roleCode')}
                placeholder={t('edit.placeholders.roleCode')}
              />
            </Field>
            <Field label={t('edit.fields.roleName')} error={createForm.formState.errors.roleName?.message}>
              <input className={inputClassName} {...createForm.register('roleName')} />
            </Field>
            <Field label={t('edit.fields.description')} error={createForm.formState.errors.description?.message}>
              <textarea className={textareaClassName} rows={3} {...createForm.register('description')} />
            </Field>
            <Field label={t('edit.fields.status')} error={createForm.formState.errors.status?.message}>
              <select className={inputClassName} {...createForm.register('status')}>
                <option value={ROLE_STATUS.ACTIVE}>{tc('enum.status.ACTIVE')}</option>
                <option value={ROLE_STATUS.DISABLED}>{tc('enum.status.DISABLED')}</option>
              </select>
            </Field>
            <FormActions
              submitting={createMutation.isPending}
              onCancel={close}
              saveLabel={t('edit.actions.save')}
              savingLabel={t('edit.actions.saving')}
              cancelLabel={t('edit.actions.cancel')}
            />
          </form>
        ) : (
          <form
            className="space-y-4"
            onSubmit={editForm.handleSubmit((values) => updateMutation.mutate(values))}
            noValidate
          >
            <Field label={t('edit.fields.roleCode')}>
              <input className={inputClassName} value={role?.roleCode ?? ''} readOnly disabled />
            </Field>
            <Field label={t('edit.fields.roleName')} error={editForm.formState.errors.roleName?.message}>
              <input className={inputClassName} {...editForm.register('roleName')} />
            </Field>
            <Field label={t('edit.fields.description')} error={editForm.formState.errors.description?.message}>
              <textarea className={textareaClassName} rows={3} {...editForm.register('description')} />
            </Field>
            <Field label={t('edit.fields.status')} error={editForm.formState.errors.status?.message}>
              <select className={inputClassName} {...editForm.register('status')}>
                <option value={ROLE_STATUS.ACTIVE}>{tc('enum.status.ACTIVE')}</option>
                <option value={ROLE_STATUS.DISABLED}>{tc('enum.status.DISABLED')}</option>
              </select>
            </Field>
            <FormActions
              submitting={updateMutation.isPending}
              onCancel={close}
              saveLabel={t('edit.actions.save')}
              savingLabel={t('edit.actions.saving')}
              cancelLabel={t('edit.actions.cancel')}
            />
          </form>
        )}
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
            : t('edit.editDescription', { roleCode: role?.roleCode ?? '' })
        }
      />
      <div className="max-w-xl">{form}</div>
    </section>
  )
}

const inputClassName = adminFieldClass

const textareaClassName = adminFieldClass

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
}: {
  submitting: boolean
  onCancel: () => void
  saveLabel: string
  savingLabel: string
  cancelLabel: string
}) {
  return (
    <div className={adminFormActionsClass}>
      <button type="button" onClick={onCancel} className={adminSecondaryButtonClass}>
        {cancelLabel}
      </button>
      <button type="submit" disabled={submitting} className={adminPrimaryButtonClass}>
        {submitting ? <Spinner /> : null}
        {submitting ? savingLabel : saveLabel}
      </button>
    </div>
  )
}
