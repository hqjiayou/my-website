import { useQuery } from '@tanstack/react-query'
import {
  Activity,
  BookOpen,
  Bot,
  Braces,
  ExternalLink,
  FileText,
  FolderOpen,
  Globe,
  MessageSquare,
  MessagesSquare,
  Palette,
  SlidersHorizontal,
  type LucideIcon,
} from 'lucide-react'
import { Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink, Outlet, useParams } from 'react-router-dom'

import { siteApi, SITE_STATUS, type Site, type SiteStatus } from '@/api'
import { RouteFallback } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'
import { siteWebUrl } from '@/utils/siteWebUrl'

export interface SiteWorkspaceOutletContext {
  site: Site
}

function statusClassName(status: string) {
  return status === SITE_STATUS.ACTIVE
    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
    : 'border-slate-200 bg-slate-50 text-slate-600'
}

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

export function SiteWorkspaceLayout() {
  const { t } = useTranslation('sites')
  const { siteId } = useParams()
  const parsedId = Number(siteId)
  const canListContent = useHasPermission(PERMISSION_CODES.contentList)
  const canListMedia = useHasPermission(PERMISSION_CODES.mediaList)
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const canKnowledge = useHasPermission(PERMISSION_CODES.aiKbList)
  const canConversations = useHasPermission(PERMISSION_CODES.aiConversationList)
  const canAi = canPrompt || canKnowledge || canConversations

  const { data: site, isPending, isError, error } = useQuery({
    queryKey: ['sites', parsedId],
    queryFn: () => siteApi.getSite(parsedId),
    enabled: Number.isFinite(parsedId),
  })

  const navGroups = useMemo<WorkspaceNavGroup[]>(() => {
    if (!site) return []
    const groups: WorkspaceNavGroup[] = []

    if (canAi) {
      const agentItems: WorkspaceNavItem[] = [
        { key: 'agentOverview', path: ROUTES.apps.siteAgent(site.id), icon: Bot, end: true },
      ]
      if (canPrompt) {
        agentItems.push(
          { key: 'agentPreview', path: ROUTES.apps.siteAgentPreview(site.id), icon: MessageSquare },
          { key: 'agentPrompt', path: ROUTES.apps.siteAgentPrompt(site.id), icon: Braces },
        )
      }
      if (canKnowledge) {
        agentItems.push({ key: 'agentKnowledge', path: ROUTES.apps.siteAgentKnowledge(site.id), icon: BookOpen })
      }
      if (canConversations) {
        agentItems.push({
          key: 'agentConversations',
          path: ROUTES.apps.siteAgentConversations(site.id),
          icon: MessagesSquare,
        })
      }
      if (canPrompt) {
        agentItems.push({ key: 'agentRuntime', path: ROUTES.apps.siteAgentRuntime(site.id), icon: Activity })
      }
      groups.push({ key: 'agent', items: agentItems })
    }

    const materialItems: WorkspaceNavItem[] = []
    if (canListContent) {
      materialItems.push({ key: 'contents', path: ROUTES.apps.siteContents(site.id), icon: FileText })
    }
    if (canListMedia) {
      materialItems.push({ key: 'files', path: ROUTES.apps.siteFiles(site.id), icon: FolderOpen })
    }
    if (materialItems.length > 0) {
      groups.push({ key: 'materials', items: materialItems })
    }

    groups.push({
      key: 'settings',
      items: [
        { key: 'basic', path: ROUTES.apps.siteBasic(site.id), icon: SlidersHorizontal },
        { key: 'theme', path: ROUTES.apps.siteTheme(site.id), icon: Palette },
        { key: 'domain', path: ROUTES.apps.siteDomain(site.id), icon: Globe },
      ],
    })
    return groups
  }, [canAi, canConversations, canKnowledge, canListContent, canListMedia, canPrompt, site])

  if (!Number.isFinite(parsedId)) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">{t('workspace.notFound.title')}</h1>
        <p className="mt-2 text-sm text-slate-600">{t('workspace.notFound.description')}</p>
        <Link
          to={ROUTES.apps.sites}
          className="mt-4 inline-flex rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
        >
          {t('workspace.backToList')}
        </Link>
      </section>
    )
  }

  if (isPending) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
        {t('workspace.loading')}
      </section>
    )
  }

  if (isError || !site) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">{t('workspace.notFound.title')}</h1>
        <p className="mt-2 text-sm text-slate-600">
          {error instanceof Error ? error.message : t('workspace.notFound.description')}
        </p>
        <Link
          to={ROUTES.apps.sites}
          className="mt-4 inline-flex rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
        >
          {t('workspace.backToList')}
        </Link>
      </section>
    )
  }

  const visitUrl = siteWebUrl(site.siteCode)

  return (
    <section className="space-y-4">
      <article className="rounded-2xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
            <h1 className="text-xl font-semibold text-slate-900">{site.siteName}</h1>
            <span
              className={cn(
                'inline-flex rounded-md border px-2 py-0.5 text-xs font-medium',
                statusClassName(site.status as SiteStatus),
              )}
            >
              {t(`status.${site.status}`, { defaultValue: site.status })}
            </span>
            <p className="text-sm text-slate-500">
              {site.siteCode} · {t(`siteType.${site.siteType}`, { defaultValue: site.siteType })}
              {site.defaultDomain ? ` · ${site.defaultDomain}` : ''}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            {visitUrl ? (
              <a
                href={visitUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1.5 rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-medium text-white transition hover:bg-slate-800"
              >
                <ExternalLink className="h-4 w-4" />
                {t('workspace.visitSite')}
              </a>
            ) : null}
            <Link
              to={ROUTES.apps.sites}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
            >
              {t('workspace.backToList')}
            </Link>
          </div>
        </div>
      </article>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
        <aside className="rounded-2xl border border-slate-200 bg-white p-3 shadow-sm lg:sticky lg:top-4 lg:w-60 lg:shrink-0">
          {navGroups.map((group) => (
            <div key={group.key} className="mb-4 last:mb-0">
              <p className="px-2 pb-1.5 text-xs font-semibold tracking-wide text-slate-400">
                {t(`workspace.groups.${group.key}`)}
              </p>
              <nav className="flex flex-wrap gap-1 lg:flex-col lg:flex-nowrap">
                {group.items.map((item) => (
                  <NavLink
                    key={item.key}
                    to={item.path}
                    end={item.end}
                    className={({ isActive }) =>
                      cn(
                        'inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition',
                        isActive ? 'bg-slate-900 text-white' : 'text-slate-700 hover:bg-slate-50',
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

        <div className="min-w-0 flex-1">
          <Suspense fallback={<RouteFallback />}>
            <Outlet context={{ site } satisfies SiteWorkspaceOutletContext} />
          </Suspense>
        </div>
      </div>
    </section>
  )
}
