import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import type { AiRuntimeLimits } from '@/types/ai'

const LIMIT_KEYS: Array<keyof AiRuntimeLimits> = [
  'maxRequests',
  'maxToolCalls',
  'maxTotalTokens',
  'toolTimeoutSeconds',
  'historyMaxChars',
]

function McpReadyProbe({ siteId }: { siteId: number }) {
  const { t } = useTranslation('sites')
  const probeQuery = useQuery({
    queryKey: ['sites', siteId, 'ai', 'mcp-ready'],
    queryFn: () => aiApi.probeMcp(siteId),
    enabled: false,
  })
  const probe = probeQuery.data

  return (
    <div className="mt-3 border-t border-slate-100 pt-2">
      <button
        type="button"
        className="text-xs underline"
        disabled={probeQuery.isFetching}
        onClick={() => void probeQuery.refetch()}
      >
        {probeQuery.isFetching ? t('aiWorkspace.runtime.mcpReady.probing') : t('aiWorkspace.runtime.mcpReady.probe')}
      </button>
      {probeQuery.isError ? (
        <p className="mt-1 text-xs text-rose-700">{t('aiWorkspace.runtime.mcpReady.loadError')}</p>
      ) : null}
      {probe && !probe.reachable ? (
        <p className="mt-1 text-xs text-amber-800">
          {t('aiWorkspace.runtime.mcpReady.unreachable', { code: probe.errorCode ?? 'SYSTEM_ERROR' })}
        </p>
      ) : null}
      {probe?.reachable && probe.probe ? (
        <div className="mt-1 space-y-1">
          <p className={`text-xs ${probe.probe.ok ? 'text-emerald-700' : 'text-rose-700'}`}>
            {t(probe.probe.ok ? 'aiWorkspace.runtime.mcpReady.ok' : 'aiWorkspace.runtime.mcpReady.failed')}
          </p>
          {probe.probe.servers.map((server) => (
            <p key={server.name} className="flex justify-between text-xs">
              <span className="font-mono">{server.name}</span>
              <span className={server.ok ? 'text-emerald-700' : 'text-rose-700'}>
                {server.ok
                  ? t('aiWorkspace.runtime.mcpReady.tools', { count: server.toolCount ?? 0 })
                  : (server.error ?? t('aiWorkspace.runtime.mcpReady.failed'))}
              </span>
            </p>
          ))}
        </div>
      ) : null}
    </div>
  )
}

function statusClass(status: string | null) {
  if (status === 'LIVE' || status === 'CONFIGURED') return 'bg-emerald-100 text-emerald-700'
  if (status === 'DISABLED') return 'bg-slate-100 text-slate-600'
  return 'bg-amber-100 text-amber-800'
}

/** Agent 运行时只读面板：Provider / Fallback / MCP / Skills / 预算（不含凭证）。 */
export function SiteAiRuntimePanel({ siteId }: { siteId: number }) {
  const { t } = useTranslation('sites')
  const runtimeQuery = useQuery({
    queryKey: ['sites', siteId, 'ai', 'runtime'],
    queryFn: () => aiApi.getRuntime(siteId),
  })
  const skillsQuery = useQuery({
    queryKey: ['sites', siteId, 'ai', 'preview', 'skills'],
    queryFn: () => aiApi.listPreviewSkills(siteId),
    enabled: runtimeQuery.data?.reachable === true,
  })

  if (runtimeQuery.isPending) {
    return <p className="text-sm text-slate-500">{t('aiWorkspace.loading')}</p>
  }
  if (runtimeQuery.isError) {
    return (
      <div className="text-sm text-rose-700">
        <p>{t('aiWorkspace.runtime.loadError')}</p>
        <button type="button" className="mt-1 underline" onClick={() => void runtimeQuery.refetch()}>
          {t('aiWorkspace.retry')}
        </button>
      </div>
    )
  }

  const runtime = runtimeQuery.data
  const header = (
    <div className="mb-4 flex items-center justify-between">
      <p className="text-sm text-slate-600">{t('aiWorkspace.runtime.hint')}</p>
      <button
        type="button"
        className="text-sm underline"
        disabled={runtimeQuery.isFetching}
        onClick={() => void runtimeQuery.refetch()}
      >
        {t('aiWorkspace.runtime.refresh')}
      </button>
    </div>
  )

  if (!runtime.reachable || !runtime.status) {
    return (
      <div>
        {header}
        <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
          {t('aiWorkspace.runtime.unreachable', { code: runtime.errorCode ?? 'SYSTEM_ERROR' })}
        </p>
      </div>
    )
  }

  const status = runtime.status
  return (
    <div>
      {header}
      <dl className="grid gap-4 text-sm md:grid-cols-2">
        <div className="rounded-xl border border-slate-200 p-3">
          <dt className="mb-1 font-medium text-slate-800">{t('aiWorkspace.runtime.provider')}</dt>
          <dd className="flex items-center gap-2">
            <span className="font-mono text-xs">{status.provider ?? '—'}</span>
            <span className={`rounded px-1.5 py-0.5 text-xs ${statusClass(status.providerStatus)}`}>
              {status.providerStatus ?? '—'}
            </span>
          </dd>
          <dd className="mt-2 text-xs text-slate-500">
            {t('aiWorkspace.runtime.fallbacks', {
              ids: status.fallbackProviders.length ? status.fallbackProviders.join(', ') : '—',
            })}
          </dd>
          <dd className="mt-2 flex items-center gap-2 text-xs">
            <span className="text-slate-500">{t('aiWorkspace.runtime.retrieval')}</span>
            <span className={`rounded px-1.5 py-0.5 ${statusClass(status.retrievalStatus)}`}>
              {status.retrievalStatus ?? '—'}
            </span>
          </dd>
          {status.retrievalStatus === 'BLOCKED' && status.retrievalBlockedReason ? (
            <dd className="mt-1 text-xs text-amber-800">{status.retrievalBlockedReason}</dd>
          ) : null}
        </div>

        <div className="rounded-xl border border-slate-200 p-3">
          <dt className="mb-1 flex items-center gap-2 font-medium text-slate-800">
            MCP
            <span className={`rounded px-1.5 py-0.5 text-xs font-normal ${statusClass(status.mcpStatus)}`}>
              {status.mcpStatus ?? '—'}
            </span>
          </dt>
          {status.mcpServers.length === 0 ? (
            <dd className="text-xs text-slate-400">{t('aiWorkspace.runtime.noMcp')}</dd>
          ) : (
            status.mcpServers.map((server) => (
              <dd key={server.name} className="flex justify-between text-xs">
                <span className="font-mono">{server.name}</span>
                <span className={server.enabled ? 'text-emerald-700' : 'text-slate-400'}>
                  {t(server.enabled ? 'aiWorkspace.runtime.enabled' : 'aiWorkspace.runtime.disabled')}
                </span>
              </dd>
            ))
          )}
          <McpReadyProbe siteId={siteId} />
        </div>

        <div className="rounded-xl border border-slate-200 p-3">
          <dt className="mb-1 font-medium text-slate-800">{t('aiWorkspace.runtime.limits.title')}</dt>
          {LIMIT_KEYS.map((key) => (
            <dd key={key} className="flex justify-between text-xs">
              <span className="text-slate-500">{t(`aiWorkspace.runtime.limits.${key}`)}</span>
              <span className="font-mono">{status.limits[key] ?? t('aiWorkspace.runtime.limits.unlimited')}</span>
            </dd>
          ))}
        </div>

        <div className="rounded-xl border border-slate-200 p-3">
          <dt className="mb-1 font-medium text-slate-800">{t('aiWorkspace.preview.skills.title')}</dt>
          {skillsQuery.isPending ? (
            <dd className="text-xs text-slate-500">{t('aiWorkspace.loading')}</dd>
          ) : skillsQuery.isError ? (
            <dd className="text-xs text-rose-700">{t('aiWorkspace.preview.skills.error')}</dd>
          ) : skillsQuery.data.length === 0 ? (
            <dd className="text-xs text-slate-400">{t('aiWorkspace.preview.skills.empty')}</dd>
          ) : (
            skillsQuery.data.map((skill) => (
              <dd key={skill.id} className="text-xs">
                <span className="font-mono text-slate-800">{skill.id}</span>
                {skill.version ? <span className="ml-1 text-slate-400">v{skill.version}</span> : null}
                {skill.description ? <span className="block text-slate-500">{skill.description}</span> : null}
              </dd>
            ))
          )}
        </div>
      </dl>
    </div>
  )
}
