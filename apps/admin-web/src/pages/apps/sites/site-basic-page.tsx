import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import type { SiteWorkspaceOutletContext } from './site-workspace-layout'

export function SiteBasicPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<SiteWorkspaceOutletContext>()

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-semibold text-slate-900">{t('detail.nav.basic')}</h2>
      <p className="mt-1 text-sm text-slate-600">{t('detail.placeholders.pendingSave')}</p>

      <dl className="mt-4 grid gap-3 sm:grid-cols-2">
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <dt className="text-xs text-slate-500">{t('list.columns.siteName')}</dt>
          <dd className="mt-1 text-sm font-medium text-slate-900">{site.name}</dd>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <dt className="text-xs text-slate-500">{t('create.fields.siteCode')}</dt>
          <dd className="mt-1 text-sm font-medium text-slate-900">{site.code}</dd>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <dt className="text-xs text-slate-500">{t('detail.labels.industry')}</dt>
          <dd className="mt-1 text-sm font-medium text-slate-900">{t(`industry.${site.industry}`)}</dd>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <dt className="text-xs text-slate-500">{t('detail.labels.template')}</dt>
          <dd className="mt-1 text-sm font-medium text-slate-900">{site.templateCode}</dd>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3 sm:col-span-2">
          <dt className="text-xs text-slate-500">{t('create.fields.siteDescription')}</dt>
          <dd className="mt-1 text-sm font-medium text-slate-900">{site.description}</dd>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <dt className="text-xs text-slate-500">{t('detail.labels.phone')}</dt>
          <dd className="mt-1 text-sm font-medium text-slate-900">{site.phone}</dd>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <dt className="text-xs text-slate-500">{t('detail.labels.businessHours')}</dt>
          <dd className="mt-1 text-sm font-medium text-slate-900">{site.businessHours}</dd>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3 sm:col-span-2">
          <dt className="text-xs text-slate-500">{t('detail.labels.address')}</dt>
          <dd className="mt-1 text-sm font-medium text-slate-900">{site.address}</dd>
        </div>
      </dl>
    </article>
  )
}
