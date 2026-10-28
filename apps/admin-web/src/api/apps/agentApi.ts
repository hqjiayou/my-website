/**
 * 站点管理 API。
 * Site Core（list/get/create/update/delete/status）→ 始终 REAL `/admin/agents`。
 * runtime-status：由现有 REAL API 推导（见 agentRuntimeStatus.ts），非独立端点。
 */
import { deriveAgentRuntimeStatus } from '@/api/apps/agentRuntimeStatus'
import type {
  CreateAgentInput,
  PageResult,
  AgentDetail,
  AgentListItem,
  AgentListQuery,
  AgentRuntimeStatus,
  UpdateAgentInput,
  UpdateAgentStatusInput,
} from '@/types/site'
import { SITE_TYPE_VALUES } from '@/types/site'
import type { HttpMessageBody } from '@/types/rbac'
import { ApiError } from '@/utils/http/apiError'
import { httpClient } from '@/utils/http/axios'
import { unwrapData } from '@/utils/http/unwrap'

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

const agentHttpService = {
  listSites(query: AgentListQuery = {}): Promise<PageResult<AgentListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      status: query.status,
      agentType: query.agentType,
    })
    return httpClient.get<HttpMessageBody<PageResult<AgentListItem>>>(`/admin/agents${qs}`).then(unwrapData)
  },

  getSite(id: number): Promise<AgentDetail> {
    return httpClient.get<HttpMessageBody<AgentDetail>>(`/admin/agents/${id}`).then(unwrapData)
  },

  createSite(input: CreateAgentInput): Promise<AgentDetail> {
    return httpClient.post<HttpMessageBody<AgentDetail>, CreateAgentInput>('/admin/agents', input).then(unwrapData)
  },

  updateSite(id: number, input: UpdateAgentInput): Promise<AgentDetail> {
    return httpClient.put<HttpMessageBody<AgentDetail>, UpdateAgentInput>(`/admin/agents/${id}`, input).then(unwrapData)
  },

  deleteSite(id: number): Promise<void> {
    return httpClient.delete<HttpMessageBody<null>>(`/admin/agents/${id}`).then(unwrapVoid)
  },

  updateAgentStatus(id: number, input: UpdateAgentStatusInput): Promise<AgentDetail> {
    return httpClient
      .patch<HttpMessageBody<AgentDetail>, UpdateAgentStatusInput>(`/admin/agents/${id}/status`, input)
      .then(unwrapData)
  },

  getAgentRuntimeStatus(id: number): Promise<AgentRuntimeStatus> {
    return deriveAgentRuntimeStatus(id)
  },
}

/** Site Core 与 runtime-status 推导均走真实 HTTP / REAL 数据聚合。 */
export const agentApi = {
  listSites: agentHttpService.listSites,
  getSite: agentHttpService.getSite,
  createSite: agentHttpService.createSite,
  updateSite: agentHttpService.updateSite,
  deleteSite: agentHttpService.deleteSite,
  updateAgentStatus: agentHttpService.updateAgentStatus,
  getAgentRuntimeStatus: agentHttpService.getAgentRuntimeStatus,
}

export type {
  CreateAgentInput,
  PageResult,
  AgentDetail,
  AgentListItem,
  AgentListQuery,
  AgentRuntimeStatus,
  AgentStatus,
  AgentType,
  UpdateAgentInput,
  UpdateAgentStatusInput,
} from '@/types/site'

export type { Site } from '@/types/site'

export {
  SITE_STATUS,
  SITE_STATUS_VALUES,
  SITE_TYPE_VALUES,
  SITE_TYPE_PRESETS,
  SITE_TAG_PATTERN,
  SITE_CODE_PATTERN,
} from '@/types/site'

/** @deprecated 旧别名：现指向预设用途标签。 */
export const SITE_INDUSTRY_VALUES = SITE_TYPE_VALUES
