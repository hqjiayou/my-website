import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import { PageHeader } from '@/components/page'

import type { SiteWorkspaceOutletContext } from './siteWorkspaceLayout'

export function SiteAiPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<SiteWorkspaceOutletContext>()

  return (
    <section>
      <PageHeader title={t('workspace.nav.ai')} description={t('placeholderPages.ai.description')} />
      <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <p className="text-sm text-slate-500">{t('placeholderPages.currentSiteLabel')}</p>
        <p className="mt-1 text-base font-semibold text-slate-900">{site.siteName}</p>
        <p className="mt-4 rounded-xl border border-dashed border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-700">
          {t('workspace.placeholderNotice')}
        </p>
      </article>
    </section>
  )
}
