/**
 * 功能注解：AI 能力相关 API（配置、提示词、知识库与会话）。
 */
import { aiMockService } from '@/mocks/services/aiMock'
import type { AiConfig, AiConversation, AiPrompt, KnowledgeBase } from '@/mocks/types/ai'
import { httpClient } from '@/utils/http/axios'

import { USE_MOCK } from '@/config/apiMode'

const aiHttpService: typeof aiMockService = {
  listAiConfigs: (siteId?: string) => httpClient.get<AiConfig[]>('/agent/ai-configs', { params: { siteId } }),
  listPrompts: () => httpClient.get<AiPrompt[]>('/agent/prompts'),
  listKnowledgeBases: (siteId?: string) => httpClient.get<KnowledgeBase[]>('/agent/knowledge-bases', { params: { siteId } }),
  listConversations: (siteId?: string) => httpClient.get<AiConversation[]>('/agent/conversations', { params: { siteId } }),
}

export const aiApi = USE_MOCK ? aiMockService : aiHttpService
