import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import { aiApi } from '@/api/apps/aiApi'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import { AgentAiKnowledgePanel } from '../agentAiKnowledgePanel'
import type { AgentWorkspaceOutletContext } from '../agentWorkspaceLayout'
import { AgentConfigSectionShell } from './AgentConfigSectionShell'
import { AgentKnowledgeSubscriptions } from './AgentKnowledgeSubscriptions'

export function AgentConfigKnowledgePage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const allowed = useHasPermission(PERMISSION_CODES.aiKbList)
  const basesQuery = useQuery({
    queryKey: ['ai-knowledge-bases-all'],
    queryFn: () => aiApi.listAllKnowledgeBases(),
    enabled: allowed,
  })

  if (!allowed) {
    return <p className="text-sm text-admin-muted">{t('aiWorkspace.noPermission')}</p>
  }

  const linked = basesQuery.data?.filter(
    (base) => base.agentId === site.id || base.linkedAgentIds.includes(site.id),
  ).length
  const summary = basesQuery.isPending ? (
    t('workspace.loading')
  ) : basesQuery.isError ? (
    <button type="button" className="underline" onClick={() => void basesQuery.refetch()}>
      {t('aiWorkspace.retry')}
    </button>
  ) : (
    t('workspace.configKnowledge.summary', { linked: linked ?? 0 })
  )

  return (
    <AgentConfigSectionShell
      title={t('workspace.configKnowledge.title')}
      description={t('workspace.configKnowledge.pageDescription')}
      summary={summary}
    >
      <AgentKnowledgeSubscriptions key={site.id} agentId={site.id} />
      <div className="space-y-2 border-t border-admin pt-4">
        <h3 className="text-sm font-semibold text-admin">{t('workspace.configKnowledge.documentsTitle')}</h3>
        <AgentAiKnowledgePanel key={site.id} agentId={site.id} />
      </div>
    </AgentConfigSectionShell>
  )
}
