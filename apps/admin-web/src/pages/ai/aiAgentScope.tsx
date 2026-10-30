import { useQuery } from '@tanstack/react-query'
import { useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { agentApi } from '@/api'

export function AiAgentScope({ children }: { children: (agentId: number) => ReactNode }) {
  const { t } = useTranslation('aiAdmin')
  const [selectedAgentId, setSelectedAgentId] = useState('')
  const listQuery = useMemo(() => ({ pageNum: 1, pageSize: 100 }), [])
  const { data, isPending, isError, error } = useQuery({
    queryKey: ['sites', listQuery],
    queryFn: () => agentApi.listSites(listQuery),
  })
  const sites = data?.records ?? []
  const activeAgentId = selectedAgentId || (sites[0] ? String(sites[0].id) : '')
  const numericAgentId = Number(activeAgentId)

  if (isPending) {
    return <p className="text-sm text-muted-foreground">{t('loading')}</p>
  }
  if (isError) {
    return (
      <div className="rounded-lg border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis">
        <p>{error instanceof Error ? error.message : t('error')}</p>
      </div>
    )
  }
  if (sites.length === 0) {
    return <p className="text-sm text-muted-foreground">{t('noSites')}</p>
  }

  return (
    <div className="space-y-4">
      <label className="block max-w-sm">
        <span className="text-sm font-medium text-foreground">{t('agentLabel')}</span>
        <select
          className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
          value={activeAgentId}
          onChange={(event) => setSelectedAgentId(event.target.value)}
        >
          {sites.map((site) => (
            <option key={site.id} value={site.id}>
              {site.agentName}
            </option>
          ))}
        </select>
      </label>
      {Number.isFinite(numericAgentId) && numericAgentId > 0 ? children(numericAgentId) : null}
    </div>
  )
}
