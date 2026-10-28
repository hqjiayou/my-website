import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'
import { toast } from 'sonner'

import { agentApi, SITE_TAG_PATTERN, SITE_TYPE_PRESETS, type Site } from '@/api'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

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
    <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-semibold text-slate-900">{t('detail.nav.basic')}</h2>
      <p className="mt-1 text-sm text-slate-600">{t('basic.description')}</p>

      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('list.columns.agentName')}</span>
          <input
            value={agentName}
            onChange={(event) => setAgentName(event.target.value)}
            disabled={!canUpdate}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200 disabled:bg-slate-50"
          />
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.agentCode')}</span>
          <input
            value={site.agentCode}
            disabled
            className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-slate-500"
          />
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.agentType')}</span>
          <select
            value={customType ? '__CUSTOM__' : agentType}
            onChange={(event) => handleTypeSelect(event.target.value)}
            disabled={!canUpdate}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200 disabled:bg-slate-50"
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
              className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200 disabled:bg-slate-50"
              placeholder={t('create.placeholders.customAgentType')}
            />
          ) : null}
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.defaultDomain')}</span>
          <input
            value={defaultDomain}
            onChange={(event) => setDefaultDomain(event.target.value)}
            disabled={!canUpdate}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200 disabled:bg-slate-50"
          />
        </label>

        <label className="space-y-1.5 text-sm sm:col-span-2">
          <span className="font-medium text-slate-700">{t('create.fields.agentDescription')}</span>
          <textarea
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            disabled={!canUpdate}
            rows={4}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200 disabled:bg-slate-50"
          />
        </label>
      </div>

      <div className="mt-4 rounded-xl border border-dashed border-slate-200 bg-slate-50 px-3 py-3 text-sm text-slate-600">
        <p className="font-medium text-slate-700">{t('basic.laterStage.title')}</p>
        <p className="mt-1">{t('basic.laterStage.description')}</p>
      </div>

      <div className="mt-4 flex items-center justify-between gap-3">
        <p className="text-xs text-slate-500">
          {t('basic.versionLabel')}: {site.version}
        </p>
        {canUpdate ? (
          <button
            type="button"
            onClick={handleSave}
            disabled={updateMutation.isPending}
            className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white transition hover:bg-slate-800 disabled:opacity-60"
          >
            {updateMutation.isPending ? t('basic.actions.saving') : t('basic.actions.save')}
          </button>
        ) : (
          <p className="text-xs text-slate-500">{t('basic.noPermission')}</p>
        )}
      </div>
    </article>
  )
}
