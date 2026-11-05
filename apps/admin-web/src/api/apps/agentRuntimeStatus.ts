import { appearanceApi } from '@/api/appearanceApi'
import { contentApi } from '@/api/apps/contentApi'
import { agentApi } from '@/api/apps/agentApi'
import type { AgentRuntimeStatus } from '@/types/site'

/**
 * 由现有 REAL API 推导站点概览指标（非独立 runtime-status 端点，不读 mock 开关）。
 * 访问量 / 当日 AI 会话暂无后端，返回 null。
 * AI 配置：已引用配置预设（configPresetId 非空）为完成（旧 prompt-profile
 * 端点已退役，#213 UoW-D；引用口径与工作台/总览一致）。
 */
export async function deriveAgentRuntimeStatus(agentId: number): Promise<AgentRuntimeStatus> {
  const site = await agentApi.getSite(agentId)

  const [appearance, contents] = await Promise.all([
    appearanceApi.getAppearance(agentId).catch(() => null),
    contentApi
      .listContents(agentId, { pageNum: 1, pageSize: 50, status: 'PUBLISHED' })
      .catch(() => ({ records: [], total: 0, pageNum: 1, pageSize: 50 })),
  ])

  return {
    agentId,
    visitCountToday: null,
    aiConversationCountToday: null,
    publishChecklist: {
      basicInfo: Boolean(site.agentName?.trim() && site.agentCode?.trim()),
      theme: appearance != null,
      domain: Boolean(site.defaultDomain?.trim()),
      homeContent: contents.records.length > 0,
      aiConfig: site.configPresetId != null,
    },
  }
}
