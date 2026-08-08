import { Navigate, Outlet } from 'react-router-dom'

import { selectHasToken, selectIsAuthenticated, useAuthStore } from '@/stores/authStore'

import { ROUTES } from '../config/routePaths'

/** 访客区守卫：未登录可访问（如登录页）；已登录则进后台首页。 */
export function GuestGuard() {
  const isAuthenticated = useAuthStore(selectIsAuthenticated)
  const hasToken = useAuthStore(selectHasToken)

  if (isAuthenticated || hasToken) {
    return <Navigate to={ROUTES.dashboard} replace />
  }

  return <Outlet />
}
