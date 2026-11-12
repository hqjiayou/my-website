import { Compass } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { ROUTES } from '@/router/config/routePaths'

/**
 * 404 专属页：catch-all 路由不在 AdminLayout 内，
 * 页面自带全屏居中容器，不依赖布局壳的内容区约束。
 */
export function NotFoundPage() {
  const { t } = useTranslation('common')

  return (
    <main className="flex min-h-screen flex-col items-center justify-center bg-background px-4 text-center">
      <div className="mb-4 rounded-full bg-primary/10 p-4 text-primary">
        <Compass className="h-8 w-8" />
      </div>
      <h1 className="text-2xl font-semibold text-foreground">{t('notFound.title')}</h1>
      <p className="mt-2 max-w-md text-sm text-muted-foreground">{t('notFound.description')}</p>
      <Link
        to={ROUTES.dashboard}
        className="mt-6 inline-flex rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/92"
      >
        {t('notFound.backHome')}
      </Link>
    </main>
  )
}
