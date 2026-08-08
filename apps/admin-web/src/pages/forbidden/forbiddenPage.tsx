import { ShieldAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { ROUTES } from '@/router/config/routePaths'

export function ForbiddenPage() {
  const { t } = useTranslation('common')

  return (
    <section className="flex min-h-[50vh] flex-col items-center justify-center text-center">
      <div className="mb-4 rounded-full bg-rose-50 p-4 text-rose-600">
        <ShieldAlert className="h-8 w-8" />
      </div>
      <h1 className="text-2xl font-semibold text-slate-900">{t('forbidden.title')}</h1>
      <p className="mt-2 max-w-md text-sm text-slate-500">{t('forbidden.description')}</p>
      <Link
        to={ROUTES.dashboard}
        className="mt-6 inline-flex rounded-lg bg-slate-900 px-4 py-2 text-sm font-medium text-white transition hover:bg-slate-800"
      >
        {t('forbidden.backHome')}
      </Link>
    </section>
  )
}
