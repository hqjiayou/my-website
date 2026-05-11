import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, NavLink, Outlet, useParams } from 'react-router-dom'

import { siteApi } from '@/api/site-api'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/route-paths'
import type { Site, SiteStatus } from '@/api/site-api'

export interface SiteWorkspaceOutletContext {
  site: Site
}

function statusClassName(status: SiteStatus) {
  switch (status) {
    case 'active':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'inactive':
      return 'border-rose-200 bg-rose-50 text-rose-700'
    default:
      return 'border-amber-200 bg-amber-50 text-amber-700'
  }
}

export function SiteWorkspaceLayout() {
  const { t } = useTranslation('sites')
  const { siteId } = useParams()
  const { data: site, isPending } = useQuery({
    queryKey: ['sites', siteId],
    queryFn: () => siteApi.getSite(siteId!),
    enabled: Boolean(siteId),
  })

  if (isPending) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
        {t('workspace.loading')}
      </section>
    )
  }

  if (!site) {
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

  return (
    <section className="space-y-4">
      <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">{site.name}</h1>
            <p className="mt-1 text-sm text-slate-500">{t('workspace.title')}</p>
          </div>
          <Link
            to={ROUTES.apps.sites}
            className="rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
          >
            {t('workspace.backToList')}
          </Link>
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-4">
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('detail.labels.industry')}</p>
            <p className="mt-1 text-sm font-medium text-slate-900">{t(`industry.${site.industry}`)}</p>
          </div>
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('detail.labels.template')}</p>
            <p className="mt-1 text-sm font-medium text-slate-900">{site.templateCode}</p>
          </div>
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('detail.labels.domain')}</p>
            <p className="mt-1 text-sm font-medium text-slate-900">{site.domain}</p>
          </div>
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('detail.labels.status')}</p>
            <span className={cn('mt-1 inline-flex rounded-md border px-2 py-1 text-xs font-medium', statusClassName(site.status))}>
              {t(`status.${site.status}`)}
            </span>
          </div>
        </div>
      </article>

      <nav className="flex flex-wrap gap-2">
        <NavLink
          to={ROUTES.apps.siteOverview(site.id)}
          className={({ isActive }) =>
            cn(
              'rounded-lg border px-3 py-1.5 text-sm font-medium transition',
              isActive ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
            )
          }
        >
          {t('workspace.nav.overview')}
        </NavLink>
        <NavLink
          to={ROUTES.apps.siteBasic(site.id)}
          className={({ isActive }) =>
            cn(
              'rounded-lg border px-3 py-1.5 text-sm font-medium transition',
              isActive ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
            )
          }
        >
          {t('workspace.nav.basic')}
        </NavLink>
        <NavLink
          to={ROUTES.apps.siteTheme(site.id)}
          className={({ isActive }) =>
            cn(
              'rounded-lg border px-3 py-1.5 text-sm font-medium transition',
              isActive ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
            )
          }
        >
          {t('workspace.nav.theme')}
        </NavLink>
        <NavLink
          to={ROUTES.apps.siteDomain(site.id)}
          className={({ isActive }) =>
            cn(
              'rounded-lg border px-3 py-1.5 text-sm font-medium transition',
              isActive ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
            )
          }
        >
          {t('workspace.nav.domain')}
        </NavLink>
        <NavLink
          to={ROUTES.apps.siteContents(site.id)}
          className={({ isActive }) =>
            cn(
              'rounded-lg border px-3 py-1.5 text-sm font-medium transition',
              isActive ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
            )
          }
        >
          {t('workspace.nav.contents')}
        </NavLink>
        <NavLink
          to={ROUTES.apps.siteReservations(site.id)}
          className={({ isActive }) =>
            cn(
              'rounded-lg border px-3 py-1.5 text-sm font-medium transition',
              isActive ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
            )
          }
        >
          {t('workspace.nav.reservations')}
        </NavLink>
        <NavLink
          to={ROUTES.apps.siteAi(site.id)}
          className={({ isActive }) =>
            cn(
              'rounded-lg border px-3 py-1.5 text-sm font-medium transition',
              isActive ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
            )
          }
        >
          {t('workspace.nav.ai')}
        </NavLink>
        <NavLink
          to={ROUTES.apps.siteFiles(site.id)}
          className={({ isActive }) =>
            cn(
              'rounded-lg border px-3 py-1.5 text-sm font-medium transition',
              isActive ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
            )
          }
        >
          {t('workspace.nav.files')}
        </NavLink>
      </nav>

      <Outlet context={{ site } satisfies SiteWorkspaceOutletContext} />
    </section>
  )
}
