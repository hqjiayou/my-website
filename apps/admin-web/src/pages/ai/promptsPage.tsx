import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'
import { AgentAiConfigPresetPanel } from '@/pages/agents/agentAiConfigPresetPanel'

import { AiAgentScope } from './aiAgentScope'

export function AiPromptsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('prompts.title')} description={t('prompts.description')} />
      <AiAgentScope>
        {(agentId) => (
          <div className="rounded-[14px] border border-admin bg-admin-surface p-5">
            <AgentAiConfigPresetPanel agentId={agentId} />
          </div>
        )}
      </AiAgentScope>
    </section>
  )
}
