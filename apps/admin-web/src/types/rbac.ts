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
  id?: number
  menuCode: string
  menuName: string
  path: string | null
  icon: string | null
  sort: number
  status?: string
  children: MenuNodeDto[]
}

export interface MenuWriteInput {
  menuName: string
  parentId: number | null
  path?: string
  icon?: string
  sort: number
  status: string
}

export interface MenuCreateInput extends MenuWriteInput {
  menuCode: string
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
  roleDetail: 'admin:role:list',
  roleCreate: 'admin:role:list',
  roleUpdate: 'admin:role:list',
  roleDelete: 'admin:role:list',
  rolePermissionView: 'admin:role:list',
  rolePermissionUpdate: 'admin:role:list',
  permissionList: 'admin:permission:list',
  menuList: 'admin:menu:list',
  menuCreate: 'admin:menu:list',
  menuUpdate: 'admin:menu:list',
  menuDelete: 'admin:menu:list',
  dashboardView: 'admin:dashboard:view',
  userList: 'admin:user:list',
  userDetail: 'admin:user:list',
  userCreate: 'admin:user:list',
  userUpdate: 'admin:user:list',
  userDelete: 'admin:user:list',
  userStatus: 'admin:user:list',
  userRoleView: 'admin:user:list',
  userRoleUpdate: 'admin:user:list',
  agentList: 'admin:agent:list',
  agentDetail: 'admin:agent:list',
  agentCreate: 'admin:agent:list',
  agentUpdate: 'admin:agent:list',
  agentDelete: 'admin:agent:list',
  agentStatus: 'admin:agent:list',
  mediaList: 'admin:media:list',
  mediaUpload: 'admin:media:list',
  mediaDownload: 'admin:media:list',
  mediaDelete: 'admin:media:list',
  contentList: 'admin:content:list',
  contentCreate: 'admin:content:list',
  contentUpdate: 'admin:content:list',
  contentDelete: 'admin:content:list',
  contentStatus: 'admin:content:list',
  aiPromptRead: 'admin:ai-prompt:read',
  aiPromptUpdate: 'admin:ai-prompt:read',
  aiModelRead: 'admin:ai-model:read',
  // 后端模型入口写接口实际只校验 read（AdminAiModelEntryController 全端点），与此处一致；
  // 唯运行配置写接口（AdminAiRuntimeConfigController）校验 update，必须分开，见 aiRuntimeConfigUpdate。
  aiModelUpdate: 'admin:ai-model:read',
  aiRuntimeConfigUpdate: 'admin:ai-model:update',
  aiKbList: 'admin:ai-kb:list',
  aiKbCreate: 'admin:ai-kb:list',
  aiKbUpdate: 'admin:ai-kb:list',
  aiKbDelete: 'admin:ai-kb:list',
  aiConversationList: 'admin:ai-conversation:list',
} as const
