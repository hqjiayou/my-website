import { Navigate, Route, Routes } from 'react-router-dom'

import { AdminLayout } from '@/layouts'
import { AiConversationsPage } from '@/pages/ai/conversations-page'
import { AiKnowledgeBasesPage } from '@/pages/ai/knowledge-bases-page'
import { AiPromptsPage } from '@/pages/ai/prompts-page'
import { AiSettingsPage } from '@/pages/ai/settings-page'
import { AnalyticsPage } from '@/pages/analytics/AnalyticsPage'
import { SiteAiPage } from '@/pages/apps/sites/site-ai-page'
import { SiteBasicPage } from '@/pages/apps/sites/site-basic-page'
import { SiteContentsPage } from '@/pages/apps/sites/site-contents-page'
import { SiteCreatePage } from '@/pages/apps/sites/site-create-page'
import { SiteDomainPage } from '@/pages/apps/sites/site-domain-page'
import { SiteFilesPage } from '@/pages/apps/sites/site-files-page'
import { SiteOverviewPage } from '@/pages/apps/sites/site-overview-page'
import { SiteReservationsPage } from '@/pages/apps/sites/site-reservations-page'
import { SitesPage } from '@/pages/apps/sites/sites-page'
import { SiteThemePage } from '@/pages/apps/sites/site-theme-page'
import { SiteWorkspaceLayout } from '@/pages/apps/sites/site-workspace-layout'
import { TemplateDetailPage } from '@/pages/apps/templates/template-detail-page.tsx'
import { TemplatesPage } from '@/pages/apps/templates/templates-page'
import { StyleSettingsPage } from '@/pages/apps/StyleSettingsPage'
import { ParamsConfigPage } from '@/pages/config/ParamsConfigPage'
import { DashboardPage } from '@/pages/dashboard/DashboardPage'
import { DeveloperPage } from '@/pages/developer/developer-page'
import { OperationContentsPage } from '@/pages/operation/contents-page'
import { OperationFilesPage } from '@/pages/operation/files-page'
import { OperationReservationsPage } from '@/pages/operation/reservations-page'
import { SystemSettingsPage } from '@/pages/system/SystemSettingsPage'
import { TeamPage } from '@/pages/team/TeamPage'

import { ROUTES } from './route-paths'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<Navigate to={ROUTES.dashboard} replace />} />
        <Route path={ROUTES.dashboard} element={<DashboardPage />} handle={{ titleKey: 'items.dashboard' }} />

        <Route path={ROUTES.apps.sites} element={<SitesPage />} handle={{ titleKey: 'items.sites' }} />
        <Route path={ROUTES.apps.siteCreate} element={<SiteCreatePage />} handle={{ titleKey: 'items.siteCreate' }} />
        <Route path={ROUTES.apps.siteDetailPattern} element={<SiteWorkspaceLayout />}>
          <Route index element={<Navigate to="overview" replace />} />
          <Route path="overview" element={<SiteOverviewPage />} handle={{ titleKey: 'items.siteOverview' }} />
          <Route path="basic" element={<SiteBasicPage />} handle={{ titleKey: 'items.siteBasic' }} />
          <Route path="theme" element={<SiteThemePage />} handle={{ titleKey: 'items.siteTheme' }} />
          <Route path="domain" element={<SiteDomainPage />} handle={{ titleKey: 'items.siteDomain' }} />
          <Route path="contents" element={<SiteContentsPage />} handle={{ titleKey: 'items.siteContents' }} />
          <Route path="reservations" element={<SiteReservationsPage />} handle={{ titleKey: 'items.siteReservations' }} />
          <Route path="ai" element={<SiteAiPage />} handle={{ titleKey: 'items.siteAi' }} />
          <Route path="files" element={<SiteFilesPage />} handle={{ titleKey: 'items.siteFiles' }} />
        </Route>

        <Route path={ROUTES.apps.templates} element={<TemplatesPage />} handle={{ titleKey: 'items.templates' }} />
        <Route
          path={ROUTES.apps.templateDetail(':templateId')}
          element={<TemplateDetailPage />}
          handle={{ titleKey: 'items.templateDetail' }}
        />
        <Route path={ROUTES.apps.style} element={<StyleSettingsPage />} handle={{ titleKey: 'items.styleSettings' }} />

        <Route path={ROUTES.operation.contents} element={<OperationContentsPage />} handle={{ titleKey: 'items.contents' }} />
        <Route
          path={ROUTES.operation.reservations}
          element={<OperationReservationsPage />}
          handle={{ titleKey: 'items.reservations' }}
        />
        <Route path={ROUTES.operation.files} element={<OperationFilesPage />} handle={{ titleKey: 'items.files' }} />

        <Route path={ROUTES.ai.settings} element={<AiSettingsPage />} handle={{ titleKey: 'items.aiSettings' }} />
        <Route path={ROUTES.ai.prompts} element={<AiPromptsPage />} handle={{ titleKey: 'items.aiPrompts' }} />
        <Route
          path={ROUTES.ai.knowledgeBases}
          element={<AiKnowledgeBasesPage />}
          handle={{ titleKey: 'items.aiKnowledgeBases' }}
        />
        <Route
          path={ROUTES.ai.conversations}
          element={<AiConversationsPage />}
          handle={{ titleKey: 'items.aiConversations' }}
        />

        <Route path={ROUTES.config.params} element={<ParamsConfigPage />} handle={{ titleKey: 'items.paramsConfig' }} />
        <Route path={ROUTES.governance.analytics} element={<AnalyticsPage />} handle={{ titleKey: 'items.analytics' }} />
        <Route path={ROUTES.governance.team} element={<TeamPage />} handle={{ titleKey: 'items.team' }} />
        <Route path={ROUTES.governance.system} element={<SystemSettingsPage />} handle={{ titleKey: 'items.system' }} />
        <Route
          path={ROUTES.governance.developer}
          element={<DeveloperPage />}
          handle={{ titleKey: 'items.developer' }}
        />
      </Route>

      <Route path={ROUTES.legacy.sites} element={<Navigate to={ROUTES.apps.sites} replace />} />
      <Route path={ROUTES.legacy.templates} element={<Navigate to={ROUTES.apps.templates} replace />} />
      <Route path={ROUTES.legacy.reservations} element={<Navigate to={ROUTES.operation.reservations} replace />} />
      <Route path={ROUTES.legacy.ai} element={<Navigate to={ROUTES.ai.settings} replace />} />
      <Route path={ROUTES.legacy.settings} element={<Navigate to={ROUTES.governance.system} replace />} />

      <Route path="*" element={<Navigate to={ROUTES.dashboard} replace />} />
    </Routes>
  )
}
