import { Outlet, useLocation } from 'react-router-dom'

import { ForbiddenPage } from '@/pages/forbidden/forbiddenPage'

import { hasPermission, resolveRoutePermission, usePermissions } from '../permission'

/** 路由权限守卫：按路径校验 RBAC，无权限展示 403 页。 */
export function PermissionGuard() {
  const location = useLocation()
  const permissions = usePermissions()

  const required = resolveRoutePermission(location.pathname)
  if (required && !hasPermission(permissions, required)) {
    return <ForbiddenPage />
  }

  return <Outlet />
}
