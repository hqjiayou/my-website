import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { contentApi } from '@/api'
import { PageHeader } from '@/components/page'

export function OperationContentsPage() {
  const { t } = useTranslation('operation')
  const { data: contents = [] } = useQuery({
    queryKey: ['operation', 'contents'],
    queryFn: () => contentApi.listContents(),
  })

  return (
    <section>
      <PageHeader title={t('contents.title')} description={t('contents.description')} />
      <div className="grid gap-3 md:grid-cols-2">
        {contents.map((item) => (
          <article key={item.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">{item.title}</h2>
            <p className="mt-1 text-xs text-slate-500">{item.type}</p>
            <p className="mt-2 text-xs text-slate-600">{item.siteId}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
