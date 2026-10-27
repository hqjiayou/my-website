import type { ComponentType } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import { PageHeader } from '@/components/page'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import { SiteAiConversationsPanel } from './siteAiConversationsPanel'
import { SiteAiKnowledgePanel } from './siteAiKnowledgePanel'
import { SiteAiPreviewChat } from './siteAiPreviewChat'
import { SiteAiPromptPanel } from './siteAiPromptPanel'
import { SiteAiRuntimePanel } from './siteAiRuntimePanel'
import type { SiteWorkspaceOutletContext } from './siteWorkspaceLayout'

/**
 * Agent 一级页：原 SiteAiPage 的五个子页签提升为工作台一级路由后的挂载壳。
 * 面板组件本身不变；这里只负责标题、权限门与卡片容器（与旧 AI 页一致）。
 */
function agentPanelPage(
  navKey: string,
  permission: string,
  Panel: ComponentType<{ siteId: number }>,
) {
  return function AgentPanelPage() {
    const { t } = useTranslation('sites')
    const { site } = useOutletContext<SiteWorkspaceOutletContext>()
    const allowed = useHasPermission(permission)

    return (
      <section>
        <PageHeader title={t(`workspace.nav.${navKey}`)} description={t('aiWorkspace.description')} />
        {allowed ? (
          <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <Panel siteId={site.id} />
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

export const SiteAgentPromptPage = agentPanelPage('agentPrompt', PERMISSION_CODES.aiPromptRead, SiteAiPromptPanel)
export const SiteAgentKnowledgePage = agentPanelPage(
  'agentKnowledge',
  PERMISSION_CODES.aiKbList,
  SiteAiKnowledgePanel,
)
export const SiteAgentConversationsPage = agentPanelPage(
  'agentConversations',
  PERMISSION_CODES.aiConversationList,
  SiteAiConversationsPanel,
)
export const SiteAgentPreviewPage = agentPanelPage('agentPreview', PERMISSION_CODES.aiPromptRead, SiteAiPreviewChat)
export const SiteAgentRuntimePage = agentPanelPage('agentRuntime', PERMISSION_CODES.aiPromptRead, SiteAiRuntimePanel)
