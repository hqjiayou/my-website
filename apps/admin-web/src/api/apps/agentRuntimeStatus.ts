import { appearanceApi } from '@/api/appearanceApi'
import { aiApi } from '@/api/apps/aiApi'
import { contentApi } from '@/api/apps/contentApi'
import { agentApi } from '@/api/apps/agentApi'
import type { AgentRuntimeStatus } from '@/types/site'

/**
 * 由现有 REAL API 推导站点概览指标（非独立 runtime-status 端点，不读 mock 开关）。
 * 访问量 / 当日 AI 会话暂无后端，返回 null。
 * AI 配置：agent 已有 Prompt 本地副本（#214 UoW-4：prompt 文本/温度/来源留痕
 * 任一非空）为完成；副本本体走 ai/prompt 配置端点读取（旧配置预设引用口径
 * 已随 #214 UoW-4 退役）。读取失败按未配置计，不阻断其余指标。
 */
export async function deriveAgentRuntimeStatus(agentId: number): Promise<AgentRuntimeStatus> {
  const site = await agentApi.getSite(agentId)

  const [appearance, contents, promptConfig] = await Promise.all([
    appearanceApi.getAppearance(agentId).catch(() => null),
    contentApi
      .listContents(agentId, { pageNum: 1, pageSize: 50, status: 'PUBLISHED' })
      .catch(() => ({ records: [], total: 0, pageNum: 1, pageSize: 50 })),
    aiApi.getAgentPromptConfig(agentId).catch(() => null),
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
      aiConfig:
        promptConfig != null &&
        (promptConfig.promptTemplateId != null ||
          Boolean(promptConfig.systemPrompt?.trim()) ||
          promptConfig.temperature != null),
    },
  }
}
