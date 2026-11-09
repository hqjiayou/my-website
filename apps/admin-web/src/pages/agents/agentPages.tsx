import type { ComponentType } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import { AgentAiPromptPanel } from './agentAiPromptPanel'
import { AgentAiConversationsPanel } from './agentAiConversationsPanel'
import { AgentAiKnowledgePanel } from './agentAiKnowledgePanel'
import { AgentAiPreviewChat } from './agentAiPreviewChat'
import { AgentAiRuntimePanel } from './agentAiRuntimePanel'
import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

/** 需要占满视口剩余高度的面板（预览 / 对话记录）。 */
const FILL_HEIGHT_PANELS = new Set(['agentPreview', 'agentConversations', 'agentKnowledge'])

/**
 * Agent 一级页：原 AgentAiPage 的五个子页签提升为工作台一级路由后的挂载壳。
 * 不再重复侧栏标题；内容贴齐工作台右栏，不套居中限宽。
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
    const fillHeight = FILL_HEIGHT_PANELS.has(navKey)

    return (
      <section className={fillHeight ? 'flex h-[calc(100vh-11rem)] min-h-[28rem] flex-col' : 'w-full'}>
        {allowed ? (
          <article
            className={
              fillHeight
                ? 'flex min-h-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card p-4 sm:p-5'
                : 'rounded-xl border border-border bg-card p-4 sm:p-5'
            }
          >
            {/* key：切换 Agent 时整体重挂载，面板内的草稿 / 预览 runtime / 调试状态不跨 Agent 泄漏。 */}
            <Panel key={site.id} agentId={site.id} />
          </article>
        ) : (
          <p className="rounded-xl border border-border bg-card px-4 py-6 text-sm text-muted-foreground">
            {t('aiWorkspace.noPermission')}
          </p>
        )}
      </section>
    )
  }
}

export const AgentPromptPage = agentPanelPage(
  'agentPrompt',
  PERMISSION_CODES.aiPromptRead,
  AgentAiPromptPanel,
)
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
