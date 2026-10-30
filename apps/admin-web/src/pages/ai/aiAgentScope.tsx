import { useQuery } from '@tanstack/react-query'
import { useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { agentApi } from '@/api'
import { adminFieldClass } from '@/components/admin/controls'

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
    return (
      <div className="rounded-[14px] border border-admin bg-admin-surface px-4 py-3 text-sm text-admin-muted">
        {t('loading')}
      </div>
    )
  }
  if (isError) {
    return (
      <div className="rounded-[14px] border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis">
        <p>{error instanceof Error ? error.message : t('error')}</p>
      </div>
    )
  }
  if (sites.length === 0) {
    return (
      <div className="rounded-[14px] border border-admin bg-admin-surface px-4 py-3 text-sm text-admin-muted">
        {t('noSites')}
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 rounded-[14px] border border-admin bg-admin-surface px-4 py-3">
        <label htmlFor="ai-agent-scope-select" className="text-sm font-semibold text-admin">
          {t('agentLabel')}
        </label>
        <select
          id="ai-agent-scope-select"
          className={`${adminFieldClass} w-64`}
          value={activeAgentId}
          onChange={(event) => setSelectedAgentId(event.target.value)}
        >
          {sites.map((site) => (
            <option key={site.id} value={site.id}>
              {site.agentName}
            </option>
          ))}
        </select>
      </div>
      {Number.isFinite(numericAgentId) && numericAgentId > 0 ? children(numericAgentId) : null}
    </div>
  )
}
