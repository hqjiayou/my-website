/**
 * 站点 AI 管理 API。
 * Prompt / KB / Conversation / Preview / Runtime → 始终 REAL `/admin/agents/{agentId}/ai/*`。
 */
import type { PageResult } from '@/types/site'
import type { HttpMessageBody } from '@/types/rbac'
import type {
  AiConversationListItem,
  AiConversationListQuery,
  AiKnowledgeArchiveResult,
  AiKnowledgeDetail,
  AiKnowledgeListItem,
  AiKnowledgeListQuery,
  AiKnowledgeBase,
  AiKnowledgeOverview,
  AiKnowledgeReembedResult,
  AiMessage,
  AiPreviewChatInput,
  AiPreviewHistoryMessage,
  AiPreviewModelBackfill,
  AiPreviewSkill,
  AiEmbeddingClass,
  AiEmbeddingClassSaveItem,
  AiModelEntry,
  AiModelEntryWriteInput,
  AiRuntimeConfig,
  AiRuntimeConfigUpdateInput,
  AiUsageReport,
  AiUsageSource,
  ProxyLoginProvider,
  ProxyServicesApiKeyCreated,
  ProxyServicesApiKeys,
  ProxyServicesLogin,
  ProxyServicesLoginStatus,
  ProxyServicesStatus,
  ProxyServicesUsage,
  AiPromptProfile,
  AiMcpReady,
  AiRuntime,
  CreateAiKnowledgeInput,
  GatewayAccessKey,
  GatewayAccessKeyIssueInput,
  GatewayAccessKeyIssueResponse,
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

function aiBase(agentId: number) {
  return `/admin/agents/${agentId}/ai`
}

export const aiApi = {
  getProxyServicesUsage(): Promise<ProxyServicesUsage> {
    return httpClient
      .get<HttpMessageBody<ProxyServicesUsage>>('/admin/ai/proxy-services/usage')
      .then(unwrapData)
  },

  getProxyServices(): Promise<ProxyServicesStatus> {
    return httpClient
      .get<HttpMessageBody<ProxyServicesStatus>>('/admin/ai/proxy-services')
      .then(unwrapData)
  },

  listProxyApiKeys(): Promise<ProxyServicesApiKeys> {
    return httpClient
      .get<HttpMessageBody<ProxyServicesApiKeys>>('/admin/ai/proxy-services/api-keys')
      .then(unwrapData)
  },

  createProxyApiKey(apiKey: string): Promise<ProxyServicesApiKeyCreated> {
    return httpClient
      .post<HttpMessageBody<ProxyServicesApiKeyCreated>, { apiKey: string }>('/admin/ai/proxy-services/api-keys', {
        apiKey,
      })
      .then(unwrapData)
  },

  deleteProxyApiKey(apiKey: string): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null | void>>('/admin/ai/proxy-services/api-keys', { data: { apiKey } })
      .then(unwrapVoid)
  },

  startProxyLogin(provider: ProxyLoginProvider): Promise<ProxyServicesLogin> {
    return httpClient
      .post<HttpMessageBody<ProxyServicesLogin>, { provider: ProxyLoginProvider }>('/admin/ai/proxy-services/logins', {
        provider,
      })
      .then(unwrapData)
  },

  proxyLoginStatus(state: string): Promise<ProxyServicesLoginStatus> {
    return httpClient
      .get<HttpMessageBody<ProxyServicesLoginStatus>>('/admin/ai/proxy-services/logins/status', {
        params: { state },
      })
      .then(unwrapData)
  },

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

  /** 嵌入类登记（Issue #126）：agentId 省略＝全站 scope；行内 spec/类键由服务端从入口解析。 */
  listEmbeddingClasses(agentId?: number): Promise<AiEmbeddingClass[]> {
    const qs = toQueryString({ agentId })
    return httpClient
      .get<HttpMessageBody<AiEmbeddingClass[]>>(`/admin/ai/embedding-classes${qs}`)
      .then(unwrapData)
  },

  /** 整表保存：有序 items 整体替换该 scope 的登记，空列表＝清空。 */
  replaceEmbeddingClasses(
    agentId: number | undefined,
    items: AiEmbeddingClassSaveItem[],
  ): Promise<AiEmbeddingClass[]> {
    const qs = toQueryString({ agentId })
    return httpClient
      .put<HttpMessageBody<AiEmbeddingClass[]>, { items: AiEmbeddingClassSaveItem[] }>(
        `/admin/ai/embedding-classes${qs}`,
        { items },
      )
      .then(unwrapData)
  },

  getRuntimeConfig(): Promise<AiRuntimeConfig> {
    return httpClient.get<HttpMessageBody<AiRuntimeConfig>>('/admin/ai/runtime-config').then(unwrapData)
  },

  updateRuntimeConfig(input: AiRuntimeConfigUpdateInput): Promise<AiRuntimeConfig> {
    return httpClient
      .put<HttpMessageBody<AiRuntimeConfig>, AiRuntimeConfigUpdateInput>('/admin/ai/runtime-config', input)
      .then(unwrapData)
  },

  retryRuntimeConfigPush(): Promise<AiRuntimeConfig> {
    return httpClient
      .post<HttpMessageBody<AiRuntimeConfig>, Record<string, never>>('/admin/ai/runtime-config/retry-push', {})
      .then(unwrapData)
  },

  /** 用量聚合（Issue #140 UoW-2）：窗口内一次返回 daily/byAgent/byModel/recent；source 省略＝全部。 */
  getAiUsage(query: { source?: AiUsageSource; days?: number; limit?: number }): Promise<AiUsageReport> {
    return httpClient
      .get<HttpMessageBody<AiUsageReport>>(`/admin/ai/usage${toQueryString({ ...query })}`)
      .then(unwrapData)
  },

  listGatewayAccessKeys(): Promise<GatewayAccessKey[]> {
    return httpClient
      .get<HttpMessageBody<GatewayAccessKey[]>>('/admin/ai/gateway-access-keys')
      .then(unwrapData)
  },

  issueGatewayAccessKey(input: GatewayAccessKeyIssueInput): Promise<GatewayAccessKeyIssueResponse> {
    return httpClient
      .post<HttpMessageBody<GatewayAccessKeyIssueResponse>, GatewayAccessKeyIssueInput>(
        '/admin/ai/gateway-access-keys',
        input,
      )
      .then(unwrapData)
  },

  revokeGatewayAccessKey(id: number): Promise<GatewayAccessKey> {
    return httpClient
      .post<HttpMessageBody<GatewayAccessKey>, Record<string, never>>(
        `/admin/ai/gateway-access-keys/${id}/revoke`,
        {},
      )
      .then(unwrapData)
  },

  getPromptProfile(agentId: number): Promise<AiPromptProfile> {
    return httpClient
      .get<HttpMessageBody<AiPromptProfile>>(`${aiBase(agentId)}/prompt-profile`)
      .then(unwrapData)
  },

  upsertPromptProfile(agentId: number, input: UpdateAiPromptProfileInput): Promise<AiPromptProfile> {
    return httpClient
      .put<HttpMessageBody<AiPromptProfile>, UpdateAiPromptProfileInput>(
        `${aiBase(agentId)}/prompt-profile`,
        input,
      )
      .then(unwrapData)
  },

  listKnowledgeDocuments(
    agentId: number,
    query: AiKnowledgeListQuery = {},
  ): Promise<PageResult<AiKnowledgeListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      status: query.status,
      baseId: query.baseId,
    })
    return httpClient
      .get<HttpMessageBody<PageResult<AiKnowledgeListItem>>>(
        `${aiBase(agentId)}/knowledge-documents${qs}`,
      )
      .then(unwrapData)
  },

  getKnowledgeDocument(agentId: number, id: number): Promise<AiKnowledgeDetail> {
    return httpClient
      .get<HttpMessageBody<AiKnowledgeDetail>>(`${aiBase(agentId)}/knowledge-documents/${id}`)
      .then(unwrapData)
  },

  createKnowledgeDocument(agentId: number, input: CreateAiKnowledgeInput): Promise<AiKnowledgeDetail> {
    return httpClient
      .post<HttpMessageBody<AiKnowledgeDetail>, CreateAiKnowledgeInput>(
        `${aiBase(agentId)}/knowledge-documents`,
        input,
      )
      .then(unwrapData)
  },

  uploadKnowledgeDocument(
    agentId: number,
    file: File,
    title?: string,
    baseId?: number | null,
  ): Promise<AiKnowledgeDetail> {
    const form = new FormData()
    form.append('file', file)
    const trimmed = title?.trim()
    if (trimmed) {
      form.append('title', trimmed)
    }
    if (baseId != null) {
      form.append('baseId', String(baseId))
    }
    return httpClient
      .post<HttpMessageBody<AiKnowledgeDetail>, FormData>(
        `${aiBase(agentId)}/knowledge-documents/upload`,
        form,
        { timeout: UPLOAD_TIMEOUT_MS },
      )
      .then(unwrapData)
  },

  uploadKnowledgeArchive(
    agentId: number,
    file: File,
    baseId?: number | null,
  ): Promise<AiKnowledgeArchiveResult> {
    const form = new FormData()
    form.append('file', file)
    if (baseId != null) {
      form.append('baseId', String(baseId))
    }
    return httpClient
      .post<HttpMessageBody<AiKnowledgeArchiveResult>, FormData>(
        `${aiBase(agentId)}/knowledge-documents/upload-archive`,
        form,
        { timeout: UPLOAD_TIMEOUT_MS },
      )
      .then(unwrapData)
  },

  getKnowledgeDocumentFile(
    agentId: number,
    id: number,
  ): Promise<{ url: string; filename: string | null; sourceFormat: string | null }> {
    return httpClient
      .get<HttpMessageBody<{ url: string; filename: string | null; sourceFormat: string | null }>>(
        `${aiBase(agentId)}/knowledge-documents/${id}/file`,
      )
      .then(unwrapData)
  },

  reindexKnowledgeDocument(agentId: number, id: number): Promise<AiKnowledgeDetail> {
    return httpClient
      .post<HttpMessageBody<AiKnowledgeDetail>, Record<string, never>>(
        `${aiBase(agentId)}/knowledge-documents/${id}/reindex`,
        {},
      )
      .then(unwrapData)
  },

  updateKnowledgeDocument(
    agentId: number,
    id: number,
    input: UpdateAiKnowledgeInput,
  ): Promise<AiKnowledgeDetail> {
    return httpClient
      .put<HttpMessageBody<AiKnowledgeDetail>, UpdateAiKnowledgeInput>(
        `${aiBase(agentId)}/knowledge-documents/${id}`,
        input,
      )
      .then(unwrapData)
  },

  listKnowledgeBases(agentId: number): Promise<AiKnowledgeBase[]> {
    return httpClient
      .get<HttpMessageBody<AiKnowledgeBase[]>>(`${aiBase(agentId)}/knowledge-bases`)
      .then(unwrapData)
  },

  createKnowledgeBase(agentId: number, name: string): Promise<AiKnowledgeBase> {
    return httpClient
      .post<HttpMessageBody<AiKnowledgeBase>, { name: string }>(`${aiBase(agentId)}/knowledge-bases`, {
        name,
      })
      .then(unwrapData)
  },

  deleteKnowledgeBase(agentId: number, baseId: number): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null>>(`${aiBase(agentId)}/knowledge-bases/${baseId}`)
      .then(() => undefined)
  },

  listAllKnowledgeBases(): Promise<AiKnowledgeBase[]> {
    return httpClient
      .get<HttpMessageBody<AiKnowledgeBase[]>>(`/admin/ai/knowledge-bases`)
      .then(unwrapData)
  },

  linkKnowledgeBase(baseId: number, targetAgentId: number): Promise<void> {
    return httpClient
      .post<HttpMessageBody<null>>(`/admin/ai/knowledge-bases/${baseId}/links/${targetAgentId}`, {})
      .then(() => undefined)
  },

  unlinkKnowledgeBase(baseId: number, targetAgentId: number): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null>>(`/admin/ai/knowledge-bases/${baseId}/links/${targetAgentId}`)
      .then(() => undefined)
  },

  getKnowledgeOverview(): Promise<AiKnowledgeOverview> {
    return httpClient
      .get<HttpMessageBody<AiKnowledgeOverview>>(`/admin/ai/knowledge-overview`)
      .then(unwrapData)
  },

  reindexFailedKnowledge(): Promise<{ attempted: number; succeeded: number }> {
    return httpClient
      .post<HttpMessageBody<{ attempted: number; succeeded: number }>>(
        `/admin/ai/knowledge/reindex-failed`,
        {},
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

  deleteKnowledgeDocument(agentId: number, id: number): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null | void>>(`${aiBase(agentId)}/knowledge-documents/${id}`)
      .then(unwrapVoid)
  },

  listConversations(
    agentId: number,
    query: AiConversationListQuery = {},
  ): Promise<PageResult<AiConversationListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
    })
    return httpClient
      .get<HttpMessageBody<PageResult<AiConversationListItem>>>(`${aiBase(agentId)}/conversations${qs}`)
      .then(unwrapData)
  },

  listConversationMessages(agentId: number, conversationId: number): Promise<AiMessage[]> {
    return httpClient
      .get<HttpMessageBody<AiMessage[]>>(`${aiBase(agentId)}/conversations/${conversationId}/messages`)
      .then(unwrapData)
  },

  listPreviewSkills(agentId: number): Promise<AiPreviewSkill[]> {
    return httpClient
      .get<HttpMessageBody<AiPreviewSkill[]>>(`${aiBase(agentId)}/preview/skills`)
      .then(unwrapData)
  },

  /** mode 缺省时拿服务端默认判定与其模型；给了就按该模式回填。 */
  getPreviewModel(agentId: number, mode?: 'CLI' | 'KEY'): Promise<AiPreviewModelBackfill> {
    const qs = toQueryString({ mode })
    return httpClient
      .get<HttpMessageBody<AiPreviewModelBackfill>>(`${aiBase(agentId)}/preview/model${qs}`)
      .then(unwrapData)
  },

  getRuntime(agentId: number): Promise<AiRuntime> {
    return httpClient.get<HttpMessageBody<AiRuntime>>(`${aiBase(agentId)}/runtime`).then(unwrapData)
  },

  probeMcp(agentId: number): Promise<AiMcpReady> {
    return httpClient.get<HttpMessageBody<AiMcpReady>>(`${aiBase(agentId)}/mcp-ready`).then(unwrapData)
  },
}

export function previewChatStreamUrl(agentId: number) {
  const base = import.meta.env.VITE_API_BASE_URL ?? '/api'
  return `${base}${aiBase(agentId)}/preview/chat/stream`
}

/** SSE 预览走 fetch：axios 默认超时不适合流式响应。 */
export function streamPreviewChat(
  agentId: number,
  body: AiPreviewChatInput,
  abortSignal?: AbortSignal,
): Promise<Response> {
  const token = getAccessToken()
  return fetch(previewChatStreamUrl(agentId), {
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
