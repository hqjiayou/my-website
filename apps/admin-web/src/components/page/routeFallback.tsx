import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

type RouteFallbackProps = {
  /** 登录等全屏路由用；布局内主内容区保持局部占位 */
  fullScreen?: boolean
}

/** Route-level code splitting 的 Suspense fallback。 */
export function RouteFallback({ fullScreen = false }: RouteFallbackProps) {
  const { t } = useTranslation('common')

  return (
    <div
      className={cn(
        'flex items-center justify-center text-sm text-muted-foreground',
        fullScreen ? 'min-h-screen bg-background' : 'min-h-48',
      )}
    >
      {t('table.loading')}
    </div>
  )
}
