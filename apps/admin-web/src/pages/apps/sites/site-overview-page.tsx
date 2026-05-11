import { useQuery } from '@tanstack/react-query'
import { CheckCircle2, Circle, Settings } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router-dom'

import { siteApi } from '@/api/site-api'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/route-paths'

import type { SiteWorkspaceOutletContext } from './site-workspace-layout'

function toPercent(value: number) {
  return `${Math.round(value)}%`
}

export function SiteOverviewPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<SiteWorkspaceOutletContext>()
  const { data: runtimeStatus, isPending } = useQuery({
    queryKey: ['sites', site.id, 'runtime-status'],
    queryFn: () => siteApi.getSiteRuntimeStatus(site.id),
  })

  const checklistItems = [
    { key: 'basicInfo', done: runtimeStatus?.publishChecklist.basicInfo ?? false },
    { key: 'theme', done: runtimeStatus?.publishChecklist.theme ?? false },
    { key: 'domain', done: runtimeStatus?.publishChecklist.domain ?? false },
    { key: 'homeContent', done: runtimeStatus?.publishChecklist.homeContent ?? false },
    { key: 'aiConfig', done: runtimeStatus?.publishChecklist.aiConfig ?? false },
  ] as const

  const completedCount = checklistItems.filter((item) => item.done).length
  const publishPercent = (completedCount / checklistItems.length) * 100

  return (
    <section>
      <PageHeader title={t('overview.title')} description={t('overview.description')} />

      <div className="grid gap-3 md:grid-cols-4">
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('overview.stats.todayVisits')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{runtimeStatus?.visitCountToday ?? 0}</p>
        </article>
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('overview.stats.todayReservations')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{runtimeStatus?.reservationCountToday ?? 0}</p>
        </article>
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('overview.stats.aiConsultations')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{runtimeStatus?.aiConversationCountToday ?? 0}</p>
        </article>
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('overview.stats.publishCompletion')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{toPercent(publishPercent)}</p>
        </article>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-semibold text-slate-900">{t('overview.quickActions.title')}</h2>
          <div className="mt-3 grid gap-2 sm:grid-cols-2">
            <Link
              to={ROUTES.apps.siteBasic(site.id)}
              className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100"
            >
              {t('overview.quickActions.basic')}
            </Link>
            <Link
              to={ROUTES.apps.siteTheme(site.id)}
              className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100"
            >
              {t('overview.quickActions.theme')}
            </Link>
            <Link
              to={ROUTES.apps.siteDomain(site.id)}
              className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100"
            >
              {t('overview.quickActions.domain')}
            </Link>
            <Link
              to={ROUTES.apps.siteContents(site.id)}
              className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100"
            >
              {t('overview.quickActions.contents')}
            </Link>
            <Link
              to={ROUTES.apps.siteReservations(site.id)}
              className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100"
            >
              {t('overview.quickActions.reservations')}
            </Link>
            <Link
              to={ROUTES.apps.siteAi(site.id)}
              className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-100"
            >
              {t('overview.quickActions.ai')}
            </Link>
          </div>
        </article>

        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-semibold text-slate-900">{t('overview.publishChecklist.title')}</h2>
          <div className="mt-3 space-y-2">
            {checklistItems.map((item) => (
              <div
                key={item.key}
                className={cn(
                  'flex items-center justify-between rounded-xl border px-3 py-2',
                  item.done ? 'border-emerald-200 bg-emerald-50' : 'border-slate-200 bg-slate-50',
                )}
              >
                <span className="text-sm text-slate-700">{t(`overview.publishChecklist.items.${item.key}`)}</span>
                <span
                  className={cn(
                    'inline-flex items-center gap-1 text-xs font-medium',
                    item.done ? 'text-emerald-700' : 'text-slate-500',
                  )}
                >
                  {item.done ? <CheckCircle2 className="h-4 w-4" /> : <Circle className="h-4 w-4" />}
                  {item.done ? t('overview.publishChecklist.done') : t('overview.publishChecklist.pending')}
                </span>
              </div>
            ))}
          </div>
          {isPending && (
            <p className="mt-3 inline-flex items-center gap-2 text-xs text-slate-500">
              <Settings className="h-3.5 w-3.5 animate-spin" />
              {t('overview.loadingRuntime')}
            </p>
          )}
        </article>
      </div>
    </section>
  )
}
