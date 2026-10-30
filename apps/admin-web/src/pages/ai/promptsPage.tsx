import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'
import { AgentAiPromptPanel } from '@/pages/apps/sites/agentAiPromptPanel'

import { AiAgentScope } from './aiAgentScope'

export function AiPromptsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('prompts.title')} description={t('prompts.description')} />
      <article className="rounded-xl border border-border bg-card p-5">
        <AiAgentScope>{(agentId) => <AgentAiPromptPanel agentId={agentId} />}</AiAgentScope>
      </article>
    </section>
  )
}
