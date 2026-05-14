import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'

import { siteApi } from '@/api'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/route-paths'
import type { SiteStatus } from '@/api'

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

export function SitesPage() {
  const { t } = useTranslation('sites')
  const navigate = useNavigate()
  const { data: sites = [] } = useQuery({
    queryKey: ['sites'],
    queryFn: siteApi.listSites,
  })

  return (
    <section>
      <PageHeader
        title={t('list.title')}
        description={t('list.description')}
        action={
          <button
            type="button"
            onClick={() => navigate(ROUTES.apps.siteCreate)}
            className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-slate-800"
          >
            <Plus className="h-4 w-4" />
            <span>{t('list.createSite')}</span>
          </button>
        }
      />

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-4 py-3">{t('list.columns.siteName')}</th>
                <th className="px-4 py-3">{t('list.columns.industry')}</th>
                <th className="px-4 py-3">{t('list.columns.template')}</th>
                <th className="px-4 py-3">{t('list.columns.domain')}</th>
                <th className="px-4 py-3">{t('list.columns.status')}</th>
                <th className="px-4 py-3">{t('list.columns.updatedAt')}</th>
                <th className="px-4 py-3">{t('list.columns.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {sites.map((site) => (
                <tr key={site.id} className="align-top">
                  <td className="px-4 py-3">
                    <p className="font-medium text-slate-900">{site.name}</p>
                    <p className="mt-1 text-xs text-slate-500">{site.code}</p>
                  </td>
                  <td className="px-4 py-3 text-slate-700">{t(`industry.${site.industry}`)}</td>
                  <td className="px-4 py-3 text-slate-700">{site.templateCode}</td>
                  <td className="px-4 py-3 text-slate-700">{site.domain}</td>
                  <td className="px-4 py-3">
                    <span className={cn('inline-flex rounded-md border px-2 py-1 text-xs font-medium', statusClassName(site.status))}>
                      {t(`status.${site.status}`)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-700">{new Date(site.updatedAt).toLocaleDateString()}</td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-2">
                      <Link
                        to={ROUTES.apps.siteOverview(site.id)}
                        className="rounded-md border border-blue-200 bg-blue-50 px-2.5 py-1.5 text-xs font-medium text-blue-700 transition hover:bg-blue-100"
                      >
                        {t('list.actions.openWorkspace')}
                      </Link>
                      <Link
                        to={ROUTES.apps.siteBasic(site.id)}
                        className="rounded-md border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
                      >
                        {t('list.actions.basicInfo')}
                      </Link>
                      <Link
                        to={ROUTES.apps.siteTheme(site.id)}
                        className="rounded-md border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
                      >
                        {t('list.actions.themeSettings')}
                      </Link>
                      <Link
                        to={ROUTES.apps.siteDomain(site.id)}
                        className="rounded-md border border-slate-200 px-2.5 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
                      >
                        {t('list.actions.domainSettings')}
                      </Link>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </section>
  )
}
