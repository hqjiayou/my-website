import { MOCK_TEMPLATES } from '@/mocks/db/templates'
import { mockDelay } from '@/mocks/utils/delay'

export const templateMockService = {
  async listTemplates() {
    await mockDelay()
    return [...MOCK_TEMPLATES]
  },
  async getTemplate(templateId: string) {
    await mockDelay()
    return MOCK_TEMPLATES.find((item) => item.id === templateId)
  },
  async getTemplateByCode(code: string) {
    await mockDelay()
    return MOCK_TEMPLATES.find((item) => item.code === code)
  },
}
