import { useEffect } from 'react'

import { AppRouter } from '@/router'
import { useAuthStore } from '@/stores/authStore'

export default function App() {
  const status = useAuthStore((state) => state.status)
  const bootstrap = useAuthStore((state) => state.bootstrap)

  useEffect(() => {
    if (status === 'idle') void bootstrap()
  }, [bootstrap, status])

  return <AppRouter />
}
