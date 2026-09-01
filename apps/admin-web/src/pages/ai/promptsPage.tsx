import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/page'
import { SiteAiPromptPanel } from '@/pages/apps/sites/siteAiPromptPanel'

import { AiSiteScope } from './aiSiteScope'

export function AiPromptsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section>
      <PageHeader title={t('prompts.title')} description={t('prompts.description')} />
      <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <AiSiteScope>{(siteId) => <SiteAiPromptPanel siteId={siteId} />}</AiSiteScope>
      </article>
    </section>
  )
}
