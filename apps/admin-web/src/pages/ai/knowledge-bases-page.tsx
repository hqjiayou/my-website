import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api'
import { PageHeader } from '@/components/page'

export function AiKnowledgeBasesPage() {
  const { t } = useTranslation('aiAdmin')
  const { data: knowledgeBases = [] } = useQuery({
    queryKey: ['ai', 'knowledge-bases'],
    queryFn: () => aiApi.listKnowledgeBases(),
  })

  return (
    <section>
      <PageHeader title={t('knowledgeBases.title')} description={t('knowledgeBases.description')} />
      <div className="grid gap-3 md:grid-cols-2">
        {knowledgeBases.map((kb) => (
          <article key={kb.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-sm font-medium text-slate-900">{kb.name}</p>
            <p className="mt-1 text-xs text-slate-500">{kb.siteId}</p>
            <p className="mt-1 text-xs text-slate-600">docs: {kb.documentCount}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
