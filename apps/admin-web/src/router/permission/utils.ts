import type { PermissionDto, RoleBindingDto } from '@/types/rbac'
import { PERMISSION_CODES, SUPER_ADMIN_ROLE_CODE } from '@/types/rbac'

import { ROUTE_PERMISSIONS } from './constants'

export function hasPermission(permissions: string[], code: string) {
  return permissions.includes(code)
}

export function groupPermissionsByResourceType(permissions: PermissionDto[]) {
  const menu: PermissionDto[] = []
  const action: PermissionDto[] = []
  const other: PermissionDto[] = []

  for (const item of permissions) {
    if (item.resourceType === 'MENU') {
      menu.push(item)
    } else if (item.resourceType === 'ACTION') {
      action.push(item)
    } else {
      other.push(item)
    }
  }

  return { menu, action, other }
}

export function isSuperAdminRole(roleCode: string) {
  return roleCode === SUPER_ADMIN_ROLE_CODE
}

/** 从 /admin/auth/me 的 userCode（如 ADMIN_000100）解析数字 id，用于「自己」判断。 */
export function parseAdminUserId(userCode: string | null | undefined): number | null {
  if (!userCode) return null
  const match = /^ADMIN_(\d+)$/i.exec(userCode.trim())
  if (!match) return null
  const id = Number(match[1])
  return Number.isFinite(id) ? id : null
}

/** 持有 super_admin（或 binding.protectedRole）的用户不可停用 / 删除 / 改角色。 */
export function isProtectedUser(roles: RoleBindingDto[] | undefined | null) {
  if (!roles?.length) return false
  return roles.some((role) => role.protectedRole || isSuperAdminRole(role.roleCode))
}

export function resolveRoutePermission(pathname: string): string | undefined {
  if (ROUTE_PERMISSIONS[pathname]) {
    return ROUTE_PERMISSIONS[pathname]
  }

  if (/^\/system\/roles\/\d+\/edit$/.test(pathname)) {
    return PERMISSION_CODES.roleDetail
  }

  if (/^\/system\/roles\/\d+\/permissions$/.test(pathname)) {
    return PERMISSION_CODES.rolePermissionView
  }

  if (/^\/system\/users\/\d+\/edit$/.test(pathname)) {
    return PERMISSION_CODES.userDetail
  }

  if (/^\/system\/users\/\d+\/roles$/.test(pathname)) {
    return PERMISSION_CODES.userRoleView
  }

  // 站点媒体：list；须先于通用 workspace detail 匹配
  if (/^\/apps\/sites\/\d+\/files(?:\/|$)/.test(pathname)) {
    return PERMISSION_CODES.mediaList
  }

  // 站点内容：list；须先于通用 workspace detail 匹配
  if (/^\/apps\/sites\/\d+\/contents(?:\/|$)/.test(pathname)) {
    return PERMISSION_CODES.contentList
  }

  // 站点预约：list；须先于通用 workspace detail 匹配
  if (/^\/apps\/sites\/\d+\/reservations(?:\/|$)/.test(pathname)) {
    return PERMISSION_CODES.reservationList
  }

  // 站点 Workspace（含子页）需 detail；create 已在 ROUTE_PERMISSIONS 精确登记
  if (/^\/apps\/sites\/\d+(\/.*)?$/.test(pathname)) {
    return PERMISSION_CODES.siteDetail
  }

  if (/^\/apps\/templates\/\d+$/.test(pathname)) {
    return PERMISSION_CODES.templateDetail
  }

  return undefined
}
