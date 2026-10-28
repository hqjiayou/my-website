import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/page'
import { AgentAiConversationsPanel } from '@/pages/apps/sites/agentAiConversationsPanel'

import { AiAgentScope } from './aiAgentScope'

export function AiConversationsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section>
      <PageHeader title={t('conversations.title')} description={t('conversations.description')} />
      <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <AiAgentScope>{(agentId) => <AgentAiConversationsPanel agentId={agentId} />}</AiAgentScope>
      </article>
    </section>
  )
}
