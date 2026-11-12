import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

type RouteFallbackProps = {
  /** 登录等全屏路由用；布局内主内容区保持局部占位 */
  fullScreen?: boolean
}

/** Route-level code splitting 的 Suspense fallback：骨架条占位，形态与内容区同高。 */
export function RouteFallback({ fullScreen = false }: RouteFallbackProps) {
  const { t } = useTranslation('common')

  return (
    <div
      role="status"
      aria-label={t('table.loading')}
      className={cn(
        'flex w-full justify-center px-4 py-6',
        fullScreen ? 'min-h-screen bg-background' : 'min-h-48',
      )}
    >
      <div aria-hidden className="w-full max-w-3xl space-y-4">
        <div className="admin-skeleton h-6 w-48" />
        <div className="admin-skeleton h-4 w-full" />
        <div className="admin-skeleton h-4 w-5/6" />
        <div className="admin-skeleton h-4 w-4/6" />
        <div className="admin-skeleton h-4 w-3/6" />
      </div>
    </div>
  )
}
