import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { useNavigate } from 'react-router-dom'

import { SITE_CODE_PATTERN, SITE_TAG_PATTERN, SITE_TYPE_PRESETS, agentApi } from '@/api'
import { AdminPageHeader } from '@/components/admin'
import {
  adminFieldClass,
  adminFormActionsClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { ROUTES } from '@/router/config/routePaths'
import { PERMISSION_CODES } from '@/types/rbac'
import { PermissionGate } from '@/router/permission'

export function AgentCreatePage() {
  const { t } = useTranslation('sites')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [customType, setCustomType] = useState(false)
  const [agentName, setAgentName] = useState('')
  const [agentCode, setAgentCode] = useState('')
  const [agentType, setAgentType] = useState(SITE_TYPE_PRESETS[0])
  const [description, setDescription] = useState('')

  const createMutation = useMutation({
    mutationFn: agentApi.createSite,
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: ['sites'] })
      toast.success(t('create.messages.createdSuccess'))
      navigate(ROUTES.apps.agent(created.id))
    },
    onError: (error: Error) => toast.error(error.message),
  })

  function handleTypeSelect(value: string) {
    if (value === '__CUSTOM__') {
      setCustomType(true)
      setAgentType('')
      return
    }
    setCustomType(false)
    setAgentType(value)
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (!agentName.trim() || !agentCode.trim() || !agentType.trim()) {
      toast.warning(t('create.messages.requiredFields'))
      return
    }
    if (!SITE_CODE_PATTERN.test(agentCode.trim())) {
      toast.warning(t('create.messages.invalidAgentCode'))
      return
    }
    if (!SITE_TAG_PATTERN.test(agentType.trim())) {
      toast.warning(t('create.messages.invalidAgentType'))
      return
    }
    if (createMutation.isPending) return
    createMutation.mutate({
      agentCode: agentCode.trim(),
      agentName: agentName.trim(),
      agentType: agentType.trim(),
      description: description.trim() || undefined,
    })
  }

  return (
    <section className="admin-content-form">
      <AdminPageHeader title={t('create.title')} description={t('create.description')} />
      <PermissionGate code={PERMISSION_CODES.agentCreate}>
        <form onSubmit={handleSubmit} className="max-w-xl space-y-4">
          <label className="block text-sm text-admin">
            {t('create.fields.agentName')}
            <input
              className={`${adminFieldClass} mt-1`}
              value={agentName}
              placeholder={t('create.placeholders.agentName')}
              onChange={(event) => setAgentName(event.target.value)}
            />
          </label>
          <label className="block text-sm text-admin">
            {t('create.fields.agentCode')}
            <input
              className={`${adminFieldClass} mt-1`}
              value={agentCode}
              placeholder={t('create.placeholders.agentCode')}
              onChange={(event) => setAgentCode(event.target.value)}
            />
          </label>
          <label className="block text-sm text-admin">
            {t('create.fields.agentType')}
            <select
              className={`${adminFieldClass} mt-1`}
              value={customType ? '__CUSTOM__' : agentType}
              onChange={(event) => handleTypeSelect(event.target.value)}
            >
              {SITE_TYPE_PRESETS.map((value) => (
                <option key={value} value={value}>
                  {t(`agentType.${value}`)}
                </option>
              ))}
              <option value="__CUSTOM__">{t('create.fields.customAgentType')}</option>
            </select>
            {customType ? (
              <input
                className={`${adminFieldClass} mt-2`}
                value={agentType}
                placeholder={t('create.placeholders.customAgentType')}
                onChange={(event) => setAgentType(event.target.value)}
              />
            ) : null}
          </label>
          <label className="block text-sm text-admin">
            {t('create.fields.agentDescription')}
            <textarea
              className={`${adminFieldClass} mt-1`}
              rows={4}
              value={description}
              placeholder={t('create.placeholders.agentDescription')}
              onChange={(event) => setDescription(event.target.value)}
            />
          </label>
          <div className={adminFormActionsClass}>
            <button type="button" className={adminSecondaryButtonClass} onClick={() => navigate(ROUTES.apps.sites)}>
              {t('create.actions.cancel')}
            </button>
            <button type="submit" className={adminPrimaryButtonClass} disabled={createMutation.isPending}>
              {createMutation.isPending ? t('create.actions.creating') : t('create.actions.createSite')}
            </button>
          </div>
        </form>
      </PermissionGate>
    </section>
  )
}
