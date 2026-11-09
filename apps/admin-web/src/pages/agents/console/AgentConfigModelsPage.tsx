import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import { AgentAiChainPresetSection } from '../agentAiChainPresetSection'
import type { AgentWorkspaceOutletContext } from '../agentWorkspaceLayout'

/** PR-2 薄容器：复用现有模型链编辑器；完整配置重构留给 PR-4。 */
export function AgentConfigModelsPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const allowed = useHasPermission(PERMISSION_CODES.aiModelRead)

  if (!allowed) {
    return <p className="text-sm text-admin-muted">{t('aiWorkspace.noPermission')}</p>
  }

  return (
    <article className="rounded-xl border border-admin bg-admin-surface p-4 sm:p-5">
      <AgentAiChainPresetSection agentId={site.id} />
    </article>
  )
}
