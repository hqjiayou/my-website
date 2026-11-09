import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'

import { aiApi } from '@/api/apps/aiApi'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import { AgentAiChainPresetSection } from '../agentAiChainPresetSection'
import type { AgentWorkspaceOutletContext } from '../agentWorkspaceLayout'
import { AgentConfigSectionShell } from './AgentConfigSectionShell'

/** 配置 · 模型链：薄壳 + 既有链编辑器（ResourcePicker 已接入）。 */
export function AgentConfigModelsPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const allowed = useHasPermission(PERMISSION_CODES.aiModelRead)
  const chainQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'chain'],
    queryFn: () => aiApi.getAgentChain(site.id),
    enabled: allowed,
  })

  if (!allowed) {
    return <p className="text-sm text-admin-muted">{t('aiWorkspace.noPermission')}</p>
  }

  const cli = chainQuery.data?.cliEntryIds.length ?? 0
  const key = chainQuery.data?.keyEntryIds.length ?? 0

  return (
    <AgentConfigSectionShell
      title={t('aiWorkspace.chainPreset.title')}
      description={t('aiWorkspace.chainPreset.pageDescription')}
      summary={t('aiWorkspace.chainPreset.summary', { cli, key })}
    >
      <div className="rounded-xl border border-admin bg-admin-surface p-4 sm:p-5">
        <AgentAiChainPresetSection key={site.id} agentId={site.id} />
      </div>
    </AgentConfigSectionShell>
  )
}
