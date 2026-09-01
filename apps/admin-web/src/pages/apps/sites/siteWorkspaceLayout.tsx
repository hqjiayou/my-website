import { useQuery } from '@tanstack/react-query'
import { Suspense, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink, Outlet, useParams } from 'react-router-dom'

import { siteApi, SITE_STATUS, type Site, type SiteStatus } from '@/api'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'
import { useHasPermission } from '@/router/permission'
import { RouteFallback } from '@/components/page'
import { PERMISSION_CODES } from '@/types/rbac'

export interface SiteWorkspaceOutletContext {
  site: Site
}

function statusClassName(status: string) {
  return status === SITE_STATUS.ACTIVE
    ? 'border-emerald-200 bg-emerald-50 text-emerald-700'
    : 'border-slate-200 bg-slate-50 text-slate-600'
}

export function SiteWorkspaceLayout() {
  const { t } = useTranslation('sites')
  const { siteId } = useParams()
  const parsedId = Number(siteId)
  const canListContent = useHasPermission(PERMISSION_CODES.contentList)
  const canListReservation = useHasPermission(PERMISSION_CODES.reservationList)
  const canListMedia = useHasPermission(PERMISSION_CODES.mediaList)
  const canAi =
    useHasPermission(PERMISSION_CODES.aiPromptRead) ||
    useHasPermission(PERMISSION_CODES.aiKbList) ||
    useHasPermission(PERMISSION_CODES.aiConversationList)

  const { data: site, isPending, isError, error } = useQuery({
    queryKey: ['sites', parsedId],
    queryFn: () => siteApi.getSite(parsedId),
    enabled: Number.isFinite(parsedId),
  })

  const navItems = useMemo(() => {
    if (!site) return [] as Array<{ key: string; path: string }>
    const items: Array<{ key: string; path: string }> = [
      { key: 'overview', path: ROUTES.apps.siteOverview(site.id) },
      { key: 'basic', path: ROUTES.apps.siteBasic(site.id) },
      { key: 'theme', path: ROUTES.apps.siteTheme(site.id) },
      { key: 'domain', path: ROUTES.apps.siteDomain(site.id) },
    ]
    if (canListContent) items.push({ key: 'contents', path: ROUTES.apps.siteContents(site.id) })
    if (canListReservation) items.push({ key: 'reservations', path: ROUTES.apps.siteReservations(site.id) })
    if (canAi) items.push({ key: 'ai', path: ROUTES.apps.siteAi(site.id) })
    if (canListMedia) items.push({ key: 'files', path: ROUTES.apps.siteFiles(site.id) })
    return items
  }, [canAi, canListContent, canListMedia, canListReservation, site])

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

  return (
    <section className="space-y-4">
      <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">{site.siteName}</h1>
            <p className="mt-1 text-sm text-slate-500">
              {t('workspace.title')} · {site.siteCode}
            </p>
          </div>
          <Link
            to={ROUTES.apps.sites}
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
          >
            {t('workspace.backToList')}
          </Link>
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('detail.labels.siteType')}</p>
            <p className="mt-1 text-sm font-medium text-slate-900">
              {t(`siteType.${site.siteType}`, { defaultValue: site.siteType })}
            </p>
          </div>
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('detail.labels.domain')}</p>
            <p className="mt-1 text-sm font-medium text-slate-900">{site.defaultDomain || '—'}</p>
          </div>
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('detail.labels.status')}</p>
            <span
              className={cn(
                'mt-1 inline-flex rounded-md border px-2 py-1 text-xs font-medium',
                statusClassName(site.status as SiteStatus),
              )}
            >
              {t(`status.${site.status}`, { defaultValue: site.status })}
            </span>
          </div>
        </div>
      </article>

      <nav className="flex flex-wrap gap-2">
        {navItems.map(({ key, path }) => (
          <NavLink
            key={key}
            to={path}
            className={({ isActive }) =>
              cn(
                'rounded-lg border px-3 py-1.5 text-sm font-medium transition',
                isActive
                  ? 'border-slate-900 bg-slate-900 text-white'
                  : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
              )
            }
          >
            {t(`workspace.nav.${key}`)}
          </NavLink>
        ))}
      </nav>

      <Suspense fallback={<RouteFallback />}>
        <Outlet context={{ site } satisfies SiteWorkspaceOutletContext} />
      </Suspense>
    </section>
  )
}
