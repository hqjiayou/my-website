import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'
import { toast } from 'sonner'

import { agentApi } from '@/api'
import { Spinner, StatusTag } from '@/components/admin'
import { adminFieldClass, adminPrimaryButtonClass } from '@/components/admin/controls'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}

export function AgentBasicPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.agentUpdate)

  const [agentName, setAgentName] = useState(site.agentName)
  const [defaultDomain, setDefaultDomain] = useState(site.defaultDomain ?? '')
  const [description, setDescription] = useState(site.description ?? '')

  const dirty =
    agentName.trim() !== site.agentName ||
    defaultDomain.trim() !== (site.defaultDomain ?? '') ||
    description.trim() !== (site.description ?? '')

  const updateMutation = useMutation({
    mutationFn: () =>
      agentApi.updateSite(site.id, {
        agentName: agentName.trim(),
        agentType: site.agentType,
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
      void queryClient.invalidateQueries({ queryKey: ['sites'] })
      void queryClient.invalidateQueries({ queryKey: ['sites', site.id] })
    },
  })

  function handleSave() {
    if (!canUpdate || !dirty) return
    if (!agentName.trim()) {
      toast.warning(t('create.messages.requiredFields'))
      return
    }
    if (updateMutation.isPending) return
    updateMutation.mutate()
  }

  return (
    <section className="w-full space-y-3">
      <article className="rounded-xl border border-admin bg-admin-surface p-5">
        <div className="mb-4 flex flex-wrap items-center gap-2 border-b border-admin pb-3">
          <StatusTag status={site.status} label={t(`status.${site.status}`, { defaultValue: site.status })} />
          <span className="font-mono text-xs text-admin-muted">#{site.id}</span>
          <span className="text-xs text-admin-muted">·</span>
          <span className="text-xs text-admin-muted">
            {t('list.columns.updatedAt')} {formatDateTime(site.updatedAt)}
          </span>
          <span className="text-xs text-admin-muted">·</span>
          <span className="text-xs text-admin-muted">
            {t('basic.versionLabel')} {site.version}
          </span>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <label className="space-y-1.5 text-sm">
            <span className="text-xs font-medium text-admin-muted">{t('list.columns.agentName')}</span>
            <input
              value={agentName}
              onChange={(event) => setAgentName(event.target.value)}
              disabled={!canUpdate}
              className={adminFieldClass}
            />
          </label>

          <label className="space-y-1.5 text-sm">
            <span className="text-xs font-medium text-admin-muted">{t('create.fields.agentCode')}</span>
            <input value={site.agentCode} disabled className={adminFieldClass} />
          </label>

          <label className="space-y-1.5 text-sm md:col-span-2">
            <span className="text-xs font-medium text-admin-muted">{t('create.fields.defaultDomain')}</span>
            <input
              value={defaultDomain}
              onChange={(event) => setDefaultDomain(event.target.value)}
              disabled={!canUpdate}
              placeholder={t('create.placeholders.domain')}
              className={adminFieldClass}
            />
          </label>

          <label className="space-y-1.5 text-sm md:col-span-2">
            <span className="text-xs font-medium text-admin-muted">{t('create.fields.agentDescription')}</span>
            <textarea
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              disabled={!canUpdate}
              rows={5}
              className={adminFieldClass}
            />
          </label>
        </div>

        <div className="mt-5 flex items-center justify-between gap-3 border-t border-admin pt-4">
          <p className="text-xs text-admin-muted">
            {t('basic.info.createdAt')} {formatDateTime(site.createdAt)}
          </p>
          {canUpdate ? (
            <button
              type="button"
              onClick={handleSave}
              disabled={!dirty || updateMutation.isPending}
              className={adminPrimaryButtonClass}
            >
              {updateMutation.isPending ? <Spinner /> : null}
              {updateMutation.isPending ? t('basic.actions.saving') : t('basic.actions.save')}
            </button>
          ) : (
            <p className="text-xs text-admin-muted">{t('basic.noPermission')}</p>
          )}
        </div>
      </article>
    </section>
  )
}
