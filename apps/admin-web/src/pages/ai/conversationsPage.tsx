import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'
import { AgentAiConversationsPanel } from '@/pages/apps/sites/agentAiConversationsPanel'

import { AiAgentScope } from './aiAgentScope'

export function AiConversationsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('conversations.title')} description={t('conversations.description')} />
      <article className="rounded-xl border border-border bg-card p-5">
        <AiAgentScope>{(agentId) => <AgentAiConversationsPanel agentId={agentId} />}</AiAgentScope>
      </article>
    </section>
  )
}
