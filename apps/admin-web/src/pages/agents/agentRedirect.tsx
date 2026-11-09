import { Navigate, useParams } from 'react-router-dom'

import { ROUTES } from '@/router/config/routePaths'

function useAgentIdParam() {
  const { agentId } = useParams()
  return agentId ?? ''
}

/** 旧 /overview 与 /ai 路径统一重定向到 Agent 总览（工作台落地页）。 */
export function RedirectToAgent() {
  const agentId = useAgentIdParam()
  return <Navigate to={ROUTES.apps.agent(agentId)} replace />
}

/** 旧路径兼容：一律用绝对 ROUTES，避免相对 Navigate 解析漂移。 */
export function RedirectToAgentSettings() {
  const agentId = useAgentIdParam()
  return <Navigate to={ROUTES.apps.agentSettings(agentId)} replace />
}

export function RedirectToAgentConfigPrompt() {
  const agentId = useAgentIdParam()
  return <Navigate to={ROUTES.apps.agentConfigPrompt(agentId)} replace />
}

export function RedirectToAgentConfigKnowledge() {
  const agentId = useAgentIdParam()
  return <Navigate to={ROUTES.apps.agentConfigKnowledge(agentId)} replace />
}

export function RedirectToAgentConfigTools() {
  const agentId = useAgentIdParam()
  return <Navigate to={ROUTES.apps.agentConfigTools(agentId)} replace />
}

export function RedirectToAgentDebug() {
  const agentId = useAgentIdParam()
  return <Navigate to={ROUTES.apps.agentDebug(agentId)} replace />
}

export function RedirectToAgentRuns() {
  const agentId = useAgentIdParam()
  return <Navigate to={ROUTES.apps.agentRuns(agentId)} replace />
}
