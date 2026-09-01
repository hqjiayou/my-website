import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/page'

export function AiSettingsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section>
      <PageHeader title={t('settings.title')} description={t('settings.description')} />
      <article className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <p className="text-sm text-slate-700">{t('settings.body')}</p>
        <p className="text-sm text-slate-600">{t('settings.envHint')}</p>
        <p className="text-sm text-slate-600">{t('settings.blockedHint')}</p>
      </article>
    </section>
  )
}
