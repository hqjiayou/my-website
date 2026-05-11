import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import type { SiteWorkspaceOutletContext } from './site-workspace-layout'

export function SiteThemePage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<SiteWorkspaceOutletContext>()

  return (
    <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-base font-semibold text-slate-900">{t('detail.nav.theme')}</h2>
      <p className="mt-1 text-sm text-slate-600">{t('detail.placeholders.themeFuture')}</p>

      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs text-slate-500">{t('detail.labels.primaryColor')}</p>
          <div className="mt-2 flex items-center gap-2">
            <span className="h-4 w-4 rounded-full border border-slate-200" style={{ backgroundColor: site.primaryColor }} />
            <span className="text-sm font-medium text-slate-900">{site.primaryColor}</span>
          </div>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs text-slate-500">{t('detail.placeholders.logo')}</p>
          <div className="mt-2 h-16 rounded-md border border-dashed border-slate-300 bg-white" />
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs text-slate-500">{t('detail.placeholders.buttonStyle')}</p>
          <p className="mt-1 text-sm font-medium text-slate-900">Primary CTA</p>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs text-slate-500">{t('detail.placeholders.fontStyle')}</p>
          <p className="mt-1 text-sm font-medium text-slate-900">Aa 123</p>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs text-slate-500">{t('detail.placeholders.heroStyle')}</p>
          <p className="mt-1 text-sm font-medium text-slate-900">{site.templateCode}</p>
        </div>
        <div className="rounded-lg border border-slate-100 bg-slate-50 p-3">
          <p className="text-xs text-slate-500">{t('detail.placeholders.templateHint')}</p>
          <p className="mt-1 text-sm font-medium text-slate-900">{t(`industry.${site.industry}`)}</p>
        </div>
      </div>
    </article>
  )
}
