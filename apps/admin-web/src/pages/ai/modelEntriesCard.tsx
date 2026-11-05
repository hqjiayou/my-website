import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { StatusTag } from '@/components/admin'
import {
  adminDangerGhostButtonClass,
  adminFieldClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type { AiModelEntry, AiModelEntryWriteInput } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'
import { modelIdFromSpec } from '@/utils/modelSpec'

type Draft = {
  id: number | null
  displayName: string
  modelSpec: string
  apiKey: string
  baseUrl: string
  sourceAgentId: number | null
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
    sourceAgentId: null,
    embeddingSpec: '',
    enabled: true,
    defaultEntry: makeDefault,
    mode: 'KEY',
    alias: '',
    version: null,
    apiKeyHint: null,
  }
}

function draftFromEntry(entry: AiModelEntry): Draft {
  return {
    id: entry.id,
    displayName: entry.displayName,
    modelSpec: entry.modelSpec,
    apiKey: '',
    baseUrl: entry.baseUrl ?? '',
    sourceAgentId: entry.sourceAgentId,
    embeddingSpec: entry.embeddingSpec ?? '',
    enabled: entry.enabled,
    defaultEntry: entry.defaultEntry,
    mode: entry.mode ?? 'KEY',
    alias: entry.alias ?? '',
    version: entry.version,
    apiKeyHint: entry.apiKeyHint,
  }
}

/** 家族图标砖：按 modelSpec 协议前缀映射色板 token，字母取家族名首字母。前缀未知时归为「其他」。 */
function familyTile(modelSpec: string): { letter: string; className: string } {
  const prefix = modelSpec.split(':')[0]?.trim().toLowerCase() ?? ''
  if (prefix === 'google') return { letter: 'G', className: 'bg-[rgb(var(--info)/.12)] text-info' }
  if (prefix === 'openai') return { letter: 'G', className: 'bg-[rgb(var(--chart-2)/.12)] text-chart-2' }
  if (prefix === 'anthropic') return { letter: 'C', className: 'bg-[rgb(var(--chart-3)/.12)] text-chart-3' }
  if (prefix === 'azure') return { letter: 'A', className: 'bg-[rgb(var(--chart-1)/.12)] text-chart-1' }
  return { letter: 'M', className: 'bg-admin-surface-muted text-admin-muted' }
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

/**
 * 模型入口管理（Issue #213 UoW-C 从 AI 设置页拆出）：入口列表＋编辑表单本体，
 * 逻辑与文案（settings.*）与拆分前逐字一致，仅宿主由设置页改为独立页。
 */
export function ModelEntriesCard() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [draft, setDraft] = useState<Draft | null>(null)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
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

  if (isPending) return <p className="text-sm text-admin-muted">{t('loading')}</p>
  if (isError) {
    return (
      <div className="rounded-lg border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis">
        <p>{error instanceof Error ? error.message : t('error')}</p>
        <button type="button" className="mt-2 underline" onClick={() => void refetch()}>
          {t('retry')}
        </button>
      </div>
    )
  }

  const entries = data ?? []
  const aliasTrimmed = draft?.alias.trim() ?? ''
  const aliasInvalid = aliasTrimmed !== '' && !/^[a-z0-9][a-z0-9_-]{1,63}$/.test(aliasTrimmed.toLowerCase())
  const cliAccounts = (usageQuery.data?.accounts ?? []).filter(
    (account) => !account.disabled && !account.unavailable,
  )
  const cliBaseUrl = usageQuery.data?.apiBaseUrl ?? ''
  const clientKeys = keysQuery.data?.apiKeys ?? []
  const cliModelId = modelIdFromSpec(draft?.modelSpec ?? '')
  const cliReady = Boolean(cliBaseUrl) && cliAccounts.some((account) => cliModels(account).length > 0) && clientKeys.length > 0
  const draftTitle =
    draft == null
      ? ''
      : draft.id == null
        ? t('settings.add')
        : draft.mode === 'CLI'
          ? modelIdFromSpec(draft.modelSpec) || t('settings.name')
          : draft.displayName || t('settings.name')

  function submitDraft() {
    if (!draft || !canUpdate) return
    const body: AiModelEntryWriteInput = {
      displayName:
        draft.mode === 'CLI' ? modelIdFromSpec(draft.modelSpec).trim() || draft.displayName.trim() : draft.displayName.trim(),
      modelSpec: draft.mode === 'CLI' ? cliModelSpec(draft.modelSpec) : draft.modelSpec.trim(),
      baseUrl: draft.mode === 'CLI' ? usageQuery.data?.apiBaseUrl || draft.baseUrl.trim() || null : draft.baseUrl.trim() || null,
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
    <>
      <article className="space-y-3 rounded-xl border border-admin bg-admin-surface p-5">
        {entries.length === 0 ? <p className="text-sm text-admin-muted">{t('settings.empty')}</p> : null}
        <ul className="space-y-2">
          {entries.map((entry) => {
            const tile = familyTile(entry.modelSpec)
            const entryMode = entry.mode ?? 'KEY'
            return (
              <li key={entry.id} className="flex items-stretch gap-2">
                <button
                  type="button"
                  className="flex flex-1 items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition hover:bg-admin-surface-muted admin-focus-ring"
                  onClick={() => setDraft(draftFromEntry(entry))}
                >
                  <span className={cn('flex h-9 w-9 shrink-0 items-center justify-center rounded-admin-md text-sm font-bold', tile.className)}>
                    {tile.letter}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-2">
                      <span className="font-medium text-admin">
                        {entryMode === 'CLI' ? modelIdFromSpec(entry.modelSpec) : entry.displayName}
                      </span>
                      {entry.alias ? (
                        <span className="rounded-md bg-admin-surface-muted px-1.5 py-0.5 font-mono text-xs text-admin-muted">
                          {entry.alias}
                        </span>
                      ) : null}
                    </span>
                    <span className="mt-0.5 block truncate font-mono text-xs text-admin-muted">
                      {entryMode === 'CLI' ? modelIdFromSpec(entry.modelSpec) : entry.modelSpec}
                      {entryMode !== 'CLI' && entry.embeddingSpec ? ` · ${entry.embeddingSpec}` : ''}
                    </span>
                  </span>
                  <span className="flex flex-wrap items-center justify-end gap-1.5">
                    {entry.defaultEntry ? (
                      <span className="rounded-full bg-primary px-2 py-0.5 text-xs font-medium text-primary-foreground">
                        {entryMode === 'CLI' ? t('settings.defaultBadgeCli') : t('settings.defaultBadgeKey')}
                      </span>
                    ) : null}
                    <span
                      className={cn(
                        'rounded-full px-2 py-0.5 text-xs font-medium',
                        entryMode === 'CLI' ? 'bg-[rgb(var(--chart-4)/.12)] text-chart-4' : 'bg-[rgb(var(--chart-1)/.12)] text-chart-1',
                      )}
                    >
                      {entryMode === 'CLI' ? t('settings.modeCli') : t('settings.modeKey')}
                    </span>
                    {!entry.enabled ? <StatusTag status="DISABLED" tone="danger" label={t('settings.disabledBadge')} /> : null}
                    {entry.apiKeyConfigured ? (
                      <span className="rounded-md bg-admin-surface-muted px-1.5 py-0.5 font-mono text-xs text-admin-muted">
                        {entry.apiKeyHint}
                      </span>
                    ) : null}
                  </span>
                </button>
                {canUpdate && entry.enabled && !entry.defaultEntry ? (
                  <button
                    type="button"
                    className={cn(adminGhostButtonClass, 'shrink-0')}
                    disabled={setDefaultMutation.isPending}
                    onClick={() => setDefaultMutation.mutate(entry)}
                  >
                    {t('settings.setDefault')}
                  </button>
                ) : null}
              </li>
            )
          })}
        </ul>
        <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
          <button
            type="button"
            className={adminPrimaryButtonClass}
            onClick={() => setDraft(emptyDraft(entries.length === 0))}
          >
            {t('settings.add')}
          </button>
        </PermissionGate>
      </article>

      {draft ? (
        <form
          className="space-y-4 rounded-xl border border-admin bg-admin-surface p-5"
          onSubmit={(event) => {
            event.preventDefault()
            submitDraft()
          }}
        >
          <h3 className="text-sm font-semibold text-admin">{draftTitle}</h3>
          <label className="block text-sm text-admin">
            {t('settings.name')}
            <input
              className={`${adminFieldClass} mt-1`}
              value={draft.displayName}
              disabled={!canUpdate}
              onChange={(event) => setDraft({ ...draft, displayName: event.target.value })}
            />
          </label>
          {draft.mode === 'CLI' ? (
            cliReady ? (
              <>
                <label className="block text-sm text-admin">
                  {t('settings.cliModel')}
                  <select
                    className={`${adminFieldClass} mt-1`}
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
                  <span className="mt-1 block text-xs text-admin-muted">{t('settings.cliModelHint')}</span>
                </label>
                <label className="block text-sm text-admin">
                  {t('settings.cliKey')}
                  <select
                    className={`${adminFieldClass} mt-1`}
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
                  <span className="mt-1 block text-xs text-admin-muted">{t('settings.cliKeyHint')}</span>
                </label>
                <p className="rounded-lg border border-success/35 bg-success-muted px-3 py-2 text-xs text-success-emphasis">
                  {t('settings.cliAddressFixed', { baseUrl: cliBaseUrl })}
                </p>
              </>
            ) : (
              <p className="rounded-lg border border-warning/35 bg-warning-muted px-3 py-2 text-sm text-warning-emphasis">
                {t('settings.cliNeedProxy')}
              </p>
            )
          ) : (
            <>
              <label className="block text-sm text-admin">
                {t('settings.spec')}
                <input
                  className={`${adminFieldClass} mt-1`}
                  value={draft.modelSpec}
                  disabled={!canUpdate}
                  placeholder={t('settings.specHint')}
                  onChange={(event) => setDraft({ ...draft, modelSpec: event.target.value })}
                />
              </label>
              <label className="block text-sm text-admin">
                {t('settings.apiKey')}
                <input
                  className={`${adminFieldClass} mt-1`}
                  type="password"
                  autoComplete="new-password"
                  value={draft.apiKey}
                  disabled={!canUpdate}
                  placeholder={draft.id == null ? '' : t('settings.apiKeyKeep')}
                  onChange={(event) => setDraft({ ...draft, apiKey: event.target.value })}
                />
                {draft.apiKeyHint ? (
                  <span className="mt-1 block text-xs text-admin-muted">
                    {t('settings.apiKeyHint', { hint: draft.apiKeyHint })}
                  </span>
                ) : null}
              </label>
              <label className="block text-sm text-admin">
                {t('settings.embeddingSpec')}
                <input
                  className={`${adminFieldClass} mt-1`}
                  value={draft.embeddingSpec}
                  disabled={!canUpdate}
                  placeholder={t('settings.embeddingSpecHint')}
                  onChange={(event) => setDraft({ ...draft, embeddingSpec: event.target.value })}
                />
              </label>
              <label className="block text-sm text-admin">
                {t('settings.baseUrl')}
                <input
                  className={`${adminFieldClass} mt-1`}
                  value={draft.baseUrl}
                  disabled={!canUpdate}
                  placeholder={t('settings.baseUrlHint')}
                  onChange={(event) => setDraft({ ...draft, baseUrl: event.target.value })}
                />
              </label>
            </>
          )}
          <label className="block text-sm text-admin">
            {t('settings.alias')}
            <input
              className={`${adminFieldClass} mt-1`}
              value={draft.alias}
              disabled={!canUpdate}
              onChange={(event) => setDraft({ ...draft, alias: event.target.value })}
            />
            <span className="mt-1 block text-xs text-admin-muted">{t('settings.aliasHint')}</span>
            {aliasInvalid ? (
              <span className="mt-1 block text-xs text-danger-emphasis">{t('settings.aliasInvalid')}</span>
            ) : null}
          </label>
          {draft.sourceAgentId != null ? (
            <p className="text-sm text-admin-muted">
              {t('settings.entryProvenanceNote', { id: draft.sourceAgentId })}
            </p>
          ) : null}
          <label className="block text-sm text-admin">
            {t('settings.mode')}
            <select
              className={`${adminFieldClass} mt-1`}
              value={draft.mode}
              disabled={!canUpdate}
              onChange={(event) => setDraft({ ...draft, mode: event.target.value })}
            >
              <option value="KEY">{t('settings.modeKey')}</option>
              <option value="CLI">{t('settings.modeCli')}</option>
            </select>
            <span className="mt-1 block text-xs text-admin-muted">{t('settings.modeHint')}</span>
          </label>
          <label className="flex items-center gap-2 text-sm text-admin">
            <input
              type="checkbox"
              className="admin-check"
              checked={draft.enabled}
              disabled={!canUpdate}
              onChange={(event) => setDraft({ ...draft, enabled: event.target.checked })}
            />
            {t('settings.enabled')}
          </label>
          <label className="flex items-center gap-2 text-sm text-admin">
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
            <div className={adminFormActionsClass}>
              {draft.id != null ? (
                <button
                  type="button"
                  className={adminDangerGhostButtonClass}
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
              <button
                type="submit"
                disabled={saveMutation.isPending || aliasInvalid}
                className={adminPrimaryButtonClass}
              >
                {saveMutation.isPending ? t('settings.saving') : t('settings.save')}
              </button>
            </div>
          </PermissionGate>
        </form>
      ) : null}
    </>
  )
}
