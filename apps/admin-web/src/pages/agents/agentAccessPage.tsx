import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router-dom'
import { toast } from 'sonner'

import { aiApi } from '@/api'
import { adminFieldClass, adminGhostButtonClass, adminPrimaryButtonClass } from '@/components/admin/controls'
import { EmbeddingRegistrationCard } from '@/pages/ai/embeddingRegistrationCard'
import { ROUTES } from '@/router/config/routePaths'
import { useHasPermission } from '@/router/permission'
import type { AiModelEntry, AiModelEntryWriteInput, ProxyServicesPublishedAccount } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'
import { modelIdFromSpec } from '@/utils/modelSpec'

import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

function entryFor(entries: AiModelEntry[], agentId: number, mode: 'CLI' | 'KEY') {
  const enabled = entries.filter((entry) => entry.enabled && (entry.mode ?? 'KEY') === mode)
  return (
    enabled.find((entry) => entry.agentId === agentId && entry.defaultEntry) ??
    enabled.find((entry) => entry.agentId == null && entry.defaultEntry) ??
    null
  )
}

function cliSpec(modelId: string) {
  const trimmed = modelId.trim()
  return trimmed.includes(':') ? trimmed : `openai:${trimmed}`
}

function visibleModelIds(provider: string, modelIds: string[]) {
  if (provider === 'claude') return modelIds
  return modelIds.filter((id) => !id.toLowerCase().startsWith('claude'))
}

function providerLabel(provider: string, t: (key: string) => string) {
  if (provider === 'codex') return t('access.providerCodex')
  if (provider === 'claude') return t('access.providerClaude')
  if (provider === 'antigravity') return t('access.providerGemini')
  if (provider === 'meta') return t('access.providerMuse')
  return provider
}

function accountLabel(name: string, provider: string, t: (key: string) => string) {
  const title = providerLabel(provider, t)
  const email = name
    .replace(/\.json$/i, '')
    .split('-')
    .find((part) => part.includes('@'))
  return email ? `${title} · ${email}` : title
}

export function AgentAccessPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const canRead = useHasPermission(PERMISSION_CODES.aiModelRead)
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)

  const entriesQuery = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
    enabled: canRead,
  })
  const usageQuery = useQuery({
    queryKey: ['ai', 'proxy-services', 'usage'],
    queryFn: () => aiApi.getProxyServicesUsage(),
    enabled: canRead,
  })
  const keysQuery = useQuery({
    queryKey: ['ai', 'proxy-services', 'api-keys'],
    queryFn: () => aiApi.listProxyApiKeys(),
    enabled: canRead,
  })

  if (!canRead) {
    return <p className="text-sm text-admin-muted">{t('access.noPermission')}</p>
  }
  if (entriesQuery.isPending || usageQuery.isPending || keysQuery.isPending) {
    return <p className="text-sm text-admin-muted">{t('access.loading')}</p>
  }
  if (entriesQuery.isError || usageQuery.isError || keysQuery.isError) {
    return <p className="text-sm text-danger-emphasis">{t('access.loadError')}</p>
  }

  const entries = entriesQuery.data ?? []
  const cliEntry = entryFor(entries, site.id, 'CLI')
  const keyEntry = entryFor(entries, site.id, 'KEY')
  const accounts = (usageQuery.data?.accounts ?? []).filter((account) => !account.disabled && !account.unavailable)
  const apiBaseUrl = usageQuery.data?.apiBaseUrl ?? ''
  const clientKeys = keysQuery.data?.apiKeys ?? []

  return (
    <section className="w-full space-y-3">
      <div className="grid gap-3 xl:grid-cols-2">
        <CliAccessForm
          key={`cli-${cliEntry?.id ?? 'new'}-${cliEntry?.version ?? 0}`}
          agentId={site.id}
          entry={cliEntry}
          accounts={accounts}
          apiBaseUrl={apiBaseUrl}
          clientKeys={clientKeys}
          canUpdate={canUpdate}
        />
        <KeyAccessForm
          key={`key-${keyEntry?.id ?? 'new'}-${keyEntry?.version ?? 0}-${entries.length}`}
          agentId={site.id}
          entry={keyEntry}
          entries={entries}
          canUpdate={canUpdate}
        />
      </div>
      <EmbeddingRegistrationCard agentId={site.id} />
    </section>
  )
}

function ScopeNote({ entry, agentId }: { entry: AiModelEntry | null; agentId: number }) {
  const { t } = useTranslation('sites')
  if (entry == null) return <p className="text-xs text-admin-muted">{t('access.none')}</p>
  if (entry.agentId === agentId) return <p className="text-xs text-admin-muted">{t('access.usingAgent')}</p>
  return <p className="text-xs text-admin-muted">{t('access.usingGlobal')}</p>
}

function CliAccessForm({
  agentId,
  entry,
  accounts,
  apiBaseUrl,
  clientKeys,
  canUpdate,
}: {
  agentId: number
  entry: AiModelEntry | null
  accounts: ProxyServicesPublishedAccount[]
  apiBaseUrl: string
  clientKeys: string[]
  canUpdate: boolean
}) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const owned = entry != null && entry.agentId === agentId
  const initialModel = entry ? modelIdFromSpec(entry.modelSpec) : ''
  const initialAccount =
    accounts.find((account) => visibleModelIds(account.provider, account.modelIds).includes(initialModel))?.name ??
    accounts[0]?.name ??
    ''
  const [accountName, setAccountName] = useState(initialAccount)
  const account = accounts.find((item) => item.name === accountName) ?? accounts[0]
  const modelIds = account ? visibleModelIds(account.provider, account.modelIds) : []
  const [modelId, setModelId] = useState(modelIds.includes(initialModel) ? initialModel : (modelIds[0] ?? ''))
  const [clientKey, setClientKey] = useState('')
  const [changingKey, setChangingKey] = useState(false)
  const hasSavedKey = entry != null && entry.agentId === agentId && entry.apiKeyConfigured
  const showKeyList = !hasSavedKey || changingKey
  const ready = Boolean(apiBaseUrl) && accounts.length > 0 && modelIds.length > 0 && (clientKeys.length > 0 || owned)

  const saveMutation = useMutation({
    mutationFn: (body: AiModelEntryWriteInput) =>
      owned && entry ? aiApi.updateModelEntry(entry.id, body) : aiApi.createModelEntry(body),
    onSuccess: () => {
      toast.success(t('access.saved'))
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  function save() {
    if (!canUpdate || !modelId.trim() || !apiBaseUrl) return
    if (!owned && !clientKey) {
      toast.warning(t('access.pickKey'))
      return
    }
    const body: AiModelEntryWriteInput = {
      displayName: modelId.trim(),
      modelSpec: cliSpec(modelId),
      baseUrl: apiBaseUrl,
      agentId,
      embeddingSpec: null,
      enabled: true,
      defaultEntry: true,
      mode: 'CLI',
      alias: owned && entry ? entry.alias : null,
      version: owned && entry ? entry.version : null,
    }
    if (clientKey) body.apiKey = clientKey
    saveMutation.mutate(body)
  }

  return (
    <article className="flex h-full flex-col rounded-xl border border-border bg-card p-5">
      <div className="mb-3 flex items-start justify-between gap-3 border-b border-border pb-3">
        <div>
          <h3 className="text-sm font-semibold text-foreground">{t('access.cliTitle')}</h3>
          <p className="mt-1 text-xs leading-5 text-muted-foreground">{t('access.cliDescription')}</p>
        </div>
        <span className="shrink-0 rounded-md bg-info-muted px-2 py-0.5 text-[11px] font-medium text-info-emphasis">
          CLI
        </span>
      </div>
      <div className="mb-3">
        <ScopeNote entry={entry} agentId={agentId} />
      </div>
      {ready && account ? (
        <div className="mt-4 space-y-4">
          <label className="block text-sm text-admin">
            {t('access.account')}
            <select
              className={`${adminFieldClass} mt-1`}
              value={account.name}
              disabled={!canUpdate}
              onChange={(event) => {
                const next = accounts.find((item) => item.name === event.target.value)
                const nextModels = next ? visibleModelIds(next.provider, next.modelIds) : []
                setAccountName(event.target.value)
                setModelId(nextModels[0] ?? '')
              }}
            >
              {accounts.map((item) => (
                <option key={item.name} value={item.name}>
                  {accountLabel(item.name, item.provider, t)}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm text-admin">
            {t('access.model')}
            <select className={`${adminFieldClass} mt-1`} value={modelId} disabled={!canUpdate} onChange={(event) => setModelId(event.target.value)}>
              {modelIds.map((id) => (
                <option key={id} value={id}>
                  {id}
                </option>
              ))}
            </select>
          </label>
          <div>
            <label htmlFor="access-client-key" className="block text-sm text-admin">
              {t('access.clientKey')}
            </label>
            {showKeyList ? (
              <select
                id="access-client-key"
                className={`${adminFieldClass} mt-1`}
                value={clientKey}
                disabled={!canUpdate}
                onChange={(event) => setClientKey(event.target.value)}
              >
                <option value="">{hasSavedKey ? t('access.clientKeyKeep') : t('access.pickKey')}</option>
                {clientKeys.map((key) => (
                  <option key={key} value={key}>
                    {key}
                  </option>
                ))}
              </select>
            ) : (
              <div className={`${adminFieldClass} mt-1 flex items-center justify-between gap-2`}>
                <span>{t('access.clientKeyKeep')}</span>
                {entry != null ? <span className="font-mono text-xs text-admin-muted">{entry.apiKeyHint}</span> : null}
              </div>
            )}
            {hasSavedKey && !changingKey && canUpdate ? (
              <button type="button" className={`${adminGhostButtonClass} mt-2`} onClick={() => setChangingKey(true)}>
                {t('access.clientKeyChange')}
              </button>
            ) : null}
            <span className="mt-1 block text-xs text-admin-muted">{t('access.keyRelation', { baseUrl: apiBaseUrl })}</span>
          </div>
        </div>
      ) : (
        <p className="mt-4 text-sm text-admin-muted">
          {t('access.needProxy')}{' '}
          <Link to={ROUTES.apps.api} className="font-medium text-admin underline-offset-2 hover:underline">
            {t('access.openApi')}
          </Link>
        </p>
      )}
      {canUpdate && ready ? (
        <div className="mt-4 flex justify-end">
          <button type="button" className={adminPrimaryButtonClass} disabled={saveMutation.isPending} onClick={save}>
            {saveMutation.isPending ? t('access.saving') : t('access.save')}
          </button>
        </div>
      ) : null}
    </article>
  )
}

function keyBody(entry: AiModelEntry, agentId: number | null, defaultEntry: boolean): AiModelEntryWriteInput {
  return {
    displayName: entry.displayName,
    modelSpec: entry.modelSpec,
    baseUrl: entry.baseUrl,
    agentId,
    embeddingSpec: entry.embeddingSpec,
    enabled: defaultEntry ? true : entry.enabled,
    defaultEntry,
    mode: 'KEY',
    alias: entry.alias,
    version: entry.version,
  }
}

function KeyAccessForm({
  agentId,
  entry,
  entries,
  canUpdate,
}: {
  agentId: number
  entry: AiModelEntry | null
  entries: AiModelEntry[]
  canUpdate: boolean
}) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const pool = entries.filter((item) => (item.mode ?? 'KEY') === 'KEY')
  const [selectedId, setSelectedId] = useState(entry ? String(entry.id) : '')
  const selected = pool.find((item) => String(item.id) === selectedId) ?? null

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!selected) return
      if (entry && entry.agentId === agentId && entry.id !== selected.id) {
        await aiApi.updateModelEntry(entry.id, keyBody(entry, null, false))
      }
      const already = selected.agentId === agentId && selected.defaultEntry && selected.enabled
      if (!already) {
        await aiApi.updateModelEntry(selected.id, keyBody(selected, agentId, true))
      }
    },
    onSuccess: () => {
      toast.success(t('access.saved'))
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  function save() {
    if (!canUpdate || !selected) {
      toast.warning(t('access.pickSavedKey'))
      return
    }
    saveMutation.mutate()
  }

  return (
    <article className="flex h-full flex-col rounded-xl border border-border bg-card p-5">
      <div className="mb-3 flex items-start justify-between gap-3 border-b border-border pb-3">
        <div>
          <h3 className="text-sm font-semibold text-foreground">{t('access.keyTitle')}</h3>
          <p className="mt-1 text-xs leading-5 text-muted-foreground">{t('access.keyDescription')}</p>
        </div>
        <span className="shrink-0 rounded-md bg-primary/10 px-2 py-0.5 text-[11px] font-medium text-primary">
          KEY
        </span>
      </div>
      <div className="mb-3">
        <ScopeNote entry={entry} agentId={agentId} />
      </div>
      {pool.length > 0 ? (
        <label className="mt-4 block text-sm text-admin">
          {t('access.keyTitle')}
          <select
            className={`${adminFieldClass} mt-1`}
            value={selectedId}
            disabled={!canUpdate}
            onChange={(event) => setSelectedId(event.target.value)}
          >
            <option value="">{t('access.pickSavedKey')}</option>
            {pool.map((item) => (
              <option key={item.id} value={item.id}>
                {item.displayName} · {item.modelSpec}
                {item.apiKeyConfigured ? ` · ${item.apiKeyHint}` : ''}
                {item.agentId != null && item.agentId !== agentId ? ` · ${t('access.keyBoundOther')}` : ''}
              </option>
            ))}
          </select>
          {selected ? (
            <span className="mt-1 block font-mono text-xs text-admin-muted">
              {selected.displayName} · {selected.modelSpec}
              {selected.apiKeyConfigured ? ` · ${selected.apiKeyHint}` : ''}
            </span>
          ) : null}
        </label>
      ) : (
        <p className="mt-4 text-sm text-admin-muted">
          {t('access.noProviderKeys')}{' '}
          <Link to={ROUTES.apps.api} className="font-medium text-admin underline-offset-2 hover:underline">
            {t('access.openApi')}
          </Link>
        </p>
      )}
      {canUpdate && pool.length > 0 ? (
        <div className="mt-4 flex justify-end">
          <button type="button" className={adminPrimaryButtonClass} disabled={saveMutation.isPending} onClick={save}>
            {saveMutation.isPending ? t('access.saving') : t('access.save')}
          </button>
        </div>
      ) : null}
    </article>
  )
}
