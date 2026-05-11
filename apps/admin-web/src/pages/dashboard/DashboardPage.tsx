import { useTranslation } from 'react-i18next'

import { PageHeader, StatCard } from '@/components/page'

const STAT_KEYS = ['activeSites', 'todayReservations', 'aiSessions', 'uptime'] as const

export function DashboardPage() {
  const { t } = useTranslation('dashboard')
  const remindersUnknown = t('reminders.items', { returnObjects: true })
  const reminders = Array.isArray(remindersUnknown) ? (remindersUnknown as string[]) : []

  return (
    <section>
      <PageHeader title={t('title')} description={t('description')} />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {STAT_KEYS.map((key) => (
          <StatCard
            key={key}
            label={t(`stats.${key}.label`)}
            value={t(`stats.${key}.value`)}
            trend={t(`stats.${key}.trend`)}
          />
        ))}
      </div>

      <div className="mt-6 grid gap-4 xl:grid-cols-5">
        <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm xl:col-span-3">
          <h2 className="text-base font-semibold text-slate-900">{t('trend.title')}</h2>
          <p className="mt-1 text-sm text-slate-500">{t('trend.description')}</p>
          <div className="mt-4 h-56 rounded-lg border border-dashed border-slate-300 bg-slate-50" />
        </article>

        <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm xl:col-span-2">
          <h2 className="text-base font-semibold text-slate-900">{t('reminders.title')}</h2>
          <ul className="mt-3 space-y-2 text-sm text-slate-600">
            {reminders.map((item) => (
              <li key={item}>{item}</li>
            ))}
          </ul>
        </article>
      </div>
    </section>
  )
}
