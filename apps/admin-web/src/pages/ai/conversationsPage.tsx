import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'
import { AgentAiConversationsPanel } from '@/pages/apps/sites/agentAiConversationsPanel'

import { AiAgentScope } from './aiAgentScope'

export function AiConversationsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section>
      <AdminPageHeader title={t('conversations.title')} description={t('conversations.description')} />
      <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <AiAgentScope>{(agentId) => <AgentAiConversationsPanel agentId={agentId} />}</AiAgentScope>
      </article>
    </section>
  )
}
