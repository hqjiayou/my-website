/**
 * 站点 AI 管理 API。
 * Prompt / KB / Conversation / Preview / Runtime → 始终 REAL `/admin/sites/{siteId}/ai/*`。
 */
import type { PageResult } from '@/types/site'
import type { HttpMessageBody } from '@/types/rbac'
import type {
  AiConversationListItem,
  AiConversationListQuery,
  AiKnowledgeDetail,
  AiKnowledgeListItem,
  AiKnowledgeListQuery,
  AiKnowledgeReembedResult,
  AiMessage,
  AiPreviewChatInput,
  AiPreviewHistoryMessage,
  AiPreviewSkill,
  AiModelEntry,
  AiModelEntryWriteInput,
  AiPromptProfile,
  AiMcpReady,
  AiRuntime,
  CreateAiKnowledgeInput,
  UpdateAiKnowledgeInput,
  UpdateAiPromptProfileInput,
} from '@/types/ai'
import { ApiError } from '@/utils/http/apiError'
import { httpClient } from '@/utils/http/axios'
import { unwrapData } from '@/utils/http/unwrap'
import { getAccessToken } from '@/utils/token'

function toQueryString(query: Record<string, string | number | undefined>) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === '') continue
    params.set(key, String(value))
  }
  const serialized = params.toString()
  return serialized ? `?${serialized}` : ''
}

function unwrapVoid(body: HttpMessageBody<null | void>) {
  if (body.code != null && body.code !== 0) {
    throw new ApiError(body.message || 'Request failed', { code: body.code })
  }
}

const UPLOAD_TIMEOUT_MS = 60_000
const REEMBED_TIMEOUT_MS = 600_000

function aiBase(siteId: number) {
  return `/admin/sites/${siteId}/ai`
}

export const aiApi = {
  listModelEntries(): Promise<AiModelEntry[]> {
    return httpClient.get<HttpMessageBody<AiModelEntry[]>>('/admin/ai/model-entries').then(unwrapData)
  },

  createModelEntry(input: AiModelEntryWriteInput): Promise<AiModelEntry> {
    return httpClient
      .post<HttpMessageBody<AiModelEntry>, AiModelEntryWriteInput>('/admin/ai/model-entries', input)
      .then(unwrapData)
  },

  updateModelEntry(id: number, input: AiModelEntryWriteInput): Promise<AiModelEntry> {
    return httpClient
      .put<HttpMessageBody<AiModelEntry>, AiModelEntryWriteInput>(`/admin/ai/model-entries/${id}`, input)
      .then(unwrapData)
  },

  deleteModelEntry(id: number): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null | void>>(`/admin/ai/model-entries/${id}`)
      .then(unwrapVoid)
  },

  getPromptProfile(siteId: number): Promise<AiPromptProfile> {
    return httpClient
      .get<HttpMessageBody<AiPromptProfile>>(`${aiBase(siteId)}/prompt-profile`)
      .then(unwrapData)
  },

  upsertPromptProfile(siteId: number, input: UpdateAiPromptProfileInput): Promise<AiPromptProfile> {
    return httpClient
      .put<HttpMessageBody<AiPromptProfile>, UpdateAiPromptProfileInput>(
        `${aiBase(siteId)}/prompt-profile`,
        input,
      )
      .then(unwrapData)
  },

  listKnowledgeDocuments(
    siteId: number,
    query: AiKnowledgeListQuery = {},
  ): Promise<PageResult<AiKnowledgeListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      status: query.status,
    })
    return httpClient
      .get<HttpMessageBody<PageResult<AiKnowledgeListItem>>>(
        `${aiBase(siteId)}/knowledge-documents${qs}`,
      )
      .then(unwrapData)
  },

  getKnowledgeDocument(siteId: number, id: number): Promise<AiKnowledgeDetail> {
    return httpClient
      .get<HttpMessageBody<AiKnowledgeDetail>>(`${aiBase(siteId)}/knowledge-documents/${id}`)
      .then(unwrapData)
  },

  createKnowledgeDocument(siteId: number, input: CreateAiKnowledgeInput): Promise<AiKnowledgeDetail> {
    return httpClient
      .post<HttpMessageBody<AiKnowledgeDetail>, CreateAiKnowledgeInput>(
        `${aiBase(siteId)}/knowledge-documents`,
        input,
      )
      .then(unwrapData)
  },

  uploadKnowledgeDocument(siteId: number, file: File, title?: string): Promise<AiKnowledgeDetail> {
    const form = new FormData()
    form.append('file', file)
    const trimmed = title?.trim()
    if (trimmed) {
      form.append('title', trimmed)
    }
    return httpClient
      .post<HttpMessageBody<AiKnowledgeDetail>, FormData>(
        `${aiBase(siteId)}/knowledge-documents/upload`,
        form,
        { timeout: UPLOAD_TIMEOUT_MS },
      )
      .then(unwrapData)
  },

  reindexKnowledgeDocument(siteId: number, id: number): Promise<AiKnowledgeDetail> {
    return httpClient
      .post<HttpMessageBody<AiKnowledgeDetail>, Record<string, never>>(
        `${aiBase(siteId)}/knowledge-documents/${id}/reindex`,
        {},
      )
      .then(unwrapData)
  },

  updateKnowledgeDocument(
    siteId: number,
    id: number,
    input: UpdateAiKnowledgeInput,
  ): Promise<AiKnowledgeDetail> {
    return httpClient
      .put<HttpMessageBody<AiKnowledgeDetail>, UpdateAiKnowledgeInput>(
        `${aiBase(siteId)}/knowledge-documents/${id}`,
        input,
      )
      .then(unwrapData)
  },

  reembedKnowledge(): Promise<AiKnowledgeReembedResult> {
    return httpClient
      .post<HttpMessageBody<AiKnowledgeReembedResult>>('/admin/ai/knowledge/reembed', undefined, {
        timeout: REEMBED_TIMEOUT_MS,
      })
      .then(unwrapData)
  },

  deleteKnowledgeDocument(siteId: number, id: number): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null | void>>(`${aiBase(siteId)}/knowledge-documents/${id}`)
      .then(unwrapVoid)
  },

  listConversations(
    siteId: number,
    query: AiConversationListQuery = {},
  ): Promise<PageResult<AiConversationListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
    })
    return httpClient
      .get<HttpMessageBody<PageResult<AiConversationListItem>>>(`${aiBase(siteId)}/conversations${qs}`)
      .then(unwrapData)
  },

  listConversationMessages(siteId: number, conversationId: number): Promise<AiMessage[]> {
    return httpClient
      .get<HttpMessageBody<AiMessage[]>>(`${aiBase(siteId)}/conversations/${conversationId}/messages`)
      .then(unwrapData)
  },

  listPreviewSkills(siteId: number): Promise<AiPreviewSkill[]> {
    return httpClient
      .get<HttpMessageBody<AiPreviewSkill[]>>(`${aiBase(siteId)}/preview/skills`)
      .then(unwrapData)
  },

  getRuntime(siteId: number): Promise<AiRuntime> {
    return httpClient.get<HttpMessageBody<AiRuntime>>(`${aiBase(siteId)}/runtime`).then(unwrapData)
  },

  probeMcp(siteId: number): Promise<AiMcpReady> {
    return httpClient.get<HttpMessageBody<AiMcpReady>>(`${aiBase(siteId)}/mcp-ready`).then(unwrapData)
  },
}

export function previewChatStreamUrl(siteId: number) {
  const base = import.meta.env.VITE_API_BASE_URL ?? '/api'
  return `${base}${aiBase(siteId)}/preview/chat/stream`
}

/** SSE 预览走 fetch：axios 默认超时不适合流式响应。 */
export function streamPreviewChat(
  siteId: number,
  body: AiPreviewChatInput,
  abortSignal?: AbortSignal,
): Promise<Response> {
  const token = getAccessToken()
  return fetch(previewChatStreamUrl(siteId), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
    signal: abortSignal,
    credentials: 'include',
  })
}

export type { AiPreviewHistoryMessage }
