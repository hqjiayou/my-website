import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api'
import { PageHeader } from '@/components/page'

export function AiPromptsPage() {
  const { t } = useTranslation('aiAdmin')
  const { data: prompts = [] } = useQuery({
    queryKey: ['ai', 'prompts'],
    queryFn: aiApi.listPrompts,
  })

  return (
    <section>
      <PageHeader title={t('prompts.title')} description={t('prompts.description')} />
      <div className="grid gap-3 md:grid-cols-2">
        {prompts.map((prompt) => (
          <article key={prompt.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-sm font-medium text-slate-900">{prompt.title}</p>
            <p className="mt-1 text-xs text-slate-500">{prompt.scene}</p>
            <p className="mt-2 text-sm text-slate-700">{prompt.content}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
