import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Activity,
  BookOpen,
  Bot,
  Braces,
  MessageSquare,
  MessagesSquare,
  Plus,
  Plug,
  SlidersHorizontal,
  type LucideIcon,
} from 'lucide-react'
import { Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { agentApi, SITE_STATUS, type Site } from '@/api'
import { StatusTag } from '@/components/admin'
import { RouteFallback } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

export interface AgentWorkspaceOutletContext {
  site: Site
}

const headerControlClass =
  'inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border px-3 text-sm font-medium leading-none transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[rgb(var(--primary)/0.35)] disabled:opacity-60'

interface WorkspaceNavItem {
  key: string
  path: string
  icon: LucideIcon
  end?: boolean
}

interface WorkspaceNavGroup {
  key: string
  items: WorkspaceNavItem[]
}

export function AgentWorkspaceLayout() {
  const { t } = useTranslation('sites')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { agentId } = useParams()
  const parsedId = Number(agentId)
  const canList = useHasPermission(PERMISSION_CODES.agentList)
  const canStatus = useHasPermission(PERMISSION_CODES.agentStatus)
  const canDelete = useHasPermission(PERMISSION_CODES.agentDelete)
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const canKnowledge = useHasPermission(PERMISSION_CODES.aiKbList)
  const canConversations = useHasPermission(PERMISSION_CODES.aiConversationList)
  const canAi = canPrompt || canKnowledge || canConversations

  const { data: site, isPending, isError, error } = useQuery({
    queryKey: ['sites', parsedId],
    queryFn: () => agentApi.getSite(parsedId),
    enabled: Number.isFinite(parsedId),
  })
  const agentsQuery = useQuery({
    queryKey: ['sites', { pageNum: 1, pageSize: 50 }],
    queryFn: () => agentApi.listSites({ pageNum: 1, pageSize: 50 }),
    enabled: canList,
  })
  const agents = agentsQuery.data?.records ?? []

  const statusMutation = useMutation({
    mutationFn: () => {
      if (!site) return Promise.reject(new Error(t('workspace.notFound.title')))
      return agentApi.updateAgentStatus(site.id, {
        status: site.status === SITE_STATUS.ACTIVE ? SITE_STATUS.DISABLED : SITE_STATUS.ACTIVE,
        version: site.version,
      })
    },
    onSuccess: () => {
      toast.success(t('list.messages.statusUpdated'))
      void queryClient.invalidateQueries({ queryKey: ['sites'] })
    },
    onError: (err: Error) => {
      toast.error(err.message)
      void queryClient.invalidateQueries({ queryKey: ['sites'] })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => {
      if (!site) return Promise.reject(new Error(t('workspace.notFound.title')))
      return agentApi.deleteSite(site.id)
    },
    onSuccess: () => {
      toast.success(t('list.messages.deleted'))
      void queryClient.invalidateQueries({ queryKey: ['sites'] })
      navigate(ROUTES.apps.sites)
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const navGroups = useMemo<WorkspaceNavGroup[]>(() => {
    if (!site) return []
    const groups: WorkspaceNavGroup[] = []

    if (canAi) {
      const agentItems: WorkspaceNavItem[] = [
        { key: 'agentOverview', path: ROUTES.apps.agent(site.id), icon: Bot, end: true },
      ]
      if (canPrompt) {
        agentItems.push(
          { key: 'agentPreview', path: ROUTES.apps.agentPreview(site.id), icon: MessageSquare },
          { key: 'agentPrompt', path: ROUTES.apps.agentPrompt(site.id), icon: Braces },
        )
      }
      if (canKnowledge) {
        agentItems.push({ key: 'agentKnowledge', path: ROUTES.apps.agentKnowledge(site.id), icon: BookOpen })
      }
      if (canConversations) {
        agentItems.push({
          key: 'agentConversations',
          path: ROUTES.apps.agentConversations(site.id),
          icon: MessagesSquare,
        })
      }
      if (canPrompt) {
        agentItems.push({ key: 'agentRuntime', path: ROUTES.apps.agentRuntime(site.id), icon: Activity })
      }
      groups.push({ key: 'agent', items: agentItems })
    }

    groups.push({
      key: 'settings',
      items: [
        { key: 'access', path: ROUTES.apps.agentAccess(site.id), icon: Plug },
        { key: 'basic', path: ROUTES.apps.agentBasic(site.id), icon: SlidersHorizontal },
      ],
    })
    return groups
  }, [canAi, canConversations, canKnowledge, canPrompt, site])

  if (!Number.isFinite(parsedId) || isError || (!isPending && !site)) {
    return (
      <p className="text-sm text-rose-700">
        {error instanceof Error ? error.message : t('workspace.notFound.title')}
      </p>
    )
  }

  if (isPending || !site) {
    return <p className="text-sm text-admin-muted">{t('workspace.loading')}</p>
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold text-admin">{site.agentName}</h1>
            <StatusTag status={site.status} label={t(`status.${site.status}`, { defaultValue: site.status })} />
          </div>
          <p className="mt-1 text-sm text-admin-muted">
            {site.agentCode}
            <span className="mx-1.5">·</span>
            {t(`agentType.${site.agentType}`, { defaultValue: site.agentType })}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {agents.length > 1 ? (
            <select
              className={cn(headerControlClass, 'w-44 border-admin bg-admin-surface text-admin')}
              value={site.id}
              onChange={(event) => navigate(ROUTES.apps.agent(event.target.value))}
            >
              {agents.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.agentName}
                </option>
              ))}
            </select>
          ) : null}
          {canStatus ? (
            <button
              type="button"
              className={cn(headerControlClass, 'border-admin bg-admin-surface text-admin hover:bg-admin-surface-muted')}
              disabled={statusMutation.isPending}
              onClick={() => statusMutation.mutate()}
            >
              {site.status === SITE_STATUS.ACTIVE ? t('list.actions.disable') : t('list.actions.enable')}
            </button>
          ) : null}
          {canDelete ? (
            <button
              type="button"
              className={cn(headerControlClass, 'border-rose-300 bg-admin-surface text-rose-700 hover:bg-rose-50')}
              disabled={deleteMutation.isPending}
              onClick={() => {
                if (!window.confirm(t('list.messages.confirmDelete', { name: site.agentName }))) return
                deleteMutation.mutate()
              }}
            >
              {t('list.actions.delete')}
            </button>
          ) : null}
          <PermissionGate code={PERMISSION_CODES.agentCreate}>
            <button
              type="button"
              className={cn(headerControlClass, 'border-admin bg-admin-surface text-admin hover:bg-admin-surface-muted')}
              onClick={() => navigate(ROUTES.apps.agentCreate)}
            >
              <Plus className="h-4 w-4" />
              {t('list.createSite')}
            </button>
          </PermissionGate>
        </div>
      </div>

      <div className="grid items-start gap-4 lg:grid-cols-[15rem_minmax(0,1fr)]">
        <aside className="rounded-xl border border-admin bg-admin-surface p-2">
          {navGroups.map((group) => (
            <div key={group.key} className="mb-3 last:mb-0">
              <p className="px-2.5 pb-1 text-xs font-medium text-admin-muted">{t(`workspace.groups.${group.key}`)}</p>
              <nav className="flex flex-col gap-0.5">
                {group.items.map((item) => (
                  <NavLink
                    key={item.key}
                    to={item.path}
                    end={item.end}
                    className={({ isActive }) =>
                      cn(
                        'inline-flex items-center gap-2 rounded-lg px-2.5 py-2 text-sm font-medium text-admin transition hover:bg-admin-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[rgb(var(--primary)/0.35)]',
                        isActive && 'bg-[rgb(var(--primary)/0.08)] shadow-[inset_2px_0_0_rgb(var(--primary))]',
                      )
                    }
                  >
                    <item.icon className="h-4 w-4 shrink-0" />
                    {t(`workspace.nav.${item.key}`)}
                  </NavLink>
                ))}
              </nav>
            </div>
          ))}
        </aside>

        <div className="min-w-0">
          <Suspense fallback={<RouteFallback />}>
            <Outlet context={{ site } satisfies AgentWorkspaceOutletContext} />
          </Suspense>
        </div>
      </div>
    </section>
  )
}
