import { Suspense } from 'react'
import { Outlet } from 'react-router-dom'

import { RouteFallback } from '@/components/page'

/** 主内容区：路由页面通过 `<Outlet />` 渲染；Suspense 承接 route-level lazy。 */
export function MainContainer() {
  return (
    <main className="p-[var(--admin-page-padding)] sm:p-[var(--admin-page-padding)]">
      <Suspense fallback={<RouteFallback />}>
        <Outlet />
      </Suspense>
    </main>
  )
}
