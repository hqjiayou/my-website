import type { ComponentType } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import { PageHeader } from '@/components/page'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import { AgentAiConversationsPanel } from './agentAiConversationsPanel'
import { AgentAiKnowledgePanel } from './agentAiKnowledgePanel'
import { AgentAiPreviewChat } from './agentAiPreviewChat'
import { AgentAiPromptPanel } from './agentAiPromptPanel'
import { AgentAiRuntimePanel } from './agentAiRuntimePanel'
import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

/**
 * Agent 一级页：原 AgentAiPage 的五个子页签提升为工作台一级路由后的挂载壳。
 * 面板组件本身不变；这里只负责标题、权限门与卡片容器（与旧 AI 页一致）。
 */
function agentPanelPage(
  navKey: string,
  permission: string,
  Panel: ComponentType<{ agentId: number }>,
) {
  return function AgentPanelPage() {
    const { t } = useTranslation('sites')
    const { site } = useOutletContext<AgentWorkspaceOutletContext>()
    const allowed = useHasPermission(permission)

    return (
      <section>
        <PageHeader title={t(`workspace.nav.${navKey}`)} description={t('aiWorkspace.description')} />
        {allowed ? (
          <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <Panel agentId={site.id} />
          </article>
        ) : (
          <p className="rounded-xl border border-slate-200 bg-white px-4 py-6 text-sm text-slate-500 shadow-sm">
            {t('aiWorkspace.noPermission')}
          </p>
        )}
      </section>
    )
  }
}

export const AgentPromptPage = agentPanelPage('agentPrompt', PERMISSION_CODES.aiPromptRead, AgentAiPromptPanel)
export const AgentKnowledgePage = agentPanelPage(
  'agentKnowledge',
  PERMISSION_CODES.aiKbList,
  AgentAiKnowledgePanel,
)
export const AgentConversationsPage = agentPanelPage(
  'agentConversations',
  PERMISSION_CODES.aiConversationList,
  AgentAiConversationsPanel,
)
export const AgentPreviewPage = agentPanelPage('agentPreview', PERMISSION_CODES.aiPromptRead, AgentAiPreviewChat)
export const AgentRuntimePage = agentPanelPage('agentRuntime', PERMISSION_CODES.aiPromptRead, AgentAiRuntimePanel)
