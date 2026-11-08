import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Activity, RefreshCw, Server, Sparkles, Wrench } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { cn } from '@/lib/cn'
import { PermissionGate } from '@/router/permission'
import type { AiMcpSelectionMode, AiRuntimeLimits } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

import { modelIdFromSpec } from '@/utils/modelSpec'

import { AgentAiChainPresetSection } from './agentAiChainPresetSection'
import { SkillCatalogItem } from './agentAiSkillCatalog'

const LIMIT_KEYS: Array<keyof AiRuntimeLimits> = [
  'maxRequests',
  'maxToolCalls',
  'maxTotalTokens',
  'toolTimeoutSeconds',
  'historyMaxChars',
]

function statusTone(status: string | null) {
  if (status === 'LIVE' || status === 'CONFIGURED') {
    return { chip: 'bg-success-muted text-success-emphasis', dot: 'bg-success' }
  }
  if (status === 'DISABLED') {
    return { chip: 'bg-card-muted text-muted-foreground', dot: 'bg-muted-foreground' }
  }
  return { chip: 'bg-warning-muted text-warning-emphasis', dot: 'bg-warning' }
}

function McpReadyProbe({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const probeQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'mcp-ready'],
    queryFn: () => aiApi.probeMcp(agentId),
    enabled: false,
  })
  const probe = probeQuery.data

  return (
    <div className="mt-3 border-t border-border pt-3">
      <div className="flex flex-wrap items-center gap-3">
        <button
          type="button"
          className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-border px-2.5 py-1.5 text-xs font-medium text-foreground transition hover:bg-card-muted"
          disabled={probeQuery.isFetching}
          onClick={() => void probeQuery.refetch()}
        >
          <Activity className={cn('h-3.5 w-3.5', probeQuery.isFetching && 'animate-pulse')} aria-hidden />
          {probeQuery.isFetching ? t('aiWorkspace.runtime.mcpReady.probing') : t('aiWorkspace.runtime.mcpReady.probe')}
        </button>
        {probeQuery.isError ? (
          <p className="text-xs text-danger-emphasis">{t('aiWorkspace.runtime.mcpReady.loadError')}</p>
        ) : null}
        {probe && !probe.reachable ? (
          <p className="text-xs text-warning-emphasis">
            {t('aiWorkspace.runtime.mcpReady.unreachable', { code: probe.errorCode ?? 'SYSTEM_ERROR' })}
          </p>
        ) : null}
        {probe?.reachable && probe.probe ? (
          <p className={cn('text-xs font-medium', probe.probe.ok ? 'text-success-emphasis' : 'text-danger-emphasis')}>
            {t(probe.probe.ok ? 'aiWorkspace.runtime.mcpReady.ok' : 'aiWorkspace.runtime.mcpReady.failed')}
          </p>
        ) : null}
      </div>
      {probe?.reachable && probe.probe && probe.probe.servers.length > 0 ? (
        <ul className="mt-2 space-y-1.5">
          {probe.probe.servers.map((server) => (
            <li key={server.name} className="flex justify-between gap-2 text-xs">
              <span className="truncate font-mono text-foreground">{server.name}</span>
              <span className={server.ok ? 'text-success-emphasis' : 'text-danger-emphasis'}>
                {server.ok
                  ? t('aiWorkspace.runtime.mcpReady.tools', { count: server.toolCount ?? 0 })
                  : (server.error ?? t('aiWorkspace.runtime.mcpReady.failed'))}
              </span>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

function LimitBar({ label, value, unlimitedLabel }: { label: string; value: number | null; unlimitedLabel: string }) {
  const capped = value != null && value > 0
  const percent = capped ? Math.min(100, Math.max(8, Math.round((Math.log10(value + 1) / 5) * 100))) : 100
  return (
    <div className="space-y-1.5">
      <div className="flex items-baseline justify-between gap-2 text-xs">
        <span className="text-muted-foreground">{label}</span>
        <span className="font-mono font-medium tabular-nums text-foreground">
          {value ?? unlimitedLabel}
        </span>
      </div>
      <div className="h-1.5 overflow-hidden rounded-full bg-card-muted">
        <div
          className={cn('h-full rounded-full', capped ? 'bg-primary' : 'bg-success')}
          style={{ width: `${percent}%` }}
        />
      </div>
    </div>
  )
}

/**
 * MCP per-agent 选择（Issue #192 UoW-4）：三态（继承全局/不使用/自定义子集）＋生效集预览。
 * 配置在 Java 侧，与 agent-services 是否可达无关，不可达时也渲染。全局注册表优先读
 * mcp-config（名字＋启停）；无读权限时退回运行时状态里的服务器名单。
 * CUSTOM 与全局启用集取交集：全局已删/已禁的名字自动失效，预览与后端组装口径一致。
 */
function McpSelectionSection({
  agentId,
  fallbackServers,
}: {
  agentId: number
  fallbackServers: Array<{ name: string; enabled: boolean }>
}) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<AiMcpSelectionMode>('INHERIT')
  const [selected, setSelected] = useState<string[]>([])
  const [syncedKey, setSyncedKey] = useState<string | null>(null)

  const selectionQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'mcp-selection'],
    queryFn: () => aiApi.getMcpSelection(agentId),
  })
  const registryQuery = useQuery({
    queryKey: ['ai', 'mcp-config'],
    queryFn: () => aiApi.getMcpConfig(),
    retry: false,
  })

  const saved = selectionQuery.data
  if (saved) {
    const key = `${saved.mode}|${saved.serverNames.join(',')}`
    if (key !== syncedKey) {
      setMode(saved.mode)
      setSelected(saved.serverNames)
      setSyncedKey(key)
    }
  }

  const registry = registryQuery.data
    ? registryQuery.data.servers.map((server) => ({ name: server.name, enabled: server.enabled }))
    : fallbackServers
  const enabledNames = registry.filter((server) => server.enabled).map((server) => server.name)
  const staleNames = selected.filter((name) => !registry.some((server) => server.name === name))
  const effective =
    mode === 'INHERIT'
      ? enabledNames
      : mode === 'OFF'
        ? []
        : selected.filter((name) => enabledNames.includes(name))
  const customEmpty = mode === 'CUSTOM' && selected.length === 0

  const saveMutation = useMutation({
    mutationFn: () =>
      aiApi.updateMcpSelection(agentId, {
        mode,
        serverNames: mode === 'CUSTOM' ? selected : [],
      }),
    onSuccess: () => {
      toast.success(t('aiWorkspace.runtime.mcpSelection.saved'))
      void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai', 'mcp-selection'] })
      queryClient.removeQueries({ queryKey: ['sites', agentId, 'ai', 'mcp-ready'] })
    },
    onError: (saveError: Error) => toast.error(saveError.message),
  })

  const modes: Array<{ value: AiMcpSelectionMode; label: string; hint: string }> = [
    {
      value: 'INHERIT',
      label: t('aiWorkspace.runtime.mcpSelection.modeInherit'),
      hint: t('aiWorkspace.runtime.mcpSelection.modeInheritHint'),
    },
    {
      value: 'OFF',
      label: t('aiWorkspace.runtime.mcpSelection.modeOff'),
      hint: t('aiWorkspace.runtime.mcpSelection.modeOffHint'),
    },
    {
      value: 'CUSTOM',
      label: t('aiWorkspace.runtime.mcpSelection.modeCustom'),
      hint: t('aiWorkspace.runtime.mcpSelection.modeCustomHint'),
    },
  ]

  return (
    <section className="mt-3 rounded-xl border border-admin bg-admin-surface p-4">
      <div className="mb-3 flex items-center gap-2">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-info-muted text-info-emphasis">
          <Wrench className="h-4 w-4" aria-hidden />
        </span>
        <h3 className="text-sm font-semibold text-admin">
          {t('aiWorkspace.runtime.mcpSelection.title')}
        </h3>
      </div>

      {selectionQuery.isPending ? (
        <p className="text-xs text-muted-foreground">{t('aiWorkspace.loading')}</p>
      ) : selectionQuery.isError ? (
        <div className="text-xs text-danger-emphasis">
          <p>{t('aiWorkspace.runtime.mcpSelection.loadError')}</p>
          <button type="button" className="mt-1 underline" onClick={() => void selectionQuery.refetch()}>
            {t('aiWorkspace.retry')}
          </button>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            {modes.map((item) => (
              <label
                key={item.value}
                className={cn(
                  'flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-xs',
                  mode === item.value
                    ? 'border-primary/50 bg-primary/10 font-medium text-foreground'
                    : 'border-border bg-card text-muted-foreground',
                )}
              >
                <input
                  type="radio"
                  name={`mcp-selection-mode-${agentId}`}
                  className="accent-primary"
                  checked={mode === item.value}
                  onChange={() => setMode(item.value)}
                />
                <span>
                  {item.label}
                  <span className="block font-normal text-muted-foreground">{item.hint}</span>
                </span>
              </label>
            ))}
          </div>

          {mode === 'CUSTOM' ? (
            registry.length === 0 ? (
              <p className="text-xs text-muted-foreground">
                {t('aiWorkspace.runtime.mcpSelection.registryEmpty')}
              </p>
            ) : (
              <ul className="space-y-1.5">
                {registry.map((server) => (
                  <li key={server.name}>
                    <label className="flex cursor-pointer items-center gap-2 rounded-lg border border-border bg-card px-3 py-2 text-xs">
                      <input
                        type="checkbox"
                        className="accent-primary"
                        checked={selected.includes(server.name)}
                        disabled={!server.enabled && !selected.includes(server.name)}
                        onChange={(event) =>
                          setSelected((current) =>
                            event.target.checked
                              ? [...current, server.name]
                              : current.filter((name) => name !== server.name),
                          )
                        }
                      />
                      <span className="min-w-0 flex-1 truncate font-mono text-foreground">{server.name}</span>
                      {!server.enabled ? (
                        <span className="text-muted-foreground">
                          {t('aiWorkspace.runtime.mcpSelection.disabledTag')}
                        </span>
                      ) : null}
                    </label>
                  </li>
                ))}
              </ul>
            )
          ) : null}

          {staleNames.length > 0 ? (
            <p className="text-xs text-warning-emphasis">
              {t('aiWorkspace.runtime.mcpSelection.staleNames', { names: staleNames.join(', ') })}
            </p>
          ) : null}

          <p className="text-xs text-muted-foreground">
            {t('aiWorkspace.runtime.mcpSelection.effective')}：
            {effective.length > 0 ? (
              <span className="font-mono text-foreground">{effective.join(', ')}</span>
            ) : (
              t('aiWorkspace.runtime.mcpSelection.effectiveEmpty')
            )}
          </p>

          {customEmpty ? (
            <p className="text-xs text-warning-emphasis">
              {t('aiWorkspace.runtime.mcpSelection.customEmpty')}
            </p>
          ) : null}

          <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
            <button
              type="button"
              className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground transition hover:opacity-90 disabled:opacity-50"
              disabled={saveMutation.isPending || customEmpty}
              onClick={() => saveMutation.mutate()}
            >
              {saveMutation.isPending
                ? t('aiWorkspace.runtime.mcpSelection.saving')
                : t('aiWorkspace.runtime.mcpSelection.save')}
            </button>
          </PermissionGate>
        </div>
      )}
    </section>
  )
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
    <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
      <p className="text-sm text-muted-foreground">{t('aiWorkspace.runtime.hint')}</p>
      <button
        type="button"
        className="inline-flex items-center gap-1.5 rounded-lg border border-border px-2.5 py-1.5 text-xs font-medium text-foreground transition hover:bg-card-muted disabled:opacity-50"
        disabled={runtimeQuery.isFetching}
        onClick={() => void runtimeQuery.refetch()}
      >
        <RefreshCw className={cn('h-3.5 w-3.5', runtimeQuery.isFetching && 'animate-spin')} aria-hidden />
        {t('aiWorkspace.runtime.refresh')}
      </button>
    </div>
  )

  if (!runtime.reachable || !runtime.status) {
    return (
      <div>
        {header}
        <p className="rounded-xl border border-warning/30 bg-warning-muted px-4 py-3 text-sm text-warning-emphasis">
          {t('aiWorkspace.runtime.unreachable', { code: runtime.errorCode ?? 'SYSTEM_ERROR' })}
        </p>
        <McpSelectionSection agentId={agentId} fallbackServers={[]} />
        <AgentAiChainPresetSection agentId={agentId} />
      </div>
    )
  }

  const status = runtime.status
  const providerTone = statusTone(status.providerStatus)
  const retrievalTone = statusTone(status.retrievalStatus)
  const mcpTone = statusTone(status.mcpStatus)

  return (
    <div>
      {header}
      <div className="grid gap-3 md:grid-cols-2">
        <section className="rounded-xl border border-admin bg-admin-surface p-4">
          <div className="mb-3 flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Server className="h-4 w-4" aria-hidden />
            </span>
            <div className="min-w-0">
              <h3 className="text-sm font-semibold text-admin">{t('aiWorkspace.runtime.provider')}</h3>
              <p className="truncate font-mono text-xs text-muted-foreground">
                {status.provider ? modelIdFromSpec(status.provider) : '—'}
              </p>
            </div>
            <span className={cn('ml-auto inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[11px] font-medium', providerTone.chip)}>
              <span className={cn('h-1.5 w-1.5 rounded-full', providerTone.dot)} aria-hidden />
              {status.providerStatus ?? '—'}
            </span>
          </div>
          <p className="text-xs leading-5 text-muted-foreground">
            {t('aiWorkspace.runtime.fallbacks', {
              ids: status.fallbackProviders.length ? status.fallbackProviders.map(modelIdFromSpec).join(', ') : '—',
            })}
          </p>
          <div className="mt-3 flex items-center justify-between gap-2 rounded-lg border border-border bg-card px-3 py-2">
            <span className="text-xs text-muted-foreground">{t('aiWorkspace.runtime.retrieval')}</span>
            <span className={cn('inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[11px] font-medium', retrievalTone.chip)}>
              <span className={cn('h-1.5 w-1.5 rounded-full', retrievalTone.dot)} aria-hidden />
              {status.retrievalStatus ?? '—'}
            </span>
          </div>
          {status.retrievalStatus === 'BLOCKED' && status.retrievalBlockedReason ? (
            <p className="mt-2 text-xs text-warning-emphasis">{status.retrievalBlockedReason}</p>
          ) : null}
        </section>

        <section className="rounded-xl border border-admin bg-admin-surface p-4">
          <div className="mb-3 flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-info-muted text-info-emphasis">
              <Wrench className="h-4 w-4" aria-hidden />
            </span>
            <h3 className="text-sm font-semibold text-admin">MCP</h3>
            <span className={cn('ml-auto inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[11px] font-medium', mcpTone.chip)}>
              <span className={cn('h-1.5 w-1.5 rounded-full', mcpTone.dot)} aria-hidden />
              {status.mcpStatus ?? '—'}
            </span>
          </div>
          {status.mcpServers.length === 0 ? (
            <p className="text-xs text-muted-foreground">{t('aiWorkspace.runtime.noMcp')}</p>
          ) : (
            <ul className="space-y-1.5">
              {status.mcpServers.map((server) => (
                <li
                  key={server.name}
                  className="flex items-center justify-between gap-2 rounded-lg border border-border bg-card px-3 py-2 text-xs"
                >
                  <span className="flex min-w-0 items-center gap-2">
                    <span
                      className={cn('h-1.5 w-1.5 shrink-0 rounded-full', server.enabled ? 'bg-success' : 'bg-muted-foreground')}
                      aria-hidden
                    />
                    <span className="truncate font-mono text-foreground">{server.name}</span>
                  </span>
                  <span className={server.enabled ? 'text-success-emphasis' : 'text-muted-foreground'}>
                    {t(server.enabled ? 'aiWorkspace.runtime.enabled' : 'aiWorkspace.runtime.disabled')}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <McpReadyProbe agentId={agentId} />
        </section>

        <section className="rounded-xl border border-admin bg-admin-surface p-4">
          <div className="mb-3 flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-warning-muted text-warning-emphasis">
              <Activity className="h-4 w-4" aria-hidden />
            </span>
            <h3 className="text-sm font-semibold text-admin">{t('aiWorkspace.runtime.limits.title')}</h3>
          </div>
          <div className="space-y-3">
            {LIMIT_KEYS.map((key) => (
              <LimitBar
                key={key}
                label={t(`aiWorkspace.runtime.limits.${key}`)}
                value={status.limits[key]}
                unlimitedLabel={t('aiWorkspace.runtime.limits.unlimited')}
              />
            ))}
          </div>
        </section>

        <section className="rounded-xl border border-admin bg-admin-surface p-4">
          <div className="mb-3 flex items-center gap-2">
            <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-success-muted text-success-emphasis">
              <Sparkles className="h-4 w-4" aria-hidden />
            </span>
            <h3 className="text-sm font-semibold text-admin">{t('aiWorkspace.preview.skills.title')}</h3>
            {skillsQuery.data ? (
              <span className="ml-auto rounded-md bg-card px-2 py-0.5 font-mono text-[11px] text-muted-foreground">
                {skillsQuery.data.length}
              </span>
            ) : null}
          </div>
          {skillsQuery.isPending ? (
            <p className="text-xs text-muted-foreground">{t('aiWorkspace.loading')}</p>
          ) : skillsQuery.isError ? (
            <p className="text-xs text-danger-emphasis">{t('aiWorkspace.preview.skills.error')}</p>
          ) : skillsQuery.data.length === 0 ? (
            <p className="text-xs text-muted-foreground">{t('aiWorkspace.preview.skills.empty')}</p>
          ) : (
            <ul className="space-y-2">
              {skillsQuery.data.map((skill) => (
                <li key={skill.id} className="rounded-lg border border-border bg-card px-3 py-2">
                  <SkillCatalogItem id={skill.id} version={skill.version} description={skill.description} />
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
      <McpSelectionSection agentId={agentId} fallbackServers={status.mcpServers} />
      <AgentAiChainPresetSection agentId={agentId} />
    </div>
  )
}
