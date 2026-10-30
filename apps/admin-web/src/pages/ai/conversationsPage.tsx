import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'
import { AgentAiConversationsPanel } from '@/pages/apps/sites/agentAiConversationsPanel'

import { AiAgentScope } from './aiAgentScope'

export function AiConversationsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section className="admin-content-data space-y-4">
      <AdminPageHeader title={t('conversations.title')} description={t('conversations.description')} />
      {/* panel 自带卡片边框/圆角，这里不再套一层 article，避免与 panel 的 md-grid 卡片重复加边框。 */}
      <AiAgentScope>{(agentId) => <AgentAiConversationsPanel agentId={agentId} />}</AiAgentScope>
    </section>
  )
}
