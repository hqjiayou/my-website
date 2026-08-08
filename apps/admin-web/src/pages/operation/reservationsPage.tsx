import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/page'

/**
 * 运营全局预约中心尚未接入真实后端。
 * 站点级预约管理见 `/apps/sites/:siteId/reservations`（reservationApi）。
 */
export function OperationReservationsPage() {
  const { t } = useTranslation('operation')

  return (
    <section>
      <PageHeader title={t('reservations.title')} description={t('reservations.description')} />
      <article className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-600">
        <p className="font-medium text-slate-800">{t('reservations.notConnected.title')}</p>
        <p className="mt-1">{t('reservations.notConnected.description')}</p>
      </article>
    </section>
  )
}
