import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { SITE_CODE_PATTERN, SITE_TAG_PATTERN, SITE_TYPE_PRESETS, agentApi } from '@/api'
import { FormDialog, Spinner } from '@/components/admin'
import {
  adminFieldClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { PermissionGate } from '@/router/permission'
import { ROUTES } from '@/router/config/routePaths'
import { PERMISSION_CODES } from '@/types/rbac'

/** 新建智能体弹窗：由列表空状态与工作台头部原地打开；/agent/create 路由亦复用本组件。 */
export function AgentCreateDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation('sites')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [customType, setCustomType] = useState(false)
  const [agentName, setAgentName] = useState('')
  const [agentCode, setAgentCode] = useState('')
  const [agentType, setAgentType] = useState(SITE_TYPE_PRESETS[0])
  const [defaultDomain, setDefaultDomain] = useState('')
  const [description, setDescription] = useState('')

  const createMutation = useMutation({
    mutationFn: agentApi.createSite,
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: ['sites'] })
      toast.success(t('create.messages.createdSuccess'))
      onClose()
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
      defaultDomain: defaultDomain.trim() || undefined,
      description: description.trim() || undefined,
    })
  }

  return (
    <FormDialog
      title={t('create.title')}
      description={t('create.description')}
      closeLabel={t('create.close')}
      onClose={() => {
        if (createMutation.isPending) return
        onClose()
      }}
    >
      <PermissionGate code={PERMISSION_CODES.agentCreate}>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm text-admin">
              <span className="font-medium">{t('create.fields.agentName')}</span>
              <input
                className={`${adminFieldClass} mt-1.5`}
                value={agentName}
                placeholder={t('create.placeholders.agentName')}
                onChange={(event) => setAgentName(event.target.value)}
              />
            </label>
            <label className="block text-sm text-admin">
              <span className="font-medium">{t('create.fields.agentCode')}</span>
              <input
                className={`${adminFieldClass} mt-1.5`}
                value={agentCode}
                placeholder={t('create.placeholders.agentCode')}
                onChange={(event) => setAgentCode(event.target.value)}
              />
            </label>
          </div>
          <label className="block text-sm text-admin">
            <span className="font-medium">{t('create.fields.agentType')}</span>
            <select
              className={`${adminFieldClass} mt-1.5`}
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
            <span className="font-medium">{t('create.fields.defaultDomain')}</span>
            <input
              className={`${adminFieldClass} mt-1.5`}
              value={defaultDomain}
              placeholder={t('create.placeholders.domain')}
              onChange={(event) => setDefaultDomain(event.target.value)}
            />
          </label>
          <label className="block text-sm text-admin">
            <span className="font-medium">{t('create.fields.agentDescription')}</span>
            <textarea
              className={`${adminFieldClass} mt-1.5`}
              rows={3}
              value={description}
              placeholder={t('create.placeholders.agentDescription')}
              onChange={(event) => setDescription(event.target.value)}
            />
          </label>
          <div className="flex items-center justify-end gap-2 pt-1">
            <button type="button" className={adminSecondaryButtonClass} onClick={onClose}>
              {t('create.actions.cancel')}
            </button>
            <button type="submit" className={adminPrimaryButtonClass} disabled={createMutation.isPending}>
              {createMutation.isPending ? <Spinner /> : null}
              {createMutation.isPending ? t('create.actions.creating') : t('create.actions.createSite')}
            </button>
          </div>
        </form>
      </PermissionGate>
    </FormDialog>
  )
}
