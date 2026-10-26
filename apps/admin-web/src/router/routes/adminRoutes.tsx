import { Navigate, Route } from 'react-router-dom'

import { ForbiddenPage } from '@/pages/forbidden/forbiddenPage'

import { ROUTES } from '../config/routePaths'

import { lazyNamed } from './lazyNamed'

// ForbiddenPage 由 PermissionGuard 同步引用，路由侧保持 eager，避免重复拆包。
const DashboardPage = lazyNamed(() => import('@/pages/dashboard/dashboardPage'), 'DashboardPage')

const UserEditPage = lazyNamed(() => import('@/pages/users/userEditPage'), 'UserEditPage')
const UserListPage = lazyNamed(() => import('@/pages/users/userListPage'), 'UserListPage')
const UserRolePage = lazyNamed(() => import('@/pages/users/userRolePage'), 'UserRolePage')

const RoleEditPage = lazyNamed(() => import('@/pages/roles/roleEditPage'), 'RoleEditPage')
const RoleListPage = lazyNamed(() => import('@/pages/roles/roleListPage'), 'RoleListPage')
const RolePermissionPage = lazyNamed(
  () => import('@/pages/roles/rolePermissionPage'),
  'RolePermissionPage',
)

const PermissionListPage = lazyNamed(
  () => import('@/pages/permissions/permissionListPage'),
  'PermissionListPage',
)
const MenuTreePage = lazyNamed(() => import('@/pages/menus/menuTreePage'), 'MenuTreePage')

const SitesPage = lazyNamed(() => import('@/pages/apps/sites/sitesPage'), 'SitesPage')
const SiteCreatePage = lazyNamed(() => import('@/pages/apps/sites/siteCreatePage'), 'SiteCreatePage')
const SiteWorkspaceLayout = lazyNamed(
  () => import('@/pages/apps/sites/siteWorkspaceLayout'),
  'SiteWorkspaceLayout',
)
const SiteOverviewPage = lazyNamed(
  () => import('@/pages/apps/sites/siteOverviewPage'),
  'SiteOverviewPage',
)
const SiteBasicPage = lazyNamed(() => import('@/pages/apps/sites/siteBasicPage'), 'SiteBasicPage')
const SiteThemePage = lazyNamed(() => import('@/pages/apps/sites/siteThemePage'), 'SiteThemePage')
const SiteDomainPage = lazyNamed(() => import('@/pages/apps/sites/siteDomainPage'), 'SiteDomainPage')
const SiteContentsPage = lazyNamed(
  () => import('@/pages/apps/sites/siteContentsPage'),
  'SiteContentsPage',
)
const SiteAiPage = lazyNamed(() => import('@/pages/apps/sites/siteAiPage'), 'SiteAiPage')
const SiteFilesPage = lazyNamed(() => import('@/pages/apps/sites/siteFilesPage'), 'SiteFilesPage')

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
const AiPromptsPage = lazyNamed(() => import('@/pages/ai/promptsPage'), 'AiPromptsPage')
const AiKnowledgeBasesPage = lazyNamed(
  () => import('@/pages/ai/knowledgeBasesPage'),
  'AiKnowledgeBasesPage',
)
const AiConversationsPage = lazyNamed(
  () => import('@/pages/ai/conversationsPage'),
  'AiConversationsPage',
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

    <Route path={ROUTES.system.userCreate} element={<UserEditPage />} />
    <Route path={ROUTES.system.userEdit(':userId')} element={<UserEditPage />} />
    <Route path={ROUTES.system.userRoles(':userId')} element={<UserRolePage />} />
    <Route path={ROUTES.system.users} element={<UserListPage />} />
    <Route path={ROUTES.system.roleCreate} element={<RoleEditPage />} />
    <Route path={ROUTES.system.roleEdit(':roleId')} element={<RoleEditPage />} />
    <Route path={ROUTES.system.rolePermissions(':roleId')} element={<RolePermissionPage />} />
    <Route path={ROUTES.system.roles} element={<RoleListPage />} />
    <Route path={ROUTES.system.permissions} element={<PermissionListPage />} />
    <Route path={ROUTES.system.menus} element={<MenuTreePage />} />

    <Route path={ROUTES.apps.sites} element={<SitesPage />} />
    <Route path={ROUTES.apps.siteCreate} element={<SiteCreatePage />} />
    <Route path={ROUTES.apps.siteDetailPattern} element={<SiteWorkspaceLayout />}>
      <Route index element={<Navigate to="overview" replace />} />
      <Route path="overview" element={<SiteOverviewPage />} />
      <Route path="basic" element={<SiteBasicPage />} />
      <Route path="theme" element={<SiteThemePage />} />
      <Route path="domain" element={<SiteDomainPage />} />
      <Route path="contents" element={<SiteContentsPage />} />
      <Route path="ai" element={<SiteAiPage />} />
      <Route path="files" element={<SiteFilesPage />} />
    </Route>
    <Route path={ROUTES.apps.style} element={<StyleSettingsPage />} />

    <Route path={ROUTES.operation.contents} element={<OperationContentsPage />} />
    <Route path={ROUTES.operation.files} element={<OperationFilesPage />} />

    <Route path={ROUTES.ai.settings} element={<AiSettingsPage />} />
    <Route path={ROUTES.ai.prompts} element={<AiPromptsPage />} />
    <Route path={ROUTES.ai.knowledgeBases} element={<AiKnowledgeBasesPage />} />
    <Route path={ROUTES.ai.conversations} element={<AiConversationsPage />} />

    <Route path={ROUTES.config.params} element={<ParamsConfigPage />} />
    <Route path={ROUTES.governance.analytics} element={<AnalyticsPage />} />
    {/* /team 为旧「人员管理」并行面，统一到系统用户管理 */}
    <Route path={ROUTES.governance.team} element={<Navigate to={ROUTES.system.users} replace />} />
    <Route path={ROUTES.governance.system} element={<SystemSettingsPage />} />
    <Route path={ROUTES.governance.developer} element={<DeveloperPage />} />
  </>
)
