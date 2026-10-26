import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

import { useAuthStore } from '@/stores/authStore'
import { setSessionExpiredHandler } from '@/utils/http/axios'

import { ROUTES } from './config/routePaths'
import { AppRoutes } from './routes/appRoutes'

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

export function AppRouter() {
  return (
    <>
      <SessionBridge />
      <AppRoutes />
    </>
  )
}
