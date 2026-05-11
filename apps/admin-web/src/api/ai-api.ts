import { aiMockService } from '@/mocks/services/ai.mock'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

function notImplemented(name: string): never {
  throw new Error(`${name} is not implemented yet`)
}

export const aiApi = USE_MOCK
  ? aiMockService
  : {
      listAiConfigs: async (_siteId?: string) => notImplemented('agent-service listAiConfigs'),
      listPrompts: async () => notImplemented('agent-service listPrompts'),
      listKnowledgeBases: async (_siteId?: string) => notImplemented('agent-service listKnowledgeBases'),
      listConversations: async (_siteId?: string) => notImplemented('agent-service listConversations'),
    }
