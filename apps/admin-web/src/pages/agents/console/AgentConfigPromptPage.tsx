import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import { AgentAiPromptPanel } from '../agentAiPromptPanel'
import type { AgentWorkspaceOutletContext } from '../agentWorkspaceLayout'
import { AgentConfigSectionShell } from './AgentConfigSectionShell'

export function AgentConfigPromptPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const allowed = useHasPermission(PERMISSION_CODES.aiPromptRead)

  if (!allowed) {
    return <p className="text-sm text-admin-muted">{t('aiWorkspace.noPermission')}</p>
  }

  return (
    <AgentConfigSectionShell
      title={t('workspace.configPrompt.title')}
      description={t('workspace.configPrompt.pageDescription')}
    >
      <div className="rounded-xl border border-admin bg-admin-surface p-4 sm:p-5">
        <AgentAiPromptPanel key={site.id} agentId={site.id} />
      </div>
    </AgentConfigSectionShell>
  )
}
