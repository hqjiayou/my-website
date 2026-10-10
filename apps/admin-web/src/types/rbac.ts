export interface HttpMessageBody<T> {
  code?: number | null
  message: string
  data: T
}

export interface RoleDto {
  id: number
  roleCode: string
  roleName: string
  status: string
  description: string | null
}

export interface RoleCreateInput {
  roleCode: string
  roleName: string
  description?: string
  status: string
}

export interface RoleUpdateInput {
  roleName: string
  description?: string
  status: string
}

export interface RoleListQuery {
  roleCode?: string
  roleName?: string
  status?: string
}

export interface PermissionDto {
  id: number
  permissionCode: string
  permissionName: string
  resourceType: string
  resourceKey: string
  status: string
}

export interface PermissionListQuery {
  permissionCode?: string
  resourceType?: string
  resourceKey?: string
}

export interface MenuNodeDto {
  menuCode: string
  menuName: string
  path: string | null
  icon: string | null
  sort: number
  children: MenuNodeDto[]
}

export interface RolePermissionsDto {
  roleId: number
  roleCode: string
  permissions: string[]
}

export interface RolePermissionsUpdateInput {
  permissionCodes: string[]
}

/** 对齐 AdminUserListItemDto（REAL API，非分页）。 */
export interface UserDto {
  id: number
  /** SubjectCodes.formatAdminSubject，如 ADMIN_000100 */
  userCode: string
  username: string
  displayName: string | null
  email: string | null
  phone: string | null
  status: string
  /** 是否持有 super_admin（后端计算字段）。 */
  protectedUser: boolean
}

/** 对齐 AdminUserDetailDto（无 passwordHash）。 */
export interface UserDetailDto extends UserDto {
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

export interface UserCreateInput {
  username: string
  password: string
  displayName?: string
  email?: string
  phone?: string
}

export interface UserUpdateInput {
  displayName?: string
  email?: string
  phone?: string
}

export interface UserStatusUpdateInput {
  status: string
}

export interface UserListQuery {
  username?: string
  status?: string
  displayName?: string
}

export interface RoleBindingDto {
  roleId: number
  roleCode: string
  roleName: string
  status: string
  protectedRole: boolean
}

/** 对齐 AdminUserRoleResponse。 */
export interface UserRolesDto {
  userId: number
  userCode: string
  username: string
  roles: RoleBindingDto[]
}

export interface UserRolesUpdateInput {
  roleCodes: string[]
}

export const ROLE_STATUS = {
  ACTIVE: 'ACTIVE',
  DISABLED: 'DISABLED',
} as const

export const USER_STATUS = {
  ACTIVE: 'ACTIVE',
  DISABLED: 'DISABLED',
} as const

export const SUPER_ADMIN_ROLE_CODE = 'super_admin'

export const PERMISSION_CODES = {
  roleList: 'admin:role:list',
  roleDetail: 'admin:role:detail',
  roleCreate: 'admin:role:create',
  roleUpdate: 'admin:role:update',
  roleDelete: 'admin:role:delete',
  rolePermissionView: 'admin:role:permission:view',
  rolePermissionUpdate: 'admin:role:permission:update',
  permissionList: 'admin:permission:list',
  menuList: 'admin:menu:list',
  dashboardView: 'admin:dashboard:view',
  userList: 'admin:user:list',
  userDetail: 'admin:user:detail',
  userCreate: 'admin:user:create',
  userUpdate: 'admin:user:update',
  userDelete: 'admin:user:delete',
  userStatus: 'admin:user:status',
  userRoleView: 'admin:user:role:view',
  userRoleUpdate: 'admin:user:role:update',
  siteList: 'admin:site:list',
  siteDetail: 'admin:site:detail',
  siteCreate: 'admin:site:create',
  siteUpdate: 'admin:site:update',
  siteDelete: 'admin:site:delete',
  siteStatus: 'admin:site:status',
  templateList: 'admin:template:list',
  templateDetail: 'admin:template:detail',
  templateStatus: 'admin:template:status',
  mediaList: 'admin:media:list',
  mediaUpload: 'admin:media:upload',
  mediaDownload: 'admin:media:download',
  mediaDelete: 'admin:media:delete',
  contentList: 'admin:content:list',
  contentCreate: 'admin:content:create',
  contentUpdate: 'admin:content:update',
  contentDelete: 'admin:content:delete',
  contentStatus: 'admin:content:status',
  reservationList: 'admin:reservation:list',
  reservationCreate: 'admin:reservation:create',
  reservationUpdate: 'admin:reservation:update',
  reservationDelete: 'admin:reservation:delete',
  aiPromptRead: 'admin:ai-prompt:read',
  aiPromptUpdate: 'admin:ai-prompt:update',
  aiKbList: 'admin:ai-kb:list',
  aiKbCreate: 'admin:ai-kb:create',
  aiKbUpdate: 'admin:ai-kb:update',
  aiKbDelete: 'admin:ai-kb:delete',
  aiConversationList: 'admin:ai-conversation:list',
} as const
