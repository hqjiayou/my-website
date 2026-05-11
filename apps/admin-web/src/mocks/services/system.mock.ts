import { MOCK_DEVELOPER_PROFILE, MOCK_SYSTEM_PARAMS } from '@/mocks/db/system'
import { mockDelay } from '@/mocks/utils/delay'

export const systemMockService = {
  async listParams() {
    await mockDelay()
    return [...MOCK_SYSTEM_PARAMS]
  },
  async getDeveloperProfile() {
    await mockDelay()
    return { ...MOCK_DEVELOPER_PROFILE, capabilities: [...MOCK_DEVELOPER_PROFILE.capabilities] }
  },
}
