import { BookOpen, Braces, Cpu, Plug } from 'lucide-react'
import { Suspense } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useOutletContext, useParams } from 'react-router-dom'

import { RouteFallback } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import type { AgentWorkspaceOutletContext } from '../agentWorkspaceLayout'

const subNavClass =
  'admin-focus-ring flex w-full items-center gap-2 rounded-lg px-3 py-2.5 text-left text-xs font-semibold text-admin-muted transition hover:bg-admin-surface-muted hover:text-admin'

/**
 * 配置区壳：紧凑二级导航（约 184–208px）+ 右侧业务 Outlet。
 * PR-2 只搭骨架；业务面板由子路由薄容器挂载现有组件。
 */
export function AgentConfigLayout() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const { agentId } = useParams()
  const id = site?.id ?? Number(agentId)
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const canKnowledge = useHasPermission(PERMISSION_CODES.aiKbList)
  const canModel = useHasPermission(PERMISSION_CODES.aiModelRead)

  const items = [
    canModel || canPrompt
      ? { key: 'models', to: ROUTES.apps.agentConfigModels(id), icon: Cpu, labelKey: 'workspace.configNav.models' }
      : null,
    canPrompt
      ? { key: 'prompt', to: ROUTES.apps.agentConfigPrompt(id), icon: Braces, labelKey: 'workspace.configNav.prompt' }
      : null,
    canKnowledge
      ? {
          key: 'knowledge',
          to: ROUTES.apps.agentConfigKnowledge(id),
          icon: BookOpen,
          labelKey: 'workspace.configNav.knowledge',
        }
      : null,
    canPrompt
      ? { key: 'tools', to: ROUTES.apps.agentConfigTools(id), icon: Plug, labelKey: 'workspace.configNav.tools' }
      : null,
  ].filter(Boolean) as Array<{ key: string; to: string; icon: typeof Cpu; labelKey: string }>

  if (items.length === 0) {
    return <p className="text-sm text-admin-muted">{t('aiWorkspace.noPermission')}</p>
  }

  return (
    <div className="grid items-start gap-4 md:grid-cols-[12.5rem_minmax(0,1fr)] xl:grid-cols-[13rem_minmax(0,1fr)]">
      <aside className="rounded-xl border border-admin bg-admin-surface p-2 md:sticky md:top-[calc(4rem+var(--admin-page-padding))]">
        <nav className="flex gap-1 overflow-x-auto md:flex-col md:overflow-visible" aria-label={t('workspace.tabs.config')}>
          {items.map((item) => (
            <NavLink
              key={item.key}
              to={item.to}
              className={({ isActive }) =>
                cn(subNavClass, 'whitespace-nowrap md:w-full', isActive && 'bg-primary/12 text-primary')
              }
            >
              <item.icon className="h-4 w-4 shrink-0" aria-hidden />
              {t(item.labelKey)}
            </NavLink>
          ))}
        </nav>
      </aside>
      <div className="min-w-0">
        <Suspense fallback={<RouteFallback />}>
          <Outlet context={{ site } satisfies AgentWorkspaceOutletContext} />
        </Suspense>
      </div>
    </div>
  )
}
