import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { agentApi } from '@/api/apps/agentApi'
import { CopyableText } from '@/components/admin/copyableText'
import { AdminPageHeader } from '@/components/admin'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type {
  AiModelEntry,
  AiModelEntryWriteInput,
  GatewayAccessKey,
  GatewayAccessKeyIssueResponse,
} from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'
import { modelIdFromSpec } from '@/utils/modelSpec'

type Draft = {
  id: number | null
  displayName: string
  modelSpec: string
  apiKey: string
  baseUrl: string
  agentId: string
  embeddingSpec: string
  enabled: boolean
  defaultEntry: boolean
  mode: string
  alias: string
  version: number | null
  apiKeyHint: string | null
}

function emptyDraft(makeDefault: boolean): Draft {
  return {
    id: null,
    displayName: '',
    modelSpec: '',
    apiKey: '',
    baseUrl: '',
    agentId: '',
    embeddingSpec: '',
    enabled: true,
    defaultEntry: makeDefault,
    mode: 'KEY',
    alias: '',
    version: null,
    apiKeyHint: null,
  }
}

/**
 * 生效链（与 Java selectionChain 同规则）：作用域预设为主用，其后同作用域其他启用入口、
 * 再全站其余启用入口（各组内默认在前、id 升序）。没有默认入口时整链不启用。
 */
function computeChain(entries: AiModelEntry[], scopeAgentId: number | null): AiModelEntry[] {
  const enabledIn = (scope: number | null) =>
    entries
      .filter((entry) => entry.enabled && (scope == null ? entry.agentId == null : entry.agentId === scope))
      .sort((a, b) => Number(b.defaultEntry) - Number(a.defaultEntry) || a.id - b.id)
  const scoped = scopeAgentId == null ? [] : enabledIn(scopeAgentId)
  const global = enabledIn(null)
  const primary = scoped.find((entry) => entry.defaultEntry) ?? global.find((entry) => entry.defaultEntry)
  if (!primary) return []
  return [
    primary,
    ...scoped.filter((entry) => entry.id !== primary.id),
    ...global.filter((entry) => entry.id !== primary.id),
  ]
}

function draftFromEntry(entry: AiModelEntry): Draft {
  return {
    id: entry.id,
    displayName: entry.displayName,
    modelSpec: entry.modelSpec,
    apiKey: '',
    baseUrl: entry.baseUrl ?? '',
    agentId: entry.agentId == null ? '' : String(entry.agentId),
    embeddingSpec: entry.embeddingSpec ?? '',
    enabled: entry.enabled,
    defaultEntry: entry.defaultEntry,
    mode: entry.mode ?? 'KEY',
    alias: entry.alias ?? '',
    version: entry.version,
    apiKeyHint: entry.apiKeyHint,
  }
}

type GatewayKeyStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'

function gatewayKeyStatus(key: GatewayAccessKey): GatewayKeyStatus {
  if (key.revokedAt) return 'REVOKED'
  if (key.expiresAt && new Date(key.expiresAt).getTime() <= Date.now()) return 'EXPIRED'
  return 'ACTIVE'
}

/**
 * 明文 apiKey 只在签发响应中出现一次：只放组件本地 state 展示，
 * 不写 localStorage，不进 React Query cache 的列表数据。
 */
function GatewayAccessKeysCard() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [name, setName] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [issuedKey, setIssuedKey] = useState<GatewayAccessKeyIssueResponse | null>(null)

  const { data, isPending } = useQuery({
    queryKey: ['ai', 'gateway-access-keys'],
    queryFn: () => aiApi.listGatewayAccessKeys(),
  })

  const issueMutation = useMutation({
    mutationFn: () =>
      aiApi.issueGatewayAccessKey({
        name: name.trim(),
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      }),
    onSuccess: (response) => {
      setIssuedKey(response)
      setName('')
      setExpiresAt('')
      toast.success(t('settings.gatewayKeys.issued'))
      void queryClient.invalidateQueries({ queryKey: ['ai', 'gateway-access-keys'] })
    },
    onError: (issueError: Error) => toast.error(issueError.message),
  })

  const revokeMutation = useMutation({
    mutationFn: (id: number) => aiApi.revokeGatewayAccessKey(id),
    onSuccess: () => {
      toast.success(t('settings.gatewayKeys.revoked'))
      void queryClient.invalidateQueries({ queryKey: ['ai', 'gateway-access-keys'] })
    },
    onError: (revokeError: Error) => toast.error(revokeError.message),
  })

  const keys = data ?? []
  const statusLabel: Record<GatewayKeyStatus, string> = {
    ACTIVE: t('settings.gatewayKeys.statusActive'),
    REVOKED: t('settings.gatewayKeys.statusRevoked'),
    EXPIRED: t('settings.gatewayKeys.statusExpired'),
  }

  return (
    <article className="space-y-3 rounded-xl border border-border bg-card p-5">
      <h3 className="text-sm font-semibold text-foreground">{t('settings.gatewayKeys.title')}</h3>
      <p className="text-sm text-muted-foreground">{t('settings.gatewayKeys.description')}</p>

      {issuedKey ? (
        <div className="rounded-lg border border-warning/35 bg-warning-muted px-4 py-3 text-sm text-warning-emphasis">
          <p className="font-medium">{t('settings.gatewayKeys.oneTimeWarning')}</p>
          <div className="mt-2 flex items-center gap-2 rounded bg-card px-3 py-2">
            <CopyableText value={issuedKey.apiKey} className="font-mono text-xs" />
          </div>
          <button
            type="button"
            className="mt-2 text-xs underline"
            onClick={() => setIssuedKey(null)}
          >
            {t('settings.gatewayKeys.close')}
          </button>
        </div>
      ) : null}

      {isPending ? (
        <p className="text-sm text-muted-foreground">{t('loading')}</p>
      ) : keys.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('settings.gatewayKeys.empty')}</p>
      ) : (
        <ul className="space-y-2">
          {keys.map((key) => {
            const status = gatewayKeyStatus(key)
            return (
              <li
                key={key.id}
                className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm"
              >
                <span>
                  <span className="font-medium text-foreground">{key.name}</span>
                  <span className="ml-2 font-mono text-xs text-muted-foreground">…{key.keyHint}</span>
                  <span className="ml-2 text-xs text-muted-foreground">
                    <span className="text-muted-foreground">{t('settings.gatewayKeys.createdAtTag')}</span>{' '}
                    {new Date(key.createdAt).toLocaleString()}
                  </span>
                  <span className="ml-2 text-xs text-muted-foreground">
                    <span className="text-muted-foreground">{t('settings.gatewayKeys.expiresAtTag')}</span>{' '}
                    {key.expiresAt
                      ? new Date(key.expiresAt).toLocaleString()
                      : t('settings.gatewayKeys.neverExpires')}
                  </span>
                </span>
                <span className="flex items-center gap-2">
                  <span
                    className={
                      status === 'ACTIVE'
                        ? 'rounded bg-success-muted px-1.5 py-0.5 text-xs text-success-emphasis'
                        : 'rounded bg-card-muted px-1.5 py-0.5 text-xs text-muted-foreground'
                    }
                  >
                    {statusLabel[status]}
                  </span>
                  {canUpdate && status === 'ACTIVE' ? (
                    <button
                      type="button"
                      className="rounded-lg border border-danger/35 px-2 py-1 text-xs text-danger-emphasis disabled:opacity-50"
                      disabled={revokeMutation.isPending}
                      onClick={() => {
                        if (!window.confirm(t('settings.gatewayKeys.confirmRevoke'))) return
                        revokeMutation.mutate(key.id)
                      }}
                    >
                      {t('settings.gatewayKeys.revoke')}
                    </button>
                  ) : null}
                </span>
              </li>
            )
          })}
        </ul>
      )}

      <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (!name.trim()) return
            issueMutation.mutate()
          }}
        >
          <label className="block">
            <span className="text-xs font-medium text-foreground">{t('settings.gatewayKeys.name')}</span>
            <input
              className="mt-1 rounded-lg border border-border px-3 py-2 text-sm"
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </label>
          <label className="block">
            <span className="text-xs font-medium text-foreground">
              {t('settings.gatewayKeys.expiresAt')}
            </span>
            <input
              type="datetime-local"
              className="mt-1 rounded-lg border border-border px-3 py-2 text-sm"
              value={expiresAt}
              onChange={(event) => setExpiresAt(event.target.value)}
            />
            <span className="mt-1 block text-xs text-muted-foreground">
              {t('settings.gatewayKeys.expiresAtHint')}
            </span>
          </label>
          <button
            type="submit"
            disabled={issueMutation.isPending || !name.trim()}
            className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
          >
            {issueMutation.isPending ? t('settings.gatewayKeys.issuing') : t('settings.gatewayKeys.issue')}
          </button>
        </form>
      </PermissionGate>
    </article>
  )
}

/** 组链预览按模式拆分的一段：同一作用域下该模式的生效链。 */
function ModeChainSection({ mode, chain }: { mode: string; chain: AiModelEntry[] }) {
  const { t } = useTranslation('aiAdmin')
  return (
    <div className="space-y-1">
      <h4 className="text-xs font-semibold text-foreground">
        {mode === 'CLI' ? t('settings.modeCli') : t('settings.modeKey')}
      </h4>
      {chain.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('settings.chainModeEmpty')}</p>
      ) : (
        <ol className="space-y-1">
          {chain.map((entry, index) => (
            <li key={entry.id} className="flex items-center gap-2 text-sm">
              <span
                className={
                  index === 0
                    ? 'rounded bg-primary px-2 py-0.5 text-xs font-medium text-primary-foreground'
                    : 'rounded bg-card-muted px-2 py-0.5 text-xs text-muted-foreground'
                }
              >
                {index === 0 ? t('settings.chainPrimary') : t('settings.chainFallback', { n: index })}
              </span>
              <span className="font-medium text-foreground">
                {mode === 'CLI' ? modelIdFromSpec(entry.modelSpec) : entry.displayName}
              </span>
              <span className="text-muted-foreground">
                {mode === 'CLI' ? modelIdFromSpec(entry.modelSpec) : entry.modelSpec}
              </span>
              <span className="text-xs text-muted-foreground">
                {entry.agentId == null ? t('settings.agentPlatform') : null}
              </span>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}

function cliModelSpec(modelId: string) {
  const trimmed = modelId.trim()
  if (!trimmed) return trimmed
  return trimmed.includes(':') ? trimmed : `openai:${trimmed}`
}

function cliModels(account: { provider: string; modelIds: string[] }) {
  if (account.provider === 'claude') return account.modelIds
  return account.modelIds.filter((id) => !id.toLowerCase().startsWith('claude'))
}

export function AiSettingsPage() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [chainScope, setChainScope] = useState('')

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
  })
  const sitesQuery = useQuery({
    queryKey: ['sites', 'model-entry-options'],
    queryFn: () => agentApi.listSites({ pageNum: 1, pageSize: 100 }),
  })
  const usageQuery = useQuery({
    queryKey: ['ai', 'proxy-services', 'usage'],
    queryFn: () => aiApi.getProxyServicesUsage(),
  })
  const keysQuery = useQuery({
    queryKey: ['ai', 'proxy-services', 'api-keys'],
    queryFn: () => aiApi.listProxyApiKeys(),
  })

  const saveMutation = useMutation({
    mutationFn: (input: { id: number | null; body: AiModelEntryWriteInput }) =>
      input.id == null ? aiApi.createModelEntry(input.body) : aiApi.updateModelEntry(input.id, input.body),
    onSuccess: (entry) => {
      toast.success(t('settings.saved'))
      setDraft(draftFromEntry(entry))
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (saveError: Error) => {
      toast.error(saveError.message)
    },
  })

  const setDefaultMutation = useMutation({
    mutationFn: (entry: AiModelEntry) =>
      aiApi.updateModelEntry(entry.id, {
        displayName: entry.displayName,
        modelSpec: entry.modelSpec,
        baseUrl: entry.baseUrl,
        agentId: entry.agentId,
        embeddingSpec: entry.embeddingSpec,
        enabled: true,
        defaultEntry: true,
        mode: entry.mode ?? 'KEY',
        alias: entry.alias ?? null,
        version: entry.version,
      }),
    onSuccess: () => {
      toast.success(t('settings.defaultSet'))
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (setError: Error) => {
      toast.error(setError.message)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => aiApi.deleteModelEntry(id),
    onSuccess: () => {
      toast.success(t('settings.deleted'))
      setDraft(null)
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (deleteError: Error) => {
      toast.error(deleteError.message)
    },
  })

  if (isPending) return <p className="text-sm text-muted-foreground">{t('loading')}</p>
  if (isError) {
    return (
      <section className="admin-content-form">
        <AdminPageHeader title={t('settings.title')} description={t('settings.description')} />
        <div className="rounded-lg border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis">
          <p>{error instanceof Error ? error.message : t('error')}</p>
          <button type="button" className="mt-2 underline" onClick={() => void refetch()}>
            {t('retry')}
          </button>
        </div>
      </section>
    )
  }

  const entries = data ?? []
  const scopeAgentId = chainScope === '' ? null : Number(chainScope)
  const keyChain = computeChain(entries.filter((entry) => (entry.mode ?? 'KEY') === 'KEY'), scopeAgentId)
  const cliChain = computeChain(entries.filter((entry) => (entry.mode ?? 'KEY') === 'CLI'), scopeAgentId)
  const aliasTrimmed = draft?.alias.trim() ?? ''
  const aliasInvalid = aliasTrimmed !== '' && !/^[a-z0-9][a-z0-9_-]{1,63}$/.test(aliasTrimmed.toLowerCase())
  const cliAccounts = (usageQuery.data?.accounts ?? []).filter(
    (account) => !account.disabled && !account.unavailable,
  )
  const cliBaseUrl = usageQuery.data?.apiBaseUrl ?? ''
  const clientKeys = keysQuery.data?.apiKeys ?? []
  const cliModelId = modelIdFromSpec(draft?.modelSpec ?? '')
  const cliReady = Boolean(cliBaseUrl) && cliAccounts.some((account) => cliModels(account).length > 0) && clientKeys.length > 0

  function submitDraft() {
    if (!draft || !canUpdate) return
    const body: AiModelEntryWriteInput = {
      displayName:
        draft.mode === 'CLI' ? modelIdFromSpec(draft.modelSpec).trim() || draft.displayName.trim() : draft.displayName.trim(),
      modelSpec: draft.mode === 'CLI' ? cliModelSpec(draft.modelSpec) : draft.modelSpec.trim(),
      baseUrl: draft.mode === 'CLI' ? usageQuery.data?.apiBaseUrl || draft.baseUrl.trim() || null : draft.baseUrl.trim() || null,
      agentId: draft.agentId.trim() === '' ? null : Number(draft.agentId),
      embeddingSpec: draft.mode === 'CLI' ? null : draft.embeddingSpec.trim() || null,
      enabled: draft.enabled,
      defaultEntry: draft.defaultEntry,
      mode: draft.mode,
      alias: draft.alias.trim() || null,
      version: draft.version,
    }
    if (draft.apiKey.trim()) body.apiKey = draft.apiKey.trim()
    saveMutation.mutate({ id: draft.id, body })
  }

  return (
    <section>
      <AdminPageHeader title={t('settings.title')} description={t('settings.description')} />
      <div className="space-y-4">
        <p className="text-sm text-muted-foreground">{t('settings.fallbackHint')}</p>
        <article className="space-y-3 rounded-xl border border-border bg-card p-5">
          {entries.length === 0 ? <p className="text-sm text-muted-foreground">{t('settings.empty')}</p> : null}
          <ul className="space-y-2">
            {entries.map((entry) => (
              <li key={entry.id} className="flex items-stretch gap-2">
                <button
                  type="button"
                  className="flex flex-1 items-center justify-between rounded-lg border border-border px-3 py-2 text-left text-sm hover:bg-card-muted"
                  onClick={() => setDraft(draftFromEntry(entry))}
                >
                  <span>
                    <span className="font-medium text-foreground">
                      {(entry.mode ?? 'KEY') === 'CLI' ? modelIdFromSpec(entry.modelSpec) : entry.displayName}
                    </span>
                    {entry.alias ? (
                      <span className="ml-2 rounded bg-card-muted px-1.5 py-0.5 text-xs text-muted-foreground">
                        {entry.alias}
                      </span>
                    ) : null}
                    <span className="ml-2 text-muted-foreground">
                      {(entry.mode ?? 'KEY') === 'CLI' ? modelIdFromSpec(entry.modelSpec) : entry.modelSpec}
                    </span>
                  </span>
                  <span className="flex flex-wrap items-center gap-1">
                    {entry.defaultEntry ? (
                      <span className="rounded bg-primary px-1.5 py-0.5 text-xs text-primary-foreground">
                        {(entry.mode ?? 'KEY') === 'CLI'
                          ? t('settings.defaultBadgeCli')
                          : t('settings.defaultBadgeKey')}
                      </span>
                    ) : null}
                    <span className="rounded bg-card-muted px-1.5 py-0.5 text-xs text-muted-foreground">
                      {(entry.mode ?? 'KEY') === 'CLI' ? t('settings.modeCli') : t('settings.modeKey')}
                    </span>
                    {!entry.enabled ? (
                      <span className="rounded bg-danger-muted px-1.5 py-0.5 text-xs text-danger-emphasis">
                        {t('settings.disabledBadge')}
                      </span>
                    ) : null}
                    {entry.apiKeyConfigured ? (
                      <span className="rounded bg-card-muted px-1.5 py-0.5 text-xs text-muted-foreground">
                        {entry.apiKeyHint}
                      </span>
                    ) : null}
                  </span>
                </button>
                {canUpdate && entry.enabled && !entry.defaultEntry ? (
                  <button
                    type="button"
                    className="shrink-0 rounded-lg border border-border px-3 py-2 text-sm disabled:opacity-50"
                    disabled={setDefaultMutation.isPending}
                    onClick={() => setDefaultMutation.mutate(entry)}
                  >
                    {t('settings.setDefault')}
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
          <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
            <button
              type="button"
              className="rounded-lg border border-border px-3 py-2 text-sm"
              onClick={() => setDraft(emptyDraft(entries.length === 0))}
            >
              {t('settings.add')}
            </button>
          </PermissionGate>
        </article>

        <GatewayAccessKeysCard />

        <article className="space-y-3 rounded-xl border border-border bg-card p-5">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-sm font-semibold text-foreground">{t('settings.chainTitle')}</h3>
            <select
              className="rounded-lg border border-border px-2 py-1 text-sm"
              value={chainScope}
              onChange={(event) => setChainScope(event.target.value)}
            >
              <option value="">{t('settings.agentPlatform')}</option>
              {(sitesQuery.data?.records ?? []).map((site) => (
                <option key={site.id} value={site.id}>
                  {site.agentName}
                </option>
              ))}
            </select>
          </div>
          <p className="text-sm text-muted-foreground">{t('settings.chainHint')}</p>
          <div className="space-y-4">
            <ModeChainSection mode="KEY" chain={keyChain} />
            <ModeChainSection mode="CLI" chain={cliChain} />
          </div>
        </article>

        {draft ? (
          <form
            className="space-y-4 rounded-xl border border-border bg-card p-5"
            onSubmit={(event) => {
              event.preventDefault()
              submitDraft()
            }}
          >
            <label className="block">
              <span className="text-sm font-medium text-foreground">{t('settings.name')}</span>
              <input
                className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                value={draft.displayName}
                disabled={!canUpdate}
                onChange={(event) => setDraft({ ...draft, displayName: event.target.value })}
              />
            </label>
            {draft.mode === 'CLI' ? (
              cliReady ? (
                <>
                  <label className="block">
                    <span className="text-sm font-medium text-foreground">{t('settings.cliModel')}</span>
                    <select
                      className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                      value={cliModelId}
                      disabled={!canUpdate}
                      onChange={(event) => setDraft({ ...draft, modelSpec: event.target.value })}
                    >
                      {cliAccounts.map((account) => {
                        const ids = cliModels(account)
                        if (ids.length === 0) return null
                        const label =
                          account.provider === 'codex'
                            ? t('proxyServices.providerCodex')
                            : account.provider === 'claude'
                              ? t('proxyServices.providerClaude')
                              : account.provider === 'antigravity'
                                ? t('proxyServices.providerGemini')
                                : account.provider === 'meta'
                                  ? t('proxyServices.providerMuse')
                                  : account.provider
                        return (
                          <optgroup key={account.name} label={label}>
                            {ids.map((id) => (
                              <option key={`${account.name}-${id}`} value={id}>
                                {id}
                              </option>
                            ))}
                          </optgroup>
                        )
                      })}
                    </select>
                    <span className="mt-1 block text-xs text-muted-foreground">{t('settings.cliModelHint')}</span>
                  </label>
                  <label className="block">
                    <span className="text-sm font-medium text-foreground">{t('settings.cliKey')}</span>
                    <select
                      className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                      value={draft.apiKey}
                      disabled={!canUpdate}
                      onChange={(event) => setDraft({ ...draft, apiKey: event.target.value })}
                    >
                      <option value="">{draft.id == null ? t('settings.cliKeyPick') : t('settings.apiKeyKeep')}</option>
                      {clientKeys.map((key) => (
                        <option key={key} value={key}>
                          {key}
                        </option>
                      ))}
                    </select>
                    <span className="mt-1 block text-xs text-muted-foreground">{t('settings.cliKeyHint')}</span>
                  </label>
                  <p className="text-xs text-muted-foreground">{t('settings.cliAddressFixed', { baseUrl: cliBaseUrl })}</p>
                </>
              ) : (
                <p className="text-sm text-muted-foreground">{t('settings.cliNeedProxy')}</p>
              )
            ) : (
              <>
                <label className="block">
                  <span className="text-sm font-medium text-foreground">{t('settings.spec')}</span>
                  <input
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                    value={draft.modelSpec}
                    disabled={!canUpdate}
                    placeholder={t('settings.specHint')}
                    onChange={(event) => setDraft({ ...draft, modelSpec: event.target.value })}
                  />
                </label>
                <label className="block">
                  <span className="text-sm font-medium text-foreground">{t('settings.apiKey')}</span>
                  <input
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                    type="password"
                    autoComplete="new-password"
                    value={draft.apiKey}
                    disabled={!canUpdate}
                    placeholder={draft.id == null ? '' : t('settings.apiKeyKeep')}
                    onChange={(event) => setDraft({ ...draft, apiKey: event.target.value })}
                  />
                  {draft.apiKeyHint ? (
                    <span className="mt-1 block text-xs text-muted-foreground">
                      {t('settings.apiKeyHint', { hint: draft.apiKeyHint })}
                    </span>
                  ) : null}
                </label>
                <label className="block">
                  <span className="text-sm font-medium text-foreground">{t('settings.embeddingSpec')}</span>
                  <input
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                    value={draft.embeddingSpec}
                    disabled={!canUpdate}
                    placeholder={t('settings.embeddingSpecHint')}
                    onChange={(event) => setDraft({ ...draft, embeddingSpec: event.target.value })}
                  />
                </label>
                <label className="block">
                  <span className="text-sm font-medium text-foreground">{t('settings.baseUrl')}</span>
                  <input
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                    value={draft.baseUrl}
                    disabled={!canUpdate}
                    placeholder={t('settings.baseUrlHint')}
                    onChange={(event) => setDraft({ ...draft, baseUrl: event.target.value })}
                  />
                </label>
              </>
            )}
            <label className="block">
              <span className="text-sm font-medium text-foreground">{t('settings.alias')}</span>
              <input
                className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                value={draft.alias}
                disabled={!canUpdate}
                onChange={(event) => setDraft({ ...draft, alias: event.target.value })}
              />
              <span className="mt-1 block text-xs text-muted-foreground">{t('settings.aliasHint')}</span>
              {aliasInvalid ? (
                <span className="mt-1 block text-xs text-danger-emphasis">{t('settings.aliasInvalid')}</span>
              ) : null}
            </label>
            <label className="block">
              <span className="text-sm font-medium text-foreground">{t('settings.site')}</span>
              <select
                className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                value={draft.agentId}
                disabled={!canUpdate}
                onChange={(event) => setDraft({ ...draft, agentId: event.target.value })}
              >
                <option value="">{t('settings.agentPlatform')}</option>
                {(sitesQuery.data?.records ?? []).map((site) => (
                  <option key={site.id} value={site.id}>
                    {site.agentName}
                  </option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="text-sm font-medium text-foreground">{t('settings.mode')}</span>
              <select
                className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
                value={draft.mode}
                disabled={!canUpdate}
                onChange={(event) => setDraft({ ...draft, mode: event.target.value })}
              >
                <option value="KEY">{t('settings.modeKey')}</option>
                <option value="CLI">{t('settings.modeCli')}</option>
              </select>
              <span className="mt-1 block text-xs text-muted-foreground">{t('settings.modeHint')}</span>
            </label>
            <label className="flex items-center gap-2 text-sm text-foreground">
              <input
                type="checkbox"
                className="admin-check"
                checked={draft.enabled}
                disabled={!canUpdate}
                onChange={(event) => setDraft({ ...draft, enabled: event.target.checked })}
              />
              {t('settings.enabled')}
            </label>
            <label className="flex items-center gap-2 text-sm text-foreground">
              <input
                type="checkbox"
                className="admin-check"
                checked={draft.defaultEntry}
                disabled={!canUpdate}
                onChange={(event) => setDraft({ ...draft, defaultEntry: event.target.checked })}
              />
              {t('settings.defaultEntry')}
            </label>
            <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
              <div className="flex gap-2">
                <button
                  type="submit"
                  disabled={saveMutation.isPending || aliasInvalid}
                  className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
                >
                  {saveMutation.isPending ? t('settings.saving') : t('settings.save')}
                </button>
                {draft.id != null ? (
                  <button
                    type="button"
                    className="rounded-lg border border-danger/35 px-3 py-2 text-sm text-danger-emphasis"
                    disabled={deleteMutation.isPending}
                    onClick={() => {
                      if (draft.id == null) return
                      if (!window.confirm(t('settings.confirmDelete'))) return
                      deleteMutation.mutate(draft.id)
                    }}
                  >
                    {t('settings.remove')}
                  </button>
                ) : null}
              </div>
            </PermissionGate>
          </form>
        ) : null}
      </div>
    </section>
  )
}
