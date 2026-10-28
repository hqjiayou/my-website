import type {
  HttpMessageBody,
  SharedConversation,
  SiteAiChatRequest,
  SiteAiConversation,
  SiteAiMessage,
  SiteAiShare,
  SiteKnowledgeDocumentDetail,
  SiteKnowledgeDocumentListItem,
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

  /** 創建或刷新分享快照，回傳 token；前端以 origin 拼 /share/{token}。 */
  createShare(siteId: string, conversationId: string) {
    return httpClient
      .post<HttpMessageBody<SiteAiShare>>(`/api/site/${siteId}/ai/conversations/${conversationId}/share`, {})
      .then(unwrapData)
  },

  /** 目前分享；未分享時 code 0 且 data 為 null，回 null。其他錯誤照拋。 */
  getShare(siteId: string, conversationId: string) {
    return httpClient
      .get<HttpMessageBody<SiteAiShare | null>>(`/api/site/${siteId}/ai/conversations/${conversationId}/share`)
      .then((body) => {
        if (body.code != null && body.code !== 0) {
          throw new ApiError(body.message || '請求失敗', { code: body.code })
        }
        return body.data ?? null
      })
  },

  /** 撤銷分享。成功体 data 為 null，處理同 deleteConversation。 */
  revokeShare(siteId: string, conversationId: string) {
    return httpClient
      .delete<HttpMessageBody<null>>(`/api/site/${siteId}/ai/conversations/${conversationId}/share`)
      .then((body) => {
        if (body.code != null && body.code !== 0) {
          throw new ApiError(body.message || '請求失敗', { code: body.code })
        }
      })
  },

  /** 公開讀分享快照（不需要登入）。 */
  getSharedConversation(token: string) {
    return httpClient
      .get<HttpMessageBody<SharedConversation>>(`/api/site/public/ai/shares/${token}`)
      .then(unwrapData)
  },

  /** 真截斷：硬刪該消息及其之後全部消息（編輯/重新生成的後端對齊）。成功体同 deleteConversation。 */
  truncateConversation(siteId: string, conversationId: string, fromMessageId: string) {
    return httpClient
      .delete<HttpMessageBody<null>>(
        `/api/site/${siteId}/ai/conversations/${conversationId}/messages/${fromMessageId}`,
      )
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

  listKnowledgeDocuments(siteId: string) {
    return httpClient
      .get<HttpMessageBody<SiteKnowledgeDocumentListItem[]>>(`/api/site/${siteId}/ai/knowledge-documents`)
      .then(unwrapData)
  },

  getKnowledgeDocument(siteId: string, documentId: number) {
    return httpClient
      .get<HttpMessageBody<SiteKnowledgeDocumentDetail>>(
        `/api/site/${siteId}/ai/knowledge-documents/${documentId}`,
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
