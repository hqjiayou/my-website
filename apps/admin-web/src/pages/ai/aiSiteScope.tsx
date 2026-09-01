import { useQuery } from '@tanstack/react-query'
import { useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { siteApi } from '@/api'

export function AiSiteScope({ children }: { children: (siteId: number) => ReactNode }) {
  const { t } = useTranslation('aiAdmin')
  const [selectedSiteId, setSelectedSiteId] = useState('')
  const listQuery = useMemo(() => ({ pageNum: 1, pageSize: 100 }), [])
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['sites', listQuery],
    queryFn: () => siteApi.listSites(listQuery),
  })
  const sites = data?.records ?? []
  const activeSiteId = selectedSiteId || (sites[0] ? String(sites[0].id) : '')
  const numericSiteId = Number(activeSiteId)

  if (isPending) {
    return <p className="text-sm text-slate-500">{t('loading')}</p>
  }
  if (isError) {
    return (
      <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
        <p>{error instanceof Error ? error.message : t('error')}</p>
      </div>
    )
  }
  if (sites.length === 0) {
    return <p className="text-sm text-slate-500">{t('noSites')}</p>
  }

  return (
    <div className="space-y-4">
      <label className="block max-w-sm">
        <span className="text-sm font-medium text-slate-800">{t('siteLabel')}</span>
        <select
          className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
          value={activeSiteId}
          onChange={(event) => setSelectedSiteId(event.target.value)}
        >
          {sites.map((site) => (
            <option key={site.id} value={site.id}>
              {site.siteName}
            </option>
          ))}
        </select>
      </label>
      {Number.isFinite(numericSiteId) && numericSiteId > 0 ? children(numericSiteId) : null}
    </div>
  )
}
