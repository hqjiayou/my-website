import { ShieldAlert } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { ROUTES } from '@/router/config/routePaths'

export function ForbiddenPage() {
  const { t } = useTranslation('common')

  return (
    <section className="admin-content-data flex min-h-[50vh] flex-col items-center justify-center text-center">
      <div className="mb-4 rounded-full bg-danger-muted p-4 text-danger-emphasis">
        <ShieldAlert className="h-8 w-8" />
      </div>
      <h1 className="text-2xl font-semibold text-foreground">{t('forbidden.title')}</h1>
      <p className="mt-2 max-w-md text-sm text-muted-foreground">{t('forbidden.description')}</p>
      <Link
        to={ROUTES.dashboard}
        className="mt-6 inline-flex rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/92"
      >
        {t('forbidden.backHome')}
      </Link>
    </section>
  )
}
