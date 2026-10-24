import type { HttpMessageBody, SiteAiChatRequest, SiteAiConversation, SiteAiMessage } from '@/types/api'
import { httpClient } from '@/utils/http/axios'
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
