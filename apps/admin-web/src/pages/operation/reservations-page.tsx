import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { reservationApi } from '@/api'
import { PageHeader } from '@/components/page'

export function OperationReservationsPage() {
  const { t } = useTranslation('operation')
  const { data: reservations = [] } = useQuery({
    queryKey: ['operation', 'reservations'],
    queryFn: () => reservationApi.listReservations(),
  })

  return (
    <section>
      <PageHeader title={t('reservations.title')} description={t('reservations.description')} />
      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">{t('reservations.columns.customer')}</th>
                <th className="px-4 py-3">{t('reservations.columns.site')}</th>
                <th className="px-4 py-3">{t('reservations.columns.service')}</th>
                <th className="px-4 py-3">{t('reservations.columns.reservedAt')}</th>
                <th className="px-4 py-3">{t('reservations.columns.status')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {reservations.map((reservation) => (
                <tr key={reservation.id}>
                  <td className="px-4 py-3 text-slate-900">{reservation.customerName}</td>
                  <td className="px-4 py-3 text-slate-700">{reservation.siteId}</td>
                  <td className="px-4 py-3 text-slate-700">{reservation.serviceType}</td>
                  <td className="px-4 py-3 text-slate-700">{new Date(reservation.reservedAt).toLocaleString()}</td>
                  <td className="px-4 py-3 text-slate-700">{t(`reservations.status.${reservation.status}`)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}
