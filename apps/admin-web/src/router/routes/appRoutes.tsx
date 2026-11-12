import { Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'

import { RouteFallback } from '@/components/page'
import { AdminLayout } from '@/layouts'
import { NotFoundPage } from '@/pages/notFound/notFoundPage'

import { ROUTES } from '../config/routePaths'
import { AuthGuard, GuestGuard, PermissionGuard } from '../guards'

import { adminRoutes } from './adminRoutes'
import { lazyNamed } from './lazyNamed'
import { legacyRedirects } from './legacyRedirects'

const LoginPage = lazyNamed(() => import('@/pages/login/loginPage'), 'LoginPage')

/**
 * 全站路由表（静态注册）。
 *
 * 侧栏菜单来自接口 /admin/auth/me，只控制「显示哪些入口」；
 * 页面组件仍须在此声明 <Route />，否则直链 URL 无法渲染。
 *
 * 页面组件按路由 lazy；AuthGuard / PermissionGuard 保持同步，权限判定不依赖 chunk 加载。
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route element={<GuestGuard />}>
        <Route
          path={ROUTES.login}
          element={
            <Suspense fallback={<RouteFallback fullScreen />}>
              <LoginPage />
            </Suspense>
          }
        />
      </Route>

      <Route element={<AuthGuard />}>
        <Route element={<PermissionGuard />}>
          <Route element={<AdminLayout />}>{adminRoutes}</Route>
          {legacyRedirects}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
