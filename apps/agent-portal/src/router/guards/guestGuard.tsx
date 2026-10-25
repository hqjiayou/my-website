import { Navigate, Outlet } from 'react-router-dom'

import { FullScreenStatus } from '@/components/screen'
import { useAuthStore } from '@/stores/authStore'

import { ROUTES } from '../config/routePaths'

export function GuestGuard() {
  const status = useAuthStore((state) => state.status)

  if (status === 'idle' || status === 'loading') {
    return <FullScreenStatus text="正在還原登入…" />
  }

  if (status === 'authenticated') {
    return <Navigate to={ROUTES.home} replace />
  }

  return <Outlet />
}
