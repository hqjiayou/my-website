import type {
  HttpMessageBody,
  SiteAiChatRequest,
  SiteAiConversation,
  SiteAiMessage,
  SiteKnowledgeSearchResult,
  SiteUserMemory,
} from '@/types/api'
import { httpClient } from '@/utils/http/axios'
import { ApiError } from '@/utils/http/apiError'
import { streamSiteChat, type SiteSseEvent } from '@/utils/http/sse'
import { unwrapData } from '@/utils/http/unwrap'

export type { SiteSseEvent }

export const siteAiApi = {
  listConversations(siteId: string) {
    return httpClient
      .get<HttpMessageBody<SiteAiConversation[]>>(`/api/site/${siteId}/ai/conversations`)
      .then(unwrapData)
  },

  listMessages(siteId: string, conversationId: string) {
    return httpClient
      .get<HttpMessageBody<SiteAiMessage[]>>(`/api/site/${siteId}/ai/conversations/${conversationId}/messages`)
      .then(unwrapData)
  },

  renameConversation(siteId: string, conversationId: string, title: string) {
    return httpClient
      .patch<HttpMessageBody<SiteAiConversation>, { title: string }>(
        `/api/site/${siteId}/ai/conversations/${conversationId}`,
        { title },
      )
      .then(unwrapData)
  },

  /** 成功体 data 为 null，不能走 unwrapData（空 data 会被当成失败）。 */
  deleteConversation(siteId: string, conversationId: string) {
    return httpClient
      .delete<HttpMessageBody<null>>(`/api/site/${siteId}/ai/conversations/${conversationId}`)
      .then((body) => {
        if (body.code != null && body.code !== 0) {
          throw new ApiError(body.message || '請求失敗', { code: body.code })
        }
      })
  },

  listMemories(siteId: string) {
    return httpClient
      .get<HttpMessageBody<SiteUserMemory[]>>(`/api/site/${siteId}/ai/memories`)
      .then(unwrapData)
  },

  deleteMemory(siteId: string, memoryId: string) {
    return httpClient.delete<HttpMessageBody<null>>(`/api/site/${siteId}/ai/memories/${memoryId}`).then((body) => {
      if (body.code != null && body.code !== 0) {
        throw new ApiError(body.message || '請求失敗', { code: body.code })
      }
    })
  },

  clearMemories(siteId: string) {
    return httpClient.delete<HttpMessageBody<null>>(`/api/site/${siteId}/ai/memories`).then((body) => {
      if (body.code != null && body.code !== 0) {
        throw new ApiError(body.message || '請求失敗', { code: body.code })
      }
    })
  },

  searchKnowledge(siteId: string, query: string, topK: number) {
    return httpClient
      .post<HttpMessageBody<SiteKnowledgeSearchResult>, { query: string; topK: number }>(
        `/api/site/${siteId}/ai/knowledge/search`,
        { query, topK },
      )
      .then(unwrapData)
  },

  streamChat(
    siteId: string,
    body: SiteAiChatRequest,
    onEvent: (event: SiteSseEvent) => void,
    signal?: AbortSignal,
  ) {
    return streamSiteChat(siteId, body, onEvent, signal)
  },
}

export function sseErrorText(errorCode: string): string {
  switch (errorCode) {
    case 'EXTERNAL_DEPENDENCY_BLOCKED':
      return 'AI 暫時無法使用'
    case 'MCP_UNAVAILABLE':
      return '工具服務暫時無法使用'
    case 'AGENT_BUDGET_EXCEEDED':
      return '本次對話已達到用量上限'
    case 'SYSTEM_ERROR':
      return '服務暫時發生錯誤，請稍後重試'
    default:
      return errorCode ? `對話失敗（${errorCode}）` : '對話失敗（未回傳 errorCode）'
  }
}
