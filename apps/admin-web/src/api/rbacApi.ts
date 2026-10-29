/**
 * Admin RBAC API：用户 / 角色 / 权限 / 菜单（强制 REAL，不走 mock）。
 */
import type {
  HttpMessageBody,
  MenuCreateInput,
  MenuNodeDto,
  MenuWriteInput,
  PermissionDto,
  PermissionListQuery,
  RoleCreateInput,
  RoleDto,
  RoleListQuery,
  RolePermissionsDto,
  RolePermissionsUpdateInput,
  RoleUpdateInput,
  UserCreateInput,
  UserDetailDto,
  UserDto,
  UserListQuery,
  UserRolesDto,
  UserRolesUpdateInput,
  UserStatusUpdateInput,
  UserUpdateInput,
} from '@/types/rbac'
import { httpClient } from '@/utils/http/axios'
import { unwrapData } from '@/utils/http/unwrap'

function toQueryString(query: Record<string, string | undefined>) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value) params.set(key, value)
  }
  const serialized = params.toString()
  return serialized ? `?${serialized}` : ''
}

export const rbacApi = {
  listUsers(query: UserListQuery = {}) {
    const qs = toQueryString({
      username: query.username,
      status: query.status,
      displayName: query.displayName,
    })
    return httpClient.get<HttpMessageBody<UserDto[]>>(`/admin/users${qs}`).then(unwrapData)
  },

  getUser(id: number) {
    return httpClient.get<HttpMessageBody<UserDetailDto>>(`/admin/users/${id}`).then(unwrapData)
  },

  createUser(input: UserCreateInput) {
    return httpClient.post<HttpMessageBody<UserDetailDto>, UserCreateInput>('/admin/users', input).then(unwrapData)
  },

  updateUser(id: number, input: UserUpdateInput) {
    return httpClient
      .put<HttpMessageBody<UserDetailDto>, UserUpdateInput>(`/admin/users/${id}`, input)
      .then(unwrapData)
  },

  updateUserStatus(id: number, input: UserStatusUpdateInput) {
    return httpClient
      .patch<HttpMessageBody<UserDetailDto>, UserStatusUpdateInput>(`/admin/users/${id}/status`, input)
      .then(unwrapData)
  },

  deleteUser(id: number) {
    return httpClient.delete<HttpMessageBody<null>>(`/admin/users/${id}`).then(unwrapData)
  },

  getUserRoles(id: number) {
    return httpClient.get<HttpMessageBody<UserRolesDto>>(`/admin/users/${id}/roles`).then(unwrapData)
  },

  replaceUserRoles(id: number, input: UserRolesUpdateInput) {
    return httpClient
      .put<HttpMessageBody<UserRolesDto>, UserRolesUpdateInput>(`/admin/users/${id}/roles`, input)
      .then(unwrapData)
  },

  listRoles(query: RoleListQuery = {}) {
    const qs = toQueryString({
      roleCode: query.roleCode,
      roleName: query.roleName,
      status: query.status,
    })
    return httpClient.get<HttpMessageBody<RoleDto[]>>(`/admin/roles${qs}`).then(unwrapData)
  },

  getRole(id: number) {
    return httpClient.get<HttpMessageBody<RoleDto>>(`/admin/roles/${id}`).then(unwrapData)
  },

  createRole(input: RoleCreateInput) {
    return httpClient.post<HttpMessageBody<RoleDto>, RoleCreateInput>('/admin/roles', input).then(unwrapData)
  },

  updateRole(id: number, input: RoleUpdateInput) {
    return httpClient.put<HttpMessageBody<RoleDto>, RoleUpdateInput>(`/admin/roles/${id}`, input).then(unwrapData)
  },

  deleteRole(id: number) {
    return httpClient.delete<HttpMessageBody<null>>(`/admin/roles/${id}`).then(unwrapData)
  },

  listPermissions(query: PermissionListQuery = {}) {
    const qs = toQueryString({
      permissionCode: query.permissionCode,
      resourceType: query.resourceType,
      resourceKey: query.resourceKey,
    })
    return httpClient.get<HttpMessageBody<PermissionDto[]>>(`/admin/permissions${qs}`).then(unwrapData)
  },

  getRolePermissions(roleId: number) {
    return httpClient
      .get<HttpMessageBody<RolePermissionsDto>>(`/admin/roles/${roleId}/permissions`)
      .then(unwrapData)
  },

  replaceRolePermissions(roleId: number, input: RolePermissionsUpdateInput) {
    return httpClient
      .put<HttpMessageBody<RolePermissionsDto>, RolePermissionsUpdateInput>(
        `/admin/roles/${roleId}/permissions`,
        input,
      )
      .then(unwrapData)
  },

  listMenus() {
    return httpClient.get<HttpMessageBody<MenuNodeDto[]>>('/admin/menus').then(unwrapData)
  },

  createMenu(input: MenuCreateInput) {
    return httpClient.post<HttpMessageBody<MenuNodeDto>, MenuCreateInput>('/admin/menus', input).then(unwrapData)
  },

  updateMenu(id: number, input: MenuWriteInput) {
    return httpClient
      .put<HttpMessageBody<MenuNodeDto>, MenuWriteInput>(`/admin/menus/${id}`, input)
      .then(unwrapData)
  },

  deleteMenu(id: number) {
    return httpClient.delete<HttpMessageBody<null>>(`/admin/menus/${id}`).then(unwrapData)
  },
}
