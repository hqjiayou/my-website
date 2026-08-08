import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { selectHasToken, useAuthStore } from '@/stores/authStore'

import { ROUTES } from '../config/routePaths'

/** 登录态守卫：校验 token、恢复用户信息。 */
export function AuthGuard() {
  const { t } = useTranslation('common')
  const location = useLocation()
  const hasToken = useAuthStore(selectHasToken)
  const status = useAuthStore((state) => state.status)
  const loadCurrentUser = useAuthStore((state) => state.loadCurrentUser)

  useEffect(() => {
    if (!hasToken || status !== 'idle') return
    void loadCurrentUser()
  }, [hasToken, loadCurrentUser, status])

  if (!hasToken) {
    return <Navigate to={ROUTES.login} replace state={{ from: location.pathname }} />
  }

  if (status === 'idle' || status === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-100 text-sm text-slate-500">
        {t('auth.restoringSession')}
      </div>
    )
  }

  if (status === 'unauthenticated') {
    return <Navigate to={ROUTES.login} replace state={{ from: location.pathname }} />
  }

  return <Outlet />
}
