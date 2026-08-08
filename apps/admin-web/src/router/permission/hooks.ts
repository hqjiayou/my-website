import { useAuthStore } from '@/stores/authStore'

import { hasPermission } from './utils'

export function usePermissions() {
  return useAuthStore((state) => state.permissions)
}

export function useHasPermission(code: string) {
  const permissions = usePermissions()
  return hasPermission(permissions, code)
}
