import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { FullScreenStatus } from '@/components/screen'
import { useAuthStore } from '@/stores/authStore'

import { ROUTES } from '../config/routePaths'

export function AuthGuard() {
  const location = useLocation()
  const status = useAuthStore((state) => state.status)

  if (status === 'idle' || status === 'loading') {
    return <FullScreenStatus text="正在還原登入…" />
  }

  if (status !== 'authenticated') {
    return <Navigate to={ROUTES.login} replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}
