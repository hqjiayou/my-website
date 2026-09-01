import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/page'
import { SiteAiConversationsPanel } from '@/pages/apps/sites/siteAiConversationsPanel'

import { AiSiteScope } from './aiSiteScope'

export function AiConversationsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section>
      <PageHeader title={t('conversations.title')} description={t('conversations.description')} />
      <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <AiSiteScope>{(siteId) => <SiteAiConversationsPanel siteId={siteId} />}</AiSiteScope>
      </article>
    </section>
  )
}
