import { MOCK_FILES } from '@/mocks/db/files'
import { mockDelay } from '@/mocks/utils/delay'

export const fileMockService = {
  async listFiles(siteId?: string) {
    await mockDelay()
    if (!siteId) return [...MOCK_FILES]
    return MOCK_FILES.filter((item) => item.siteId === siteId)
  },
}
