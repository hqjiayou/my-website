import { Navigate, Route } from 'react-router-dom'

import { ForbiddenPage } from '@/pages/forbidden/forbiddenPage'

import { ROUTES } from '../config/routePaths'

import { lazyNamed } from './lazyNamed'

// ForbiddenPage 由 PermissionGuard 同步引用，路由侧保持 eager，避免重复拆包。
const DashboardPage = lazyNamed(() => import('@/pages/dashboard/dashboardPage'), 'DashboardPage')

const UserListPage = lazyNamed(() => import('@/pages/users/userListPage'), 'UserListPage')

const RoleListPage = lazyNamed(() => import('@/pages/roles/roleListPage'), 'RoleListPage')

const MenuTreePage = lazyNamed(() => import('@/pages/menus/menuTreePage'), 'MenuTreePage')

const AgentsPage = lazyNamed(() => import('@/pages/agents/agentsPage'), 'AgentsPage')
const AgentCreatePage = lazyNamed(() => import('@/pages/agents/agentCreatePage'), 'AgentCreatePage')
const AgentWorkspaceLayout = lazyNamed(
  () => import('@/pages/agents/agentWorkspaceLayout'),
  'AgentWorkspaceLayout',
)
const RedirectToAgent = lazyNamed(
  () => import('@/pages/agents/agentRedirect'),
  'RedirectToAgent',
)
const RedirectToAgentSettings = lazyNamed(
  () => import('@/pages/agents/agentRedirect'),
  'RedirectToAgentSettings',
)
const RedirectToAgentConfigPrompt = lazyNamed(
  () => import('@/pages/agents/agentRedirect'),
  'RedirectToAgentConfigPrompt',
)
const RedirectToAgentConfigKnowledge = lazyNamed(
  () => import('@/pages/agents/agentRedirect'),
  'RedirectToAgentConfigKnowledge',
)
const RedirectToAgentConfigTools = lazyNamed(
  () => import('@/pages/agents/agentRedirect'),
  'RedirectToAgentConfigTools',
)
const RedirectToAgentDebug = lazyNamed(
  () => import('@/pages/agents/agentRedirect'),
  'RedirectToAgentDebug',
)
const RedirectToAgentRuns = lazyNamed(
  () => import('@/pages/agents/agentRedirect'),
  'RedirectToAgentRuns',
)
const AgentOverviewPage = lazyNamed(
  () => import('@/pages/agents/agentOverviewPage'),
  'AgentOverviewPage',
)
const AgentConversationsPage = lazyNamed(
  () => import('@/pages/agents/agentPages'),
  'AgentConversationsPage',
)
const AgentPreviewPage = lazyNamed(
  () => import('@/pages/agents/agentPages'),
  'AgentPreviewPage',
)
const AgentConfigLayout = lazyNamed(
  () => import('@/pages/agents/console/AgentConfigLayout'),
  'AgentConfigLayout',
)
const RedirectToAgentConfigIndex = lazyNamed(
  () => import('@/pages/agents/console/AgentConfigLayout'),
  'RedirectToAgentConfigIndex',
)
const AgentConfigModelsPage = lazyNamed(
  () => import('@/pages/agents/console/AgentConfigModelsPage'),
  'AgentConfigModelsPage',
)
const AgentConfigPromptPage = lazyNamed(
  () => import('@/pages/agents/console/AgentConfigPromptPage'),
  'AgentConfigPromptPage',
)
const AgentConfigKnowledgePage = lazyNamed(
  () => import('@/pages/agents/console/AgentConfigKnowledgePage'),
  'AgentConfigKnowledgePage',
)
const AgentConfigToolsPage = lazyNamed(
  () => import('@/pages/agents/console/AgentConfigToolsPage'),
  'AgentConfigToolsPage',
)
const AgentSettingsPage = lazyNamed(
  () => import('@/pages/agents/console/AgentSettingsPage'),
  'AgentSettingsPage',
)
const AgentThemePage = lazyNamed(() => import('@/pages/agents/agentThemePage'), 'AgentThemePage')
const AgentContentsPage = lazyNamed(
  () => import('@/pages/agents/agentContentsPage'),
  'AgentContentsPage',
)
const AgentFilesPage = lazyNamed(() => import('@/pages/agents/agentFilesPage'), 'AgentFilesPage')

const StyleSettingsPage = lazyNamed(
  () => import('@/pages/apps/styleSettingsPage'),
  'StyleSettingsPage',
)

const OperationContentsPage = lazyNamed(
  () => import('@/pages/operation/contentsPage'),
  'OperationContentsPage',
)
const OperationFilesPage = lazyNamed(
  () => import('@/pages/operation/filesPage'),
  'OperationFilesPage',
)

const AiSettingsPage = lazyNamed(() => import('@/pages/ai/settingsPage'), 'AiSettingsPage')
const AiUsagePage = lazyNamed(() => import('@/pages/ai/usagePage'), 'AiUsagePage')
const AiPromptsPage = lazyNamed(() => import('@/pages/ai/promptsPage'), 'AiPromptsPage')
const AiKnowledgeBasesPage = lazyNamed(
  () => import('@/pages/ai/knowledgeBasesPage'),
  'AiKnowledgeBasesPage',
)
const AiConversationsPage = lazyNamed(
  () => import('@/pages/ai/conversationsPage'),
  'AiConversationsPage',
)
const AiModelEntriesPage = lazyNamed(
  () => import('@/pages/ai/modelEntriesPage'),
  'AiModelEntriesPage',
)
const AiPromptTemplatesPage = lazyNamed(
  () => import('@/pages/ai/promptTemplatesPage'),
  'AiPromptTemplatesPage',
)
const AiMcpServersPage = lazyNamed(
  () => import('@/pages/ai/mcpServersPage'),
  'AiMcpServersPage',
)
const AiSkillsPage = lazyNamed(
  () => import('@/pages/ai/skillsPage'),
  'AiSkillsPage',
)

const ParamsConfigPage = lazyNamed(
  () => import('@/pages/config/paramsConfigPage'),
  'ParamsConfigPage',
)
const AnalyticsPage = lazyNamed(() => import('@/pages/analytics/analyticsPage'), 'AnalyticsPage')
const SystemSettingsPage = lazyNamed(
  () => import('@/pages/system/systemSettingsPage'),
  'SystemSettingsPage',
)
const DeveloperPage = lazyNamed(() => import('@/pages/developer/developerPage'), 'DeveloperPage')

/** AdminLayout 下业务路由片段；须 `{adminRoutes}` 展开（RR v7 只接受 Route / Fragment 子节点）。 */
export const adminRoutes = (
  <>
    <Route index element={<Navigate to={ROUTES.dashboard} replace />} />
    <Route path={ROUTES.forbidden} element={<ForbiddenPage />} />
    <Route path={ROUTES.dashboard} element={<DashboardPage />} />

    <Route path={ROUTES.system.userCreate} element={<Navigate to={ROUTES.system.users} replace />} />
    <Route path={ROUTES.system.userEdit(':userId')} element={<Navigate to={ROUTES.system.users} replace />} />
    <Route path={ROUTES.system.userRoles(':userId')} element={<Navigate to={ROUTES.system.users} replace />} />
    <Route path={ROUTES.system.users} element={<UserListPage />} />
    <Route path={ROUTES.system.roleCreate} element={<Navigate to={ROUTES.system.roles} replace />} />
    <Route path={ROUTES.system.roleEdit(':roleId')} element={<Navigate to={ROUTES.system.roles} replace />} />
    <Route path={ROUTES.system.rolePermissions(':roleId')} element={<Navigate to={ROUTES.system.roles} replace />} />
    <Route path={ROUTES.system.roles} element={<RoleListPage />} />
    <Route path={ROUTES.system.permissions} element={<Navigate to={ROUTES.system.roles} replace />} />
    <Route path={ROUTES.system.menus} element={<MenuTreePage />} />

    <Route path={ROUTES.apps.sites} element={<AgentsPage />} />
    <Route path={ROUTES.apps.agentCreate} element={<AgentCreatePage />} />
    <Route path={ROUTES.apps.agentDetailPattern} element={<AgentWorkspaceLayout />}>
      <Route index element={<Navigate to="agent" replace />} />
      <Route path="overview" element={<RedirectToAgent />} />
      {/* 旧设置路径：有等价能力的进 settings；主题/内容/文件仍可达。 */}
      <Route path="basic" element={<RedirectToAgentSettings />} />
      <Route path="access" element={<RedirectToAgentSettings />} />
      <Route path="theme" element={<AgentThemePage />} />
      <Route path="domain" element={<RedirectToAgentSettings />} />
      <Route path="contents" element={<AgentContentsPage />} />
      <Route path="ai" element={<RedirectToAgent />} />
      <Route path="agent">
        <Route index element={<AgentOverviewPage />} />
        <Route path="config" element={<AgentConfigLayout />}>
          <Route index element={<RedirectToAgentConfigIndex />} />
          <Route path="models" element={<AgentConfigModelsPage />} />
          <Route path="prompt" element={<AgentConfigPromptPage />} />
          <Route path="knowledge" element={<AgentConfigKnowledgePage />} />
          <Route path="tools" element={<AgentConfigToolsPage />} />
        </Route>
        <Route path="debug" element={<AgentPreviewPage />} />
        <Route path="runs" element={<AgentConversationsPage />} />
        <Route path="settings" element={<AgentSettingsPage />} />
        {/* 旧 Agent 子路径 → V2 等价入口（绝对路径，禁止相对解析漂移）。 */}
        <Route path="prompt" element={<RedirectToAgentConfigPrompt />} />
        <Route path="knowledge" element={<RedirectToAgentConfigKnowledge />} />
        <Route path="preview" element={<RedirectToAgentDebug />} />
        <Route path="conversations" element={<RedirectToAgentRuns />} />
        <Route path="runtime" element={<RedirectToAgentConfigTools />} />
      </Route>
      <Route path="files" element={<AgentFilesPage />} />
    </Route>
    <Route path={ROUTES.apps.style} element={<StyleSettingsPage />} />

    <Route path={ROUTES.operation.contents} element={<OperationContentsPage />} />
    <Route path={ROUTES.operation.files} element={<OperationFilesPage />} />

    <Route path={ROUTES.ai.settings} element={<AiSettingsPage />} />
    <Route path={ROUTES.ai.usage} element={<AiUsagePage />} />
    <Route path={ROUTES.ai.prompts} element={<AiPromptsPage />} />
    <Route path={ROUTES.ai.knowledgeBases} element={<AiKnowledgeBasesPage />} />
    <Route path={ROUTES.ai.conversations} element={<AiConversationsPage />} />
    <Route path={ROUTES.ai.modelEntries} element={<AiModelEntriesPage />} />
    <Route path={ROUTES.ai.promptTemplates} element={<AiPromptTemplatesPage />} />
    <Route path={ROUTES.ai.mcpServers} element={<AiMcpServersPage />} />
    <Route path={ROUTES.ai.skills} element={<AiSkillsPage />} />

    <Route path={ROUTES.config.params} element={<ParamsConfigPage />} />
    <Route path={ROUTES.governance.analytics} element={<AnalyticsPage />} />
    {/* /team 为旧「人员管理」并行面，统一到系统用户管理 */}
    <Route path={ROUTES.governance.team} element={<Navigate to={ROUTES.system.users} replace />} />
    <Route path={ROUTES.governance.system} element={<SystemSettingsPage />} />
    <Route path={ROUTES.governance.developer} element={<DeveloperPage />} />
  </>
)
