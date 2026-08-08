import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api'
import { PageHeader } from '@/components/page'
import { USE_MOCK } from '@/config/apiMode'

export function AiSettingsPage() {
  const { t } = useTranslation('aiAdmin')
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['ai', 'settings'],
    queryFn: () => aiApi.listAiConfigs(),
  })
  const configs = data ?? []

  return (
    <section>
      <PageHeader title={t('settings.title')} description={t('settings.description')} />
      {USE_MOCK ? (
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          {t('mockNotice')}
        </div>
      ) : null}
      {isPending ? (
        <p className="text-sm text-slate-500">{t('loading')}</p>
      ) : isError ? (
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          <p>{error instanceof Error ? error.message : t('error')}</p>
          <button type="button" onClick={() => void refetch()} className="mt-2 underline">
            {t('retry')}
          </button>
        </div>
      ) : configs.length === 0 ? (
        <p className="text-sm text-slate-500">{t('empty')}</p>
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {configs.map((config) => (
            <article key={config.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
              <p className="text-sm font-medium text-slate-900">{config.siteId}</p>
              <p className="mt-1 text-xs text-slate-600">{config.model}</p>
              <p className="mt-1 text-xs text-slate-500">temperature: {config.temperature}</p>
            </article>
          ))}
        </div>
      )}
    </section>
  )
}
