import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import { AgentAiRuntimePanel } from '../agentAiRuntimePanel'
import type { AgentWorkspaceOutletContext } from '../agentWorkspaceLayout'
import { AgentConfigSectionShell } from './AgentConfigSectionShell'

export function AgentConfigToolsPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const allowed = useHasPermission(PERMISSION_CODES.aiPromptRead)

  if (!allowed) {
    return <p className="text-sm text-admin-muted">{t('aiWorkspace.noPermission')}</p>
  }

  return (
    <AgentConfigSectionShell
      title={t('workspace.configTools.title')}
      description={t('workspace.configTools.pageDescription')}
    >
      <AgentAiRuntimePanel key={site.id} agentId={site.id} />
    </AgentConfigSectionShell>
  )
}
