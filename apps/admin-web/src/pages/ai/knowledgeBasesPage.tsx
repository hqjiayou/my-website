import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/page'
import { SiteAiKnowledgePanel } from '@/pages/apps/sites/siteAiKnowledgePanel'

import { AiSiteScope } from './aiSiteScope'

export function AiKnowledgeBasesPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section>
      <PageHeader title={t('knowledgeBases.title')} description={t('knowledgeBases.description')} />
      <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <AiSiteScope>{(siteId) => <SiteAiKnowledgePanel siteId={siteId} />}</AiSiteScope>
      </article>
    </section>
  )
}
