/**
 * 功能注解：用户与角色相关 API（用户列表、角色列表）。
 */
import { userMockService } from '@/mocks/services/user.mock'
import type { Role, User } from '@/mocks/types/user'
import { httpClient } from '@/utils/http/axios'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

const userHttpService: typeof userMockService = {
  listUsers: (siteId?: string) => httpClient.get<User[]>('/site/users', { params: { siteId } }),
  listRoles: () => httpClient.get<Role[]>('/site/roles'),
}

export const userApi = USE_MOCK ? userMockService : userHttpService
