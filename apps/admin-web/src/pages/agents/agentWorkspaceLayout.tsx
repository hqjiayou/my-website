import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bot, MoreHorizontal, Plus } from 'lucide-react'
import { Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { agentApi, SITE_STATUS, type Site } from '@/api'
import { FormDialog, StatusTag } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { RouteFallback } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import { firstAgentConfigPath } from './agentConfigNav'
import { AgentCreateDialog } from './agentCreateDialog'

export interface AgentWorkspaceOutletContext {
  site: Site
}

const headerControlClass =
  'inline-flex h-9 items-center justify-center gap-1.5 rounded-lg border px-3 text-sm font-medium leading-none transition admin-focus-ring disabled:opacity-60'

function formatUpdatedAt(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(undefined, { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

type WorkspaceTabKey = 'overview' | 'config' | 'debug' | 'runs' | 'settings'

function resolveActiveTab(pathname: string): WorkspaceTabKey {
  if (/\/agent\/[^/]+\/agent\/config(?:\/|$)/.test(pathname)) return 'config'
  if (/\/agent\/[^/]+\/agent\/debug(?:\/|$)/.test(pathname)) return 'debug'
  if (/\/agent\/[^/]+\/agent\/runs(?:\/|$)/.test(pathname)) return 'runs'
  if (/\/agent\/[^/]+\/agent\/settings(?:\/|$)/.test(pathname)) return 'settings'
  if (/\/agent\/[^/]+\/(?:basic|access|theme|contents|files)(?:\/|$)/.test(pathname)) return 'settings'
  return 'overview'
}

export function AgentWorkspaceLayout() {
  const { t } = useTranslation('sites')
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const { agentId } = useParams()
  const parsedId = Number(agentId)
  const canList = useHasPermission(PERMISSION_CODES.agentList)
  const canStatus = useHasPermission(PERMISSION_CODES.agentStatus)
  const canDelete = useHasPermission(PERMISSION_CODES.agentDelete)
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const canKnowledge = useHasPermission(PERMISSION_CODES.aiKbList)
  const canConversations = useHasPermission(PERMISSION_CODES.aiConversationList)
  const canModel = useHasPermission(PERMISSION_CODES.aiModelRead)
  const canConfig = canPrompt || canKnowledge || canModel
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
  const activeTab = resolveActiveTab(location.pathname)

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
      setMenuOpen(false)
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

  const tabs = useMemo(() => {
    if (!site) return []
    const configTo =
      firstAgentConfigPath(site.id, { canModel, canPrompt, canKnowledge }) ??
      ROUTES.apps.agentConfigModels(site.id)
    const items: Array<{ key: WorkspaceTabKey; to: string; end?: boolean; visible: boolean }> = [
      { key: 'overview', to: ROUTES.apps.agent(site.id), end: true, visible: canAi },
      { key: 'config', to: configTo, visible: canConfig },
      { key: 'debug', to: ROUTES.apps.agentDebug(site.id), visible: canPrompt },
      { key: 'runs', to: ROUTES.apps.agentRuns(site.id), visible: canConversations },
      { key: 'settings', to: ROUTES.apps.agentSettings(site.id), visible: true },
    ]
    return items.filter((item) => item.visible)
  }, [canAi, canConfig, canConversations, canKnowledge, canModel, canPrompt, site])

  useEffect(() => {
    if (!menuOpen) return
    function onPointerDown(event: MouseEvent) {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false)
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [menuOpen])

  if (!Number.isFinite(parsedId) || isError || (!isPending && !site)) {
    return (
      <p className="text-sm text-danger-emphasis">
        {error instanceof Error ? error.message : t('workspace.notFound.title')}
      </p>
    )
  }

  if (isPending || !site) {
    return <p className="text-sm text-admin-muted">{t('workspace.loading')}</p>
  }

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-admin bg-admin-surface px-4 py-3 sm:px-5">
        <Link
          to={ROUTES.apps.sites}
          className={cn(adminGhostButtonClass, 'shrink-0')}
          aria-label={t('workspace.backToList')}
        >
          ←
        </Link>
        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-[11px] bg-primary/12 text-primary">
          <Bot className="h-5 w-5" aria-hidden />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="truncate text-lg font-semibold text-admin sm:text-xl">{site.agentName}</h1>
            <StatusTag status={site.status} label={t(`status.${site.status}`, { defaultValue: site.status })} />
          </div>
          <p className="mt-0.5 flex flex-wrap items-center text-xs text-admin-muted">
            <span className="font-mono">{site.agentCode}</span>
            <span className="mx-1.5">·</span>
            <span>{t(`agentType.${site.agentType}`, { defaultValue: site.agentType })}</span>
            {site.updatedAt ? (
              <>
                <span className="mx-1.5">·</span>
                <span>
                  {t('list.columns.updatedAt')} {formatUpdatedAt(site.updatedAt)}
                </span>
              </>
            ) : null}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {canPrompt ? (
            <button
              type="button"
              className={adminPrimaryButtonClass}
              onClick={() => navigate(ROUTES.apps.agentDebug(site.id))}
            >
              {t('workspace.actions.debug')}
            </button>
          ) : null}
          <div ref={menuRef} className="relative">
            <button
              type="button"
              className={cn(adminGhostButtonClass, 'h-9 w-9 justify-center px-0')}
              aria-haspopup="menu"
              aria-expanded={menuOpen}
              aria-label={t('workspace.actions.more')}
              onClick={() => setMenuOpen((value) => !value)}
            >
              <MoreHorizontal className="h-4 w-4" aria-hidden />
            </button>
            {menuOpen ? (
              <div
                role="menu"
                className="absolute top-full right-0 z-30 mt-1 min-w-44 rounded-lg border border-admin bg-admin-surface p-1 shadow-admin-pop"
              >
                {agents.length > 1 ? (
                  <div className="border-b border-admin px-2 py-2">
                    <label className="mb-1 block text-[10px] font-semibold tracking-wide text-admin-muted uppercase">
                      {t('workspace.actions.switchAgent')}
                    </label>
                    <select
                      className={cn(headerControlClass, 'w-full border-admin bg-admin-surface text-admin')}
                      value={site.id}
                      onChange={(event) => {
                        navigate(ROUTES.apps.agent(event.target.value))
                        setMenuOpen(false)
                      }}
                    >
                      {agents.map((item) => (
                        <option key={item.id} value={item.id}>
                          {item.agentName}
                        </option>
                      ))}
                    </select>
                  </div>
                ) : null}
                {canStatus ? (
                  <button
                    type="button"
                    role="menuitem"
                    className="flex w-full rounded-md px-2.5 py-1.5 text-left text-xs font-medium text-admin hover:bg-admin-surface-muted"
                    disabled={statusMutation.isPending}
                    onClick={() => statusMutation.mutate()}
                  >
                    {site.status === SITE_STATUS.ACTIVE ? t('list.actions.disable') : t('list.actions.enable')}
                  </button>
                ) : null}
                <PermissionGate code={PERMISSION_CODES.agentCreate}>
                  <button
                    type="button"
                    role="menuitem"
                    className="flex w-full items-center gap-1.5 rounded-md px-2.5 py-1.5 text-left text-xs font-medium text-admin hover:bg-admin-surface-muted"
                    onClick={() => {
                      setMenuOpen(false)
                      setCreateOpen(true)
                    }}
                  >
                    <Plus className="h-3.5 w-3.5" aria-hidden />
                    {t('list.createSite')}
                  </button>
                </PermissionGate>
                <button
                  type="button"
                  role="menuitem"
                  className="flex w-full rounded-md px-2.5 py-1.5 text-left text-xs font-medium text-admin hover:bg-admin-surface-muted"
                  onClick={() => {
                    setMenuOpen(false)
                    navigate(ROUTES.apps.agentSettings(site.id))
                  }}
                >
                  {t('workspace.tabs.settings')}
                </button>
                {canDelete ? (
                  <button
                    type="button"
                    role="menuitem"
                    className="flex w-full rounded-md px-2.5 py-1.5 text-left text-xs font-medium text-admin-danger hover:bg-admin-danger-muted"
                    onClick={() => {
                      setMenuOpen(false)
                      setDeleteOpen(true)
                    }}
                  >
                    {t('list.actions.delete')}
                  </button>
                ) : null}
              </div>
            ) : null}
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-admin bg-admin-surface px-2 sm:px-3">
        <nav className="flex gap-0.5 overflow-x-auto" aria-label={t('workspace.title')}>
          {tabs.map((tab) => (
            <NavLink
              key={tab.key}
              to={tab.to}
              end={tab.end}
              className={cn(
                'admin-focus-ring shrink-0 border-b-2 border-transparent px-3 py-3 text-sm font-medium text-admin-muted transition hover:text-admin',
                activeTab === tab.key && 'border-primary text-primary',
              )}
            >
              {t(`workspace.tabs.${tab.key}`)}
            </NavLink>
          ))}
        </nav>
      </div>

      <div className="min-w-0">
        <Suspense fallback={<RouteFallback />}>
          <Outlet context={{ site } satisfies AgentWorkspaceOutletContext} />
        </Suspense>
      </div>

      {createOpen ? <AgentCreateDialog onClose={() => setCreateOpen(false)} /> : null}
      {deleteOpen ? (
        <FormDialog
          title={t('list.messages.deleteConfirmTitle')}
          closeLabel={t('list.actions.cancel')}
          onClose={() => setDeleteOpen(false)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">{t('list.messages.confirmDelete', { name: site.agentName })}</p>
          <div className={adminFormActionsClass}>
            <button type="button" className={adminSecondaryButtonClass} onClick={() => setDeleteOpen(false)}>
              {t('list.actions.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate()}
            >
              {t('list.actions.delete')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </section>
  )
}
