import { useQuery } from '@tanstack/react-query'
import { AlertCircle, Layers } from 'lucide-react'
import { useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { agentApi } from '@/api'
import { adminFieldClass, adminSecondaryButtonSmClass } from '@/components/admin/controls'
import { cn } from '@/lib/cn'

export function AiAgentScope({ children }: { children: (agentId: number) => ReactNode }) {
  const { t } = useTranslation('aiAdmin')
  const [selectedAgentId, setSelectedAgentId] = useState('')
  const listQuery = useMemo(() => ({ pageNum: 1, pageSize: 100 }), [])
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', listQuery],
    queryFn: () => agentApi.listSites(listQuery),
  })
  const sites = data?.records ?? []
  const activeAgentId = selectedAgentId || (sites[0] ? String(sites[0].id) : '')
  const numericAgentId = Number(activeAgentId)

  if (isPending) {
    return (
      <div
        className="flex flex-wrap items-center gap-3 rounded-xl border border-admin bg-admin-surface px-4 py-3"
        aria-hidden
      >
        <div className="admin-skeleton h-4 w-16" />
        <div className="admin-skeleton h-9 w-64" />
      </div>
    )
  }
  if (isError) {
    return (
      <div className="flex items-center gap-2.5 rounded-xl border border-danger/35 bg-danger-muted px-4 py-3">
        <AlertCircle className="h-4.5 w-4.5 shrink-0 text-danger-emphasis" aria-hidden />
        <p className="min-w-0 flex-1 text-sm text-danger-emphasis">
          {error instanceof Error ? error.message : t('error')}
        </p>
        <button
          type="button"
          className={cn(adminSecondaryButtonSmClass, 'shrink-0')}
          onClick={() => void refetch()}
        >
          {t('retry')}
        </button>
      </div>
    )
  }
  if (sites.length === 0) {
    return (
      <div className="flex flex-col items-center gap-2 rounded-xl border border-admin bg-admin-surface px-4 py-8 text-center">
        <span className="admin-empty-mark">
          <Layers className="h-6 w-6" aria-hidden />
        </span>
        <p className="text-sm font-medium text-admin">{t('noSites')}</p>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-admin bg-admin-surface px-4 py-3">
        <label htmlFor="ai-agent-scope-select" className="text-sm font-semibold text-admin">
          {t('agentLabel')}
        </label>
        <select
          id="ai-agent-scope-select"
          className={cn(adminFieldClass, 'w-64 rounded-admin-md')}
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
