import { Suspense, useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, NavLink, Outlet, useLocation, useOutletContext, useParams } from 'react-router-dom'

import { RouteFallback } from '@/components/page'
import { cn } from '@/lib/cn'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import { buildAgentConfigNavItems, firstAgentConfigPath } from '../agentConfigNav'
import type { AgentWorkspaceOutletContext } from '../agentWorkspaceLayout'

const subNavClass =
  'admin-focus-ring flex w-full items-center gap-2 rounded-lg px-3 py-2.5 text-left text-xs font-semibold text-admin-muted transition hover:bg-admin-surface-muted hover:text-admin'

/**
 * 配置区壳：按**内容容器宽**分栏（约 56rem），不足时横向滚动 tabs。
 * 避免仅凭 viewport `lg` 在 1024–1279 + 全局侧栏时挤扁表单。
 */
export function AgentConfigLayout() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const { agentId } = useParams()
  const location = useLocation()
  const navRef = useRef<HTMLElement>(null)
  const id = site?.id ?? Number(agentId)
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const canKnowledge = useHasPermission(PERMISSION_CODES.aiKbList)
  const canModel = useHasPermission(PERMISSION_CODES.aiModelRead)
  const items = buildAgentConfigNavItems(id, { canModel, canPrompt, canKnowledge })

  useEffect(() => {
    const active = navRef.current?.querySelector<HTMLElement>('[aria-current="page"]')
    active?.scrollIntoView({ inline: 'nearest', block: 'nearest' })
  }, [location.pathname])

  if (items.length === 0) {
    return <p className="text-sm text-admin-muted">{t('aiWorkspace.noPermission')}</p>
  }

  return (
    <div className="@container/agent-config">
      <div className="grid items-start gap-4 @[56rem]:grid-cols-[13rem_minmax(0,1fr)]">
        <aside className="rounded-xl border border-admin bg-admin-surface p-2 @[56rem]:sticky @[56rem]:top-[calc(4rem+var(--admin-page-padding))]">
          <nav
            ref={navRef}
            className="flex gap-1 overflow-x-auto @[56rem]:flex-col @[56rem]:overflow-visible"
            aria-label={t('workspace.tabs.config')}
          >
            {items.map((item) => (
              <NavLink
                key={item.key}
                to={item.to}
                className={({ isActive }) =>
                  cn(
                    subNavClass,
                    'shrink-0 whitespace-nowrap @[56rem]:w-full',
                    isActive && 'bg-primary/12 text-primary',
                  )
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
    </div>
  )
}

/** `/config` 根路径：按权限落到第一个可访问子页，不固定 models。 */
export function RedirectToAgentConfigIndex() {
  const { agentId } = useParams()
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const canKnowledge = useHasPermission(PERMISSION_CODES.aiKbList)
  const canModel = useHasPermission(PERMISSION_CODES.aiModelRead)
  if (agentId == null || agentId === '') return null
  const target = firstAgentConfigPath(agentId, { canModel, canPrompt, canKnowledge })
  if (!target) return null
  return <Navigate to={target} replace />
}
