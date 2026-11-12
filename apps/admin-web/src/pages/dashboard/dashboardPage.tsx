import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'

export function DashboardPage() {
  const { t } = useTranslation('dashboard')

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('title')} description={t('description')} />

      <div className="rounded-xl border border-dashed border-admin bg-admin-surface-muted p-5 text-sm text-admin-muted">
        <p className="font-medium text-admin">{t('notConnected.title')}</p>
        <p className="mt-1">{t('notConnected.description')}</p>
      </div>

      <div className="mt-6 grid gap-4 xl:grid-cols-5">
        <article className="rounded-xl border border-admin bg-admin-surface p-5 xl:col-span-3">
          <h2 className="text-base font-semibold text-admin">{t('trend.title')}</h2>
          <p className="mt-1 text-sm text-admin-muted">{t('trend.description')}</p>
          <div className="mt-4 h-56 rounded-lg border border-dashed border-admin bg-admin-surface-muted" />
        </article>

        <article className="rounded-xl border border-admin bg-admin-surface p-5 xl:col-span-2">
          <h2 className="text-base font-semibold text-admin">{t('reminders.title')}</h2>
          <p className="mt-3 text-sm text-admin-muted">{t('reminders.empty')}</p>
        </article>
      </div>
    </section>
  )
}
