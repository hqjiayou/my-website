import { MOCK_AI_CONFIGS, MOCK_AI_CONVERSATIONS, MOCK_AI_PROMPTS, MOCK_KNOWLEDGE_BASES } from '@/mocks/db/ai'
import { mockDelay } from '@/mocks/utils/delay'

export const aiMockService = {
  async listAiConfigs(siteId?: string) {
    await mockDelay()
    if (!siteId) return [...MOCK_AI_CONFIGS]
    return MOCK_AI_CONFIGS.filter((item) => item.siteId === siteId)
  },
  async listPrompts() {
    await mockDelay()
    return [...MOCK_AI_PROMPTS]
  },
  async listKnowledgeBases(siteId?: string) {
    await mockDelay()
    if (!siteId) return [...MOCK_KNOWLEDGE_BASES]
    return MOCK_KNOWLEDGE_BASES.filter((item) => item.siteId === siteId)
  },
  async listConversations(siteId?: string) {
    await mockDelay()
    if (!siteId) return [...MOCK_AI_CONVERSATIONS]
    return MOCK_AI_CONVERSATIONS.filter((item) => item.siteId === siteId)
  },
}
