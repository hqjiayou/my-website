import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

import { useAuthStore } from '@/stores/authStore'
import { setSessionExpiredHandler } from '@/utils/http/axios'

import { ROUTES } from './config/routePaths'
import { AppRoutes } from './routes/appRoutes'

/** 将 HTTP 401 会话过期与路由登出打通。 */
function SessionBridge() {
  const navigate = useNavigate()
  const clearAuth = useAuthStore((state) => state.clearAuth)

  useEffect(() => {
    setSessionExpiredHandler(() => {
      clearAuth()
      navigate(ROUTES.login, { replace: true })
    })
    return () => setSessionExpiredHandler(null)
  }, [clearAuth, navigate])

  return null
}

/** 应用路由入口（仅挂载；路径/权限从子路径按需 import，勿 barrel re-export）。 */
export function AppRouter() {
  return (
    <>
      <SessionBridge />
      <AppRoutes />
    </>
  )
}
