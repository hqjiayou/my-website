import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/page'
import { AgentAiPromptPanel } from '@/pages/apps/sites/agentAiPromptPanel'

import { AiAgentScope } from './aiAgentScope'

export function AiPromptsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section>
      <PageHeader title={t('prompts.title')} description={t('prompts.description')} />
      <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <AiAgentScope>{(agentId) => <AgentAiPromptPanel agentId={agentId} />}</AiAgentScope>
      </article>
    </section>
  )
}
