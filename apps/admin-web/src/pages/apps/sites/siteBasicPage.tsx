import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'
import { toast } from 'sonner'

import { siteApi, SITE_TYPE_VALUES, type Site, type SiteType } from '@/api'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import type { SiteWorkspaceOutletContext } from './siteWorkspaceLayout'

export function SiteBasicPage() {
  const { site } = useOutletContext<SiteWorkspaceOutletContext>()
  // site 刷新后以 id+version 强制重建表单，避免 effect 内同步 setState
  return <SiteBasicForm key={`${site.id}-${site.version}`} site={site} />
}

function SiteBasicForm({ site }: { site: Site }) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.siteUpdate)

  const [siteName, setSiteName] = useState(site.siteName)
  const [siteType, setSiteType] = useState(site.siteType)
  const [defaultDomain, setDefaultDomain] = useState(site.defaultDomain ?? '')
  const [description, setDescription] = useState(site.description ?? '')

  const updateMutation = useMutation({
    mutationFn: () =>
      siteApi.updateSite(site.id, {
        siteName: siteName.trim(),
        siteType,
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
    if (!siteName.trim()) {
      toast.warning(t('create.messages.requiredFields'))
      return
    }
    if (updateMutation.isPending) return
    updateMutation.mutate()
  }

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-semibold text-slate-900">{t('detail.nav.basic')}</h2>
      <p className="mt-1 text-sm text-slate-600">{t('basic.description')}</p>

      <div className="mt-4 grid gap-4 sm:grid-cols-2">
        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('list.columns.siteName')}</span>
          <input
            value={siteName}
            onChange={(event) => setSiteName(event.target.value)}
            disabled={!canUpdate}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200 disabled:bg-slate-50"
          />
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.siteCode')}</span>
          <input
            value={site.siteCode}
            disabled
            className="w-full rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-slate-500"
          />
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.siteType')}</span>
          <select
            value={siteType}
            onChange={(event) => setSiteType(event.target.value)}
            disabled={!canUpdate}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200 disabled:bg-slate-50"
          >
            {SITE_TYPE_VALUES.map((value) => (
              <option key={value} value={value}>
                {t(`siteType.${value}`)}
              </option>
            ))}
            {!SITE_TYPE_VALUES.includes(siteType as SiteType) ? (
              <option value={siteType}>{siteType}</option>
            ) : null}
          </select>
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
          <span className="font-medium text-slate-700">{t('create.fields.siteDescription')}</span>
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
