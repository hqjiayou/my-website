import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/ai-api'
import { PageHeader } from '@/components/page'

export function AiConversationsPage() {
  const { t } = useTranslation('aiAdmin')
  const { data: conversations = [] } = useQuery({
    queryKey: ['ai', 'conversations'],
    queryFn: () => aiApi.listConversations(),
  })

  return (
    <section>
      <PageHeader title={t('conversations.title')} description={t('conversations.description')} />
      <div className="grid gap-3 md:grid-cols-2">
        {conversations.map((item) => (
          <article key={item.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-sm font-medium text-slate-900">{item.visitorName}</p>
            <p className="mt-1 text-xs text-slate-500">{item.siteId}</p>
            <p className="mt-2 text-sm text-slate-700">{item.question}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
