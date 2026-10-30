import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'

export function DashboardPage() {
  const { t } = useTranslation('dashboard')

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('title')} description={t('description')} />

      <div className="rounded-xl border border-dashed border-border bg-card-muted p-5 text-sm text-muted-foreground">
        <p className="font-medium text-foreground">{t('notConnected.title')}</p>
        <p className="mt-1">{t('notConnected.description')}</p>
      </div>

      <div className="mt-6 grid gap-4 xl:grid-cols-5">
        <article className="rounded-xl border border-border bg-card p-5 xl:col-span-3">
          <h2 className="text-base font-semibold text-foreground">{t('trend.title')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t('trend.description')}</p>
          <div className="mt-4 h-56 rounded-lg border border-dashed border-border bg-card-muted" />
        </article>

        <article className="rounded-xl border border-border bg-card p-5 xl:col-span-2">
          <h2 className="text-base font-semibold text-foreground">{t('reminders.title')}</h2>
          <p className="mt-3 text-sm text-muted-foreground">{t('reminders.empty')}</p>
        </article>
      </div>
    </section>
  )
}
