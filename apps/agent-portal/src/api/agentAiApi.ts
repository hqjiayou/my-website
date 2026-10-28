import type {
  HttpMessageBody,
  SharedConversation,
  AgentAiChatRequest,
  AgentAiConversation,
  AgentAiModelModes,
  AgentAiMessage,
  AgentAiSearchHit,
  AgentAiShare,
  AgentKnowledgeDocumentDetail,
  AgentKnowledgeDocumentListItem,
  AgentKnowledgeSearchResult,
  AgentUserMemory,
} from '@/types/api'
import { httpClient } from '@/utils/http/axios'
import { ApiError } from '@/utils/http/apiError'
import { streamAgentChat, type AgentSseEvent } from '@/utils/http/sse'
import { unwrapData } from '@/utils/http/unwrap'

export type { AgentSseEvent }

export const agentAiApi = {
  listConversations(agentId: string) {
    return httpClient
      .get<HttpMessageBody<AgentAiConversation[]>>(`/api/agent/${agentId}/ai/conversations`)
      .then(unwrapData)
  },

  listMessages(agentId: string, conversationId: string) {
    return httpClient
      .get<HttpMessageBody<AgentAiMessage[]>>(`/api/agent/${agentId}/ai/conversations/${conversationId}/messages`)
      .then(unwrapData)
  },

  renameConversation(agentId: string, conversationId: string, title: string) {
    return httpClient
      .patch<HttpMessageBody<AgentAiConversation>, { title: string }>(
        `/api/agent/${agentId}/ai/conversations/${conversationId}`,
        { title },
      )
      .then(unwrapData)
  },

  /** 成功体 data 为 null，不能走 unwrapData（空 data 会被当成失败）。 */
  deleteConversation(agentId: string, conversationId: string) {
    return httpClient
      .delete<HttpMessageBody<null>>(`/api/agent/${agentId}/ai/conversations/${conversationId}`)
      .then((body) => {
        if (body.code != null && body.code !== 0) {
          throw new ApiError(body.message || '請求失敗', { code: body.code })
        }
      })
  },

  /** 創建或刷新分享快照，回傳 token；前端以 origin 拼 /share/{token}。 */
  createShare(agentId: string, conversationId: string) {
    return httpClient
      .post<HttpMessageBody<AgentAiShare>>(`/api/agent/${agentId}/ai/conversations/${conversationId}/share`, {})
      .then(unwrapData)
  },

  /** 目前分享；未分享時 code 0 且 data 為 null，回 null。其他錯誤照拋。 */
  getShare(agentId: string, conversationId: string) {
    return httpClient
      .get<HttpMessageBody<AgentAiShare | null>>(`/api/agent/${agentId}/ai/conversations/${conversationId}/share`)
      .then((body) => {
        if (body.code != null && body.code !== 0) {
          throw new ApiError(body.message || '請求失敗', { code: body.code })
        }
        return body.data ?? null
      })
  },

  /** 撤銷分享。成功体 data 為 null，處理同 deleteConversation。 */
  revokeShare(agentId: string, conversationId: string) {
    return httpClient
      .delete<HttpMessageBody<null>>(`/api/agent/${agentId}/ai/conversations/${conversationId}/share`)
      .then((body) => {
        if (body.code != null && body.code !== 0) {
          throw new ApiError(body.message || '請求失敗', { code: body.code })
        }
      })
  },

  /** 跨對話全文搜尋：回傳命中消息的摘要（按消息 id 倒序，最多 30 條）。 */
  searchConversations(agentId: string, query: string) {
    return httpClient
      .get<HttpMessageBody<AgentAiSearchHit[]>>(`/api/agent/${agentId}/ai/search?q=${encodeURIComponent(query)}`)
      .then(unwrapData)
  },

  /** 公開讀分享快照（不需要登入）。 */
  getSharedConversation(token: string) {
    return httpClient
      .get<HttpMessageBody<SharedConversation>>(`/api/agent/public/ai/shares/${token}`)
      .then(unwrapData)
  },

  /** 真截斷：硬刪該消息及其之後全部消息（編輯/重新生成的後端對齊）。成功体同 deleteConversation。 */
  truncateConversation(agentId: string, conversationId: string, fromMessageId: string) {
    return httpClient
      .delete<HttpMessageBody<null>>(
        `/api/agent/${agentId}/ai/conversations/${conversationId}/messages/${fromMessageId}`,
      )
      .then((body) => {
        if (body.code != null && body.code !== 0) {
          throw new ApiError(body.message || '請求失敗', { code: body.code })
        }
      })
  },

  listMemories(agentId: string) {
    return httpClient
      .get<HttpMessageBody<AgentUserMemory[]>>(`/api/agent/${agentId}/ai/memories`)
      .then(unwrapData)
  },

  deleteMemory(agentId: string, memoryId: string) {
    return httpClient.delete<HttpMessageBody<null>>(`/api/agent/${agentId}/ai/memories/${memoryId}`).then((body) => {
      if (body.code != null && body.code !== 0) {
        throw new ApiError(body.message || '請求失敗', { code: body.code })
      }
    })
  },

  clearMemories(agentId: string) {
    return httpClient.delete<HttpMessageBody<null>>(`/api/agent/${agentId}/ai/memories`).then((body) => {
      if (body.code != null && body.code !== 0) {
        throw new ApiError(body.message || '請求失敗', { code: body.code })
      }
    })
  },

  searchKnowledge(agentId: string, query: string, topK: number) {
    return httpClient
      .post<HttpMessageBody<AgentKnowledgeSearchResult>, { query: string; topK: number }>(
        `/api/agent/${agentId}/ai/knowledge/search`,
        { query, topK },
      )
      .then(unwrapData)
  },

  listKnowledgeDocuments(agentId: string) {
    return httpClient
      .get<HttpMessageBody<AgentKnowledgeDocumentListItem[]>>(`/api/agent/${agentId}/ai/knowledge-documents`)
      .then(unwrapData)
  },

  getKnowledgeDocument(agentId: string, documentId: number) {
    return httpClient
      .get<HttpMessageBody<AgentKnowledgeDocumentDetail>>(
        `/api/agent/${agentId}/ai/knowledge-documents/${documentId}`,
      )
      .then(unwrapData)
  },

  /** 雙模式可用性：每模式一項（available + 主用模型名 label）。 */
  getModelModes(agentId: string) {
    return httpClient
      .get<HttpMessageBody<AgentAiModelModes>>(`/api/agent/${agentId}/ai/model-modes`)
      .then(unwrapData)
  },

  streamChat(
    agentId: string,
    body: AgentAiChatRequest,
    onEvent: (event: AgentSseEvent) => void,
    signal?: AbortSignal,
  ) {
    return streamAgentChat(agentId, body, onEvent, signal)
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
