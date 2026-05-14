import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api'
import { PageHeader } from '@/components/page'

export function AiSettingsPage() {
  const { t } = useTranslation('aiAdmin')
  const { data: configs = [] } = useQuery({
    queryKey: ['ai', 'settings'],
    queryFn: () => aiApi.listAiConfigs(),
  })

  return (
    <section>
      <PageHeader title={t('settings.title')} description={t('settings.description')} />
      <div className="grid gap-3 md:grid-cols-2">
        {configs.map((config) => (
          <article key={config.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <p className="text-sm font-medium text-slate-900">{config.siteId}</p>
            <p className="mt-1 text-xs text-slate-600">{config.model}</p>
            <p className="mt-1 text-xs text-slate-500">temperature: {config.temperature}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
