import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'
import { toast } from 'sonner'

import { agentApi, SITE_TAG_PATTERN, SITE_TYPE_PRESETS, type Site } from '@/api'
import { AdminPageHeader, StatusTag } from '@/components/admin'
import { adminFieldClass, adminPrimaryButtonClass } from '@/components/admin/controls'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function AgentBasicPage() {
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  // site 刷新后以 id+version 强制重建表单，避免 effect 内同步 setState
  return <AgentBasicForm key={`${site.id}-${site.version}`} site={site} />
}

function AgentBasicForm({ site }: { site: Site }) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.agentUpdate)

  const [agentName, setAgentName] = useState(site.agentName)
  const [agentType, setAgentType] = useState(site.agentType)
  const [customType, setCustomType] = useState(false)
  const [defaultDomain, setDefaultDomain] = useState(site.defaultDomain ?? '')
  const [description, setDescription] = useState(site.description ?? '')

  const dirty =
    agentName.trim() !== site.agentName ||
    agentType.trim() !== site.agentType ||
    defaultDomain.trim() !== (site.defaultDomain ?? '') ||
    description.trim() !== (site.description ?? '')

  const updateMutation = useMutation({
    mutationFn: () =>
      agentApi.updateSite(site.id, {
        agentName: agentName.trim(),
        agentType: agentType.trim(),
        defaultDomain: defaultDomain.trim() || undefined,
        description: description.trim() || undefined,
        version: site.version,
      }),
    onSuccess: async () => {
      toast.success(t('basic.messages.saved'))
      await queryClient.invalidateQueries({ queryKey: ['sites'] })
      await queryClient.invalidateQueries({ queryKey: ['sites', site.id] })
    },
    onError: (error: Error) => {
      toast.error(error.message)
      // 乐观锁 / 任意更新失败后刷新 version，避免连点沿用旧 version 持续 409
      void queryClient.invalidateQueries({ queryKey: ['sites'] })
      void queryClient.invalidateQueries({ queryKey: ['sites', site.id] })
    },
  })

  function handleSave() {
    if (!agentName.trim() || !agentType.trim()) {
      toast.warning(t('create.messages.requiredFields'))
      return
    }
    if (!SITE_TAG_PATTERN.test(agentType.trim())) {
      toast.warning(t('create.messages.invalidAgentType'))
      return
    }
    if (updateMutation.isPending) return
    updateMutation.mutate()
  }

  function handleTypeSelect(value: string) {
    if (value === '__CUSTOM__') {
      setCustomType(true)
      setAgentType('')
      return
    }
    setCustomType(false)
    setAgentType(value)
  }

  return (
    <section className="admin-content-form">
      <AdminPageHeader title={t('detail.nav.basic')} description={t('basic.description')} />
      <article className="rounded-xl border border-admin bg-admin-surface p-5">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="space-y-1.5 text-sm text-admin">
            <span className="font-medium">{t('list.columns.agentName')}</span>
            <input
              value={agentName}
              onChange={(event) => setAgentName(event.target.value)}
              disabled={!canUpdate}
              className={adminFieldClass}
            />
          </label>

          <label className="space-y-1.5 text-sm text-admin">
            <span className="font-medium">{t('create.fields.agentCode')}</span>
            <input value={site.agentCode} disabled className={adminFieldClass} />
          </label>

          <label className="space-y-1.5 text-sm text-admin">
            <span className="font-medium">{t('create.fields.agentType')}</span>
            <select
              value={customType ? '__CUSTOM__' : agentType}
              onChange={(event) => handleTypeSelect(event.target.value)}
              disabled={!canUpdate}
              className={adminFieldClass}
            >
              {SITE_TYPE_PRESETS.map((value) => (
                <option key={value} value={value}>
                  {t(`agentType.${value}`)}
                </option>
              ))}
              {!customType && agentType && !SITE_TYPE_PRESETS.includes(agentType) ? (
                <option value={agentType}>{t(`agentType.${agentType}`, { defaultValue: agentType })}</option>
              ) : null}
              <option value="__CUSTOM__">{t('create.fields.customAgentType')}</option>
            </select>
            {customType ? (
              <input
                value={agentType}
                onChange={(event) => setAgentType(event.target.value)}
                disabled={!canUpdate}
                className={adminFieldClass}
                placeholder={t('create.placeholders.customAgentType')}
              />
            ) : null}
          </label>

          <label className="space-y-1.5 text-sm text-admin">
            <span className="font-medium">{t('create.fields.defaultDomain')}</span>
            <input
              value={defaultDomain}
              onChange={(event) => setDefaultDomain(event.target.value)}
              disabled={!canUpdate}
              placeholder={t('create.placeholders.domain')}
              className={adminFieldClass}
            />
          </label>

          <label className="space-y-1.5 text-sm text-admin sm:col-span-2">
            <span className="font-medium">{t('create.fields.agentDescription')}</span>
            <textarea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              disabled={!canUpdate}
              rows={4}
              className={adminFieldClass}
            />
          </label>
        </div>

        <div className="mt-5 flex items-center justify-end gap-3 border-t border-admin pt-4">
          {canUpdate ? (
            <button
              type="button"
              onClick={handleSave}
              disabled={!dirty || updateMutation.isPending}
              className={adminPrimaryButtonClass}
            >
              {updateMutation.isPending ? t('basic.actions.saving') : t('basic.actions.save')}
            </button>
          ) : (
            <p className="text-xs text-admin-muted">{t('basic.noPermission')}</p>
          )}
        </div>
      </article>

      <article className="mt-4 rounded-xl border border-admin bg-admin-surface p-5">
        <h2 className="text-base font-semibold text-admin">{t('basic.info.title')}</h2>
        <dl className="mt-4 grid gap-x-6 gap-y-4 sm:grid-cols-2">
          <div>
            <dt className="text-xs font-medium text-admin-muted">{t('list.columns.status')}</dt>
            <dd className="mt-1.5">
              <StatusTag status={site.status} label={t(`status.${site.status}`, { defaultValue: site.status })} />
            </dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-admin-muted">{t('basic.info.id')}</dt>
            <dd className="mt-1 font-mono text-sm tabular-nums text-admin">{site.id}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-admin-muted">{t('basic.info.createdAt')}</dt>
            <dd className="mt-1 text-sm tabular-nums text-admin">{formatDateTime(site.createdAt)}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-admin-muted">{t('list.columns.updatedAt')}</dt>
            <dd className="mt-1 text-sm tabular-nums text-admin">{formatDateTime(site.updatedAt)}</dd>
          </div>
          <div>
            <dt className="text-xs font-medium text-admin-muted">{t('basic.versionLabel')}</dt>
            <dd className="mt-1 text-sm tabular-nums text-admin">{site.version}</dd>
          </div>
        </dl>
      </article>
    </section>
  )
}
