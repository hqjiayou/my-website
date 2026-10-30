import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import type { AiRuntimeLimits } from '@/types/ai'

import { modelIdFromSpec } from '@/utils/modelSpec'

const LIMIT_KEYS: Array<keyof AiRuntimeLimits> = [
  'maxRequests',
  'maxToolCalls',
  'maxTotalTokens',
  'toolTimeoutSeconds',
  'historyMaxChars',
]

function McpReadyProbe({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const probeQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'mcp-ready'],
    queryFn: () => aiApi.probeMcp(agentId),
    enabled: false,
  })
  const probe = probeQuery.data

  return (
    <div className="mt-3 border-t border-border pt-2">
      <button
        type="button"
        className="text-xs underline"
        disabled={probeQuery.isFetching}
        onClick={() => void probeQuery.refetch()}
      >
        {probeQuery.isFetching ? t('aiWorkspace.runtime.mcpReady.probing') : t('aiWorkspace.runtime.mcpReady.probe')}
      </button>
      {probeQuery.isError ? (
        <p className="mt-1 text-xs text-danger-emphasis">{t('aiWorkspace.runtime.mcpReady.loadError')}</p>
      ) : null}
      {probe && !probe.reachable ? (
        <p className="mt-1 text-xs text-warning-emphasis">
          {t('aiWorkspace.runtime.mcpReady.unreachable', { code: probe.errorCode ?? 'SYSTEM_ERROR' })}
        </p>
      ) : null}
      {probe?.reachable && probe.probe ? (
        <div className="mt-1 space-y-1">
          <p className={`text-xs ${probe.probe.ok ? 'text-success-emphasis' : 'text-danger-emphasis'}`}>
            {t(probe.probe.ok ? 'aiWorkspace.runtime.mcpReady.ok' : 'aiWorkspace.runtime.mcpReady.failed')}
          </p>
          {probe.probe.servers.map((server) => (
            <p key={server.name} className="flex justify-between text-xs">
              <span className="font-mono">{server.name}</span>
              <span className={server.ok ? 'text-success-emphasis' : 'text-danger-emphasis'}>
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
  if (status === 'LIVE' || status === 'CONFIGURED') return 'bg-success-muted text-success-emphasis'
  if (status === 'DISABLED') return 'bg-card-muted text-muted-foreground'
  return 'bg-warning-muted text-warning-emphasis'
}

/** Agent 运行时只读面板：Provider / Fallback / MCP / Skills / 预算（不含凭证）。 */
export function AgentAiRuntimePanel({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const runtimeQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'runtime'],
    queryFn: () => aiApi.getRuntime(agentId),
  })
  const skillsQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'preview', 'skills'],
    queryFn: () => aiApi.listPreviewSkills(agentId),
    enabled: runtimeQuery.data?.reachable === true,
  })

  if (runtimeQuery.isPending) {
    return <p className="text-sm text-muted-foreground">{t('aiWorkspace.loading')}</p>
  }
  if (runtimeQuery.isError) {
    return (
      <div className="text-sm text-danger-emphasis">
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
      <p className="text-sm text-muted-foreground">{t('aiWorkspace.runtime.hint')}</p>
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
        <p className="rounded-lg bg-warning-muted px-3 py-2 text-sm text-warning-emphasis">
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
        <div className="rounded-xl border border-border p-3">
          <dt className="mb-1 font-medium text-foreground">{t('aiWorkspace.runtime.provider')}</dt>
          <dd className="flex items-center gap-2">
            <span className="font-mono text-xs">{status.provider ? modelIdFromSpec(status.provider) : '—'}</span>
            <span className={`rounded px-1.5 py-0.5 text-xs ${statusClass(status.providerStatus)}`}>
              {status.providerStatus ?? '—'}
            </span>
          </dd>
          <dd className="mt-2 text-xs text-muted-foreground">
            {t('aiWorkspace.runtime.fallbacks', {
              ids: status.fallbackProviders.length ? status.fallbackProviders.map(modelIdFromSpec).join(', ') : '—',
            })}
          </dd>
          <dd className="mt-2 flex items-center gap-2 text-xs">
            <span className="text-muted-foreground">{t('aiWorkspace.runtime.retrieval')}</span>
            <span className={`rounded px-1.5 py-0.5 ${statusClass(status.retrievalStatus)}`}>
              {status.retrievalStatus ?? '—'}
            </span>
          </dd>
          {status.retrievalStatus === 'BLOCKED' && status.retrievalBlockedReason ? (
            <dd className="mt-1 text-xs text-warning-emphasis">{status.retrievalBlockedReason}</dd>
          ) : null}
        </div>

        <div className="rounded-xl border border-border p-3">
          <dt className="mb-1 flex items-center gap-2 font-medium text-foreground">
            MCP
            <span className={`rounded px-1.5 py-0.5 text-xs font-normal ${statusClass(status.mcpStatus)}`}>
              {status.mcpStatus ?? '—'}
            </span>
          </dt>
          {status.mcpServers.length === 0 ? (
            <dd className="text-xs text-muted-foreground">{t('aiWorkspace.runtime.noMcp')}</dd>
          ) : (
            status.mcpServers.map((server) => (
              <dd key={server.name} className="flex justify-between text-xs">
                <span className="font-mono">{server.name}</span>
                <span className={server.enabled ? 'text-success-emphasis' : 'text-muted-foreground'}>
                  {t(server.enabled ? 'aiWorkspace.runtime.enabled' : 'aiWorkspace.runtime.disabled')}
                </span>
              </dd>
            ))
          )}
          <McpReadyProbe agentId={agentId} />
        </div>

        <div className="rounded-xl border border-border p-3">
          <dt className="mb-1 font-medium text-foreground">{t('aiWorkspace.runtime.limits.title')}</dt>
          {LIMIT_KEYS.map((key) => (
            <dd key={key} className="flex justify-between text-xs">
              <span className="text-muted-foreground">{t(`aiWorkspace.runtime.limits.${key}`)}</span>
              <span className="font-mono">{status.limits[key] ?? t('aiWorkspace.runtime.limits.unlimited')}</span>
            </dd>
          ))}
        </div>

        <div className="rounded-xl border border-border p-3">
          <dt className="mb-1 font-medium text-foreground">{t('aiWorkspace.preview.skills.title')}</dt>
          {skillsQuery.isPending ? (
            <dd className="text-xs text-muted-foreground">{t('aiWorkspace.loading')}</dd>
          ) : skillsQuery.isError ? (
            <dd className="text-xs text-danger-emphasis">{t('aiWorkspace.preview.skills.error')}</dd>
          ) : skillsQuery.data.length === 0 ? (
            <dd className="text-xs text-muted-foreground">{t('aiWorkspace.preview.skills.empty')}</dd>
          ) : (
            skillsQuery.data.map((skill) => (
              <dd key={skill.id} className="text-xs">
                <span className="font-mono text-foreground">{skill.id}</span>
                {skill.version ? <span className="ml-1 text-muted-foreground">v{skill.version}</span> : null}
                {skill.description ? <span className="block text-muted-foreground">{skill.description}</span> : null}
              </dd>
            ))
          )}
        </div>
      </dl>
    </div>
  )
}
