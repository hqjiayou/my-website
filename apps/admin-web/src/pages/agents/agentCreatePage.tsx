import { useNavigate } from 'react-router-dom'

import { ROUTES } from '@/router/config/routePaths'

import { AgentCreateDialog } from './agentCreateDialog'

/** /agent/create 深链接兼容：新建已弹窗化，本页只承载弹窗，关闭回列表。 */
export function AgentCreatePage() {
  const navigate = useNavigate()
  return (
    <section className="admin-content-form">
      <AgentCreateDialog onClose={() => navigate(ROUTES.apps.sites)} />
    </section>
  )
}
