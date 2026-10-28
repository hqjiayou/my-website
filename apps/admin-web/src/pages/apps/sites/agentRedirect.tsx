import { Navigate, useParams } from 'react-router-dom'

import { ROUTES } from '@/router/config/routePaths'

/** 旧 /overview 与 /ai 路径统一重定向到 Agent 总览（工作台落地页）。 */
export function RedirectToAgent() {
  const { agentId } = useParams()
  return <Navigate to={ROUTES.apps.agent(agentId ?? '')} replace />
}
