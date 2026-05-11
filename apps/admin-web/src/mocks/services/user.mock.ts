import { MOCK_ROLES, MOCK_USERS } from '@/mocks/db/users'
import { mockDelay } from '@/mocks/utils/delay'

export const userMockService = {
  async listUsers(siteId?: string) {
    await mockDelay()
    if (!siteId) return [...MOCK_USERS]
    return MOCK_USERS.filter((item) => item.siteId === siteId)
  },
  async listRoles() {
    await mockDelay()
    return [...MOCK_ROLES]
  },
}
