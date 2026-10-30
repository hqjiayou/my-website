import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'

export function DashboardPage() {
  const { t } = useTranslation('dashboard')

  return (
    <section>
      <AdminPageHeader title={t('title')} description={t('description')} />

      <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-5 text-sm text-slate-600 shadow-sm">
        <p className="font-medium text-slate-800">{t('notConnected.title')}</p>
        <p className="mt-1">{t('notConnected.description')}</p>
      </div>

      <div className="mt-6 grid gap-4 xl:grid-cols-5">
        <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm xl:col-span-3">
          <h2 className="text-base font-semibold text-slate-900">{t('trend.title')}</h2>
          <p className="mt-1 text-sm text-slate-500">{t('trend.description')}</p>
          <div className="mt-4 h-56 rounded-lg border border-dashed border-slate-300 bg-slate-50" />
        </article>

        <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm xl:col-span-2">
          <h2 className="text-base font-semibold text-slate-900">{t('reminders.title')}</h2>
          <p className="mt-3 text-sm text-slate-500">{t('reminders.empty')}</p>
        </article>
      </div>
    </section>
  )
}
