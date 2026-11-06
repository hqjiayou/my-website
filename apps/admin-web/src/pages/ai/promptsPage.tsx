import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'
import { AgentAiPromptPanel } from '@/pages/agents/agentAiPromptPanel'

import { AiAgentScope } from './aiAgentScope'

export function AiPromptsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('prompts.title')} description={t('prompts.description')} />
      <AiAgentScope>
        {(agentId) => (
          <div className="rounded-[14px] border border-admin bg-admin-surface p-5">
            <AgentAiPromptPanel agentId={agentId} />
          </div>
        )}
      </AiAgentScope>
    </section>
  )
}
