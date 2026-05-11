import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import type { SiteWorkspaceOutletContext } from './site-workspace-layout'

export function SiteDomainPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<SiteWorkspaceOutletContext>()

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-semibold text-slate-900">{t('detail.nav.domain')}</h2>
      <p className="mt-1 text-sm text-slate-600">{t('detail.placeholders.domainFuture')}</p>

      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs text-slate-500">{t('detail.labels.domain')}</p>
          <p className="mt-1 text-sm font-medium text-slate-900">{site.domain}</p>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs text-slate-500">{t('detail.labels.status')}</p>
          <p className="mt-1 text-sm font-medium text-slate-900">{t(`status.${site.status}`)}</p>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs text-slate-500">{t('detail.labels.dnsStatus')}</p>
          <p className="mt-1 text-sm font-medium text-slate-900">{t('detail.placeholders.dnsHint')}</p>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs text-slate-500">{t('detail.labels.sslStatus')}</p>
          <p className="mt-1 text-sm font-medium text-slate-900">{t('detail.placeholders.cdnHint')}</p>
        </div>
      </div>
    </article>
  )
}
