import type { ReactNode } from 'react'

import { useHasPermission, usePermissions } from './hooks'
import { hasPermission } from './utils'

interface PermissionGateProps {
  code: string
  children: ReactNode
}

export function PermissionGate({ code, children }: PermissionGateProps) {
  const allowed = useHasPermission(code)
  if (!allowed) return null
  return children
}

interface CanAccessProps {
  code?: string
  anyOf?: string[]
  allOf?: string[]
  fallback?: ReactNode
  children: ReactNode
}

/** 按钮/区块级权限门控；支持单码、任一、全部。 */
export function CanAccess({ code, anyOf, allOf, fallback = null, children }: CanAccessProps) {
  const permissions = usePermissions()

  let allowed = true
  if (code) {
    allowed = hasPermission(permissions, code)
  } else if (anyOf?.length) {
    allowed = anyOf.some((item) => hasPermission(permissions, item))
  } else if (allOf?.length) {
    allowed = allOf.every((item) => hasPermission(permissions, item))
  }

  if (!allowed) return fallback
  return children
}
