import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Zap } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { FilterTabs, FormDialog, FormField, Spinner } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminDangerGhostButtonClass,
  adminFieldClass,
  adminFormActionsClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type {
  AiModelEntry,
  AiModelEntryWriteInput,
  ProxyLoginProvider,
  ProxyServicesAccount,
  ProxyServicesPublishedAccount,
} from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'
import { modelIdFromSpec } from '@/utils/modelSpec'

import { ModelEntriesCliPane } from './modelEntriesCliPane'
import { ModelEntriesKeyPane } from './modelEntriesKeyPane'
import {
  cliModelSpec,
  draftFromEntry,
  emptyDraft,
  type ModelEntryDraft,
  type ModelEntryMode,
} from './modelEntriesShared'
import { ProxyLoginFlow } from './proxyServicesPage'
import { shownModelIds } from './proxyServicesShared'

const EMPTY_ENTRIES: AiModelEntry[] = []
const EMPTY_ACCOUNTS: ProxyServicesAccount[] = []
const EMPTY_PUBLISHED: ProxyServicesPublishedAccount[] = []

type ModelEntriesCardProps = {
  mode: ModelEntryMode
  onModeChange: (mode: ModelEntryMode) => void
}

/**
 * 模型入口编排卡：共用 listModelEntries；CLI/KEY 由 URL mode 驱动；
 * 表单与同步留在此卡，列表渲染下沉到各 pane。
 */
export function ModelEntriesCard({ mode, onModeChange }: ModelEntriesCardProps) {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [draft, setDraft] = useState<ModelEntryDraft | null>(null)
  const [formTouched, setFormTouched] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<AiModelEntry | null>(null)
  const [loginOpen, setLoginOpen] = useState(false)
  const [loginProvider, setLoginProvider] = useState<ProxyLoginProvider | null>(null)

  const syncRan = useRef(false)
  useEffect(() => {
    if (syncRan.current) return
    syncRan.current = true
    aiApi
      .syncCliModelEntries()
      .then((report) => {
        void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
        if (!report.proxyReady) {
          toast.info(t('modelManagement.syncProxyUnavailable'))
        } else if (
          report.created + report.reactivated + report.deactivated > 0 ||
          report.skippedNoClientKey > 0
        ) {
          toast.success(
            t('modelManagement.syncSummary', {
              created: report.created,
              reactivated: report.reactivated,
              deactivated: report.deactivated,
              skipped: report.skippedNoClientKey,
            }),
          )
        }
      })
      .catch(() => {
        toast.error(t('modelManagement.syncFailed'))
      })
  }, [queryClient, t])

  const entriesQuery = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
  })
  const statusQuery = useQuery({
    queryKey: ['admin', 'proxy-services'],
    queryFn: () => aiApi.getProxyServices(),
  })
  const usageQuery = useQuery({
    queryKey: ['admin', 'proxy-services', 'usage'],
    queryFn: () => aiApi.getProxyServicesUsage(),
  })
  const keysQuery = useQuery({
    queryKey: ['admin', 'proxy-services', 'api-keys'],
    queryFn: () => aiApi.listProxyApiKeys(),
    enabled: draft?.mode === 'CLI',
  })

  const saveMutation = useMutation({
    mutationFn: (input: { id: number | null; body: AiModelEntryWriteInput }) =>
      input.id == null ? aiApi.createModelEntry(input.body) : aiApi.updateModelEntry(input.id, input.body),
    onSuccess: (_entry, input) => {
      toast.success(t('settings.saved'))
      setDraft(null)
      setFormTouched(false)
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
      if (input.id == null) {
        onModeChange('KEY')
      }
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
      setConfirmingDelete(false)
      setDeleteTarget(null)
      setDraft(null)
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (deleteError: Error) => {
      toast.error(deleteError.message)
    },
  })

  const entries = entriesQuery.data ?? EMPTY_ENTRIES
  const keyEntries = useMemo(
    () => entries.filter((entry) => (entry.mode ?? 'KEY') !== 'CLI'),
    [entries],
  )
  const cliEntries = useMemo(
    () => entries.filter((entry) => (entry.mode ?? 'KEY') === 'CLI'),
    [entries],
  )
  const accounts = statusQuery.data?.accounts ?? EMPTY_ACCOUNTS
  const usageAccounts = usageQuery.data?.accounts ?? EMPTY_PUBLISHED

  const aliasTrimmed = draft?.alias.trim() ?? ''
  const aliasInvalid = aliasTrimmed !== '' && !/^[a-z0-9][a-z0-9_-]{1,63}$/.test(aliasTrimmed.toLowerCase())
  const nameInvalid = formTouched && draft != null && draft.mode !== 'CLI' && draft.displayName.trim() === ''
  const specInvalid = formTouched && draft != null && draft.mode !== 'CLI' && draft.modelSpec.trim() === ''
  const draftTitle =
    draft == null
      ? ''
      : draft.id == null
        ? t('settings.add')
        : draft.mode === 'CLI'
          ? modelIdFromSpec(draft.modelSpec) || t('settings.name')
          : draft.displayName || t('settings.name')

  const cliEditAccounts = (usageQuery.data?.accounts ?? []).filter(
    (account) => !account.disabled && !account.unavailable,
  )
  const cliBaseUrl = usageQuery.data?.apiBaseUrl ?? ''
  const clientKeys = keysQuery.data?.apiKeys ?? []
  const cliEditLoading = usageQuery.isPending || keysQuery.isPending
  const cliEditError = usageQuery.isError || keysQuery.isError
  const cliReady =
    Boolean(cliBaseUrl) &&
    cliEditAccounts.some((account) => (shownModelIds(account.provider, account.modelIds) ?? []).length > 0) &&
    clientKeys.length > 0
  const cliModelId = modelIdFromSpec(draft?.modelSpec ?? '')

  function openEntryEdit(entry: AiModelEntry) {
    if (entry.derived) {
      toast.info(t('modelManagement.derivedNote'))
      return
    }
    setFormTouched(false)
    setDraft(draftFromEntry(entry))
  }

  function startLogin(provider: ProxyLoginProvider | null) {
    setLoginProvider(provider)
    setLoginOpen(true)
  }

  function submitDraft() {
    if (!draft || !canUpdate) return
    if (draft.mode !== 'CLI' && (draft.displayName.trim() === '' || draft.modelSpec.trim() === '')) {
      setFormTouched(true)
      return
    }
    const body: AiModelEntryWriteInput =
      draft.mode === 'CLI'
        ? {
            displayName: modelIdFromSpec(draft.modelSpec).trim() || draft.displayName.trim(),
            modelSpec: cliModelSpec(draft.modelSpec),
            baseUrl: usageQuery.data?.apiBaseUrl || draft.baseUrl.trim() || null,
            embeddingSpec: null,
            enabled: draft.enabled,
            defaultEntry: draft.defaultEntry,
            mode: draft.mode,
            alias: draft.alias.trim() || null,
            version: draft.version,
          }
        : {
            displayName: draft.displayName.trim(),
            modelSpec: draft.modelSpec.trim(),
            baseUrl: draft.baseUrl.trim() || null,
            embeddingSpec: draft.embeddingSpec.trim() || null,
            enabled: draft.enabled,
            defaultEntry: draft.defaultEntry,
            mode: 'KEY',
            alias: draft.alias.trim() || null,
            version: draft.version,
          }
    if (draft.apiKey.trim()) body.apiKey = draft.apiKey.trim()
    saveMutation.mutate({ id: draft.id, body })
  }

  function refreshAll() {
    void entriesQuery.refetch()
    void statusQuery.refetch()
    void usageQuery.refetch()
  }

  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <FilterTabs
          tabs={[
            { key: 'CLI', label: t('modelManagement.tabCli'), count: cliEntries.length },
            { key: 'KEY', label: t('modelManagement.tabKey'), count: keyEntries.length },
          ]}
          activeKey={mode}
          onChange={(key) => onModeChange(key === 'KEY' ? 'KEY' : 'CLI')}
        />
        <span className="flex-1" />
        <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
          {mode === 'CLI' ? (
            <button type="button" className={adminPrimaryButtonClass} onClick={() => startLogin(null)}>
              <Zap className="h-4 w-4" />
              {t('modelManagement.loginCli')}
            </button>
          ) : (
            <button
              type="button"
              className={adminPrimaryButtonClass}
              onClick={() => {
                setFormTouched(false)
                setDraft(emptyDraft(keyEntries.length === 0))
              }}
            >
              <Plus className="h-4 w-4" />
              {t('modelManagement.addKeyEntry')}
            </button>
          )}
        </PermissionGate>
      </div>

      {mode === 'KEY' ? (
        <ModelEntriesKeyPane
          entries={keyEntries}
          isLoading={entriesQuery.isPending}
          isError={entriesQuery.isError}
          errorMessage={entriesQuery.error instanceof Error ? entriesQuery.error.message : undefined}
          isRefreshing={entriesQuery.isFetching}
          canUpdate={canUpdate}
          setDefaultPending={setDefaultMutation.isPending}
          onRefresh={() => void entriesQuery.refetch()}
          onEdit={openEntryEdit}
          onSetDefault={(entry) => setDefaultMutation.mutate(entry)}
          onDelete={(entry) => {
            setDeleteTarget(entry)
            setConfirmingDelete(true)
          }}
        />
      ) : (
        <ModelEntriesCliPane
          entries={cliEntries}
          accounts={accounts}
          publishedAccounts={usageAccounts}
          statusPending={statusQuery.isPending}
          statusError={statusQuery.isError}
          usagePending={usageQuery.isPending}
          usageError={usageQuery.isError}
          isLoading={entriesQuery.isPending}
          isError={entriesQuery.isError}
          errorMessage={entriesQuery.error instanceof Error ? entriesQuery.error.message : undefined}
          isRefreshing={entriesQuery.isFetching || statusQuery.isFetching || usageQuery.isFetching}
          canUpdate={canUpdate}
          setDefaultPending={setDefaultMutation.isPending}
          onRefresh={refreshAll}
          onRetryStatus={() => void statusQuery.refetch()}
          onRetryUsage={() => void usageQuery.refetch()}
          onOpenEntry={openEntryEdit}
          onSetDefault={(entry) => setDefaultMutation.mutate(entry)}
          onRelogin={startLogin}
        />
      )}

      <ProxyLoginFlow
        open={loginOpen}
        provider={loginProvider}
        onClose={() => {
          setLoginOpen(false)
          setLoginProvider(null)
        }}
      />

      {draft ? (
        <FormDialog
          title={draftTitle}
          closeLabel={t('settings.cancel')}
          onClose={() => {
            if (saveMutation.isPending) return
            setDraft(null)
          }}
        >
          <form
            onSubmit={(event) => {
              event.preventDefault()
              submitDraft()
            }}
          >
            <div className="space-y-4">
              {draft.mode === 'CLI' ? (
                cliEditLoading ? (
                  <p className="text-sm text-admin-muted">{t('loading')}</p>
                ) : cliEditError ? (
                  <p className="text-sm text-danger-emphasis">
                    {t('error')}{' '}
                    <button
                      type="button"
                      className="underline"
                      onClick={() => {
                        void usageQuery.refetch()
                        void keysQuery.refetch()
                      }}
                    >
                      {t('retry')}
                    </button>
                  </p>
                ) : cliReady ? (
                  <>
                    <FormField label={t('settings.cliModel')} help={t('settings.cliModelHint')}>
                      <select
                        className={adminFieldClass}
                        value={cliModelId}
                        disabled={!canUpdate}
                        onChange={(event) => setDraft({ ...draft, modelSpec: event.target.value })}
                      >
                        {cliEditAccounts.map((account) => {
                          const ids = shownModelIds(account.provider, account.modelIds) ?? []
                          if (ids.length === 0) return null
                          return (
                            <optgroup key={account.name} label={account.provider}>
                              {ids.map((id) => (
                                <option key={`${account.name}-${id}`} value={id}>
                                  {id}
                                </option>
                              ))}
                            </optgroup>
                          )
                        })}
                      </select>
                    </FormField>
                    <FormField label={t('settings.cliKey')} help={t('settings.cliKeyHint')}>
                      <select
                        className={adminFieldClass}
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
                    </FormField>
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
                  <FormField
                    required
                    label={t('settings.name')}
                    error={nameInvalid ? t('modelManagement.nameRequired') : undefined}
                  >
                    <input
                      className={adminFieldClass}
                      value={draft.displayName}
                      disabled={!canUpdate}
                      aria-invalid={nameInvalid}
                      onChange={(event) => setDraft({ ...draft, displayName: event.target.value })}
                    />
                  </FormField>
                  <FormField
                    required
                    label={t('settings.spec')}
                    error={specInvalid ? t('modelManagement.specRequired') : undefined}
                  >
                    <input
                      className={adminFieldClass}
                      value={draft.modelSpec}
                      disabled={!canUpdate}
                      aria-invalid={specInvalid}
                      placeholder={t('settings.specHint')}
                      onChange={(event) => setDraft({ ...draft, modelSpec: event.target.value })}
                    />
                  </FormField>
                  <FormField
                    required={draft.id == null}
                    label={t('settings.apiKey')}
                    help={draft.apiKeyHint ? t('settings.apiKeyHint', { hint: draft.apiKeyHint }) : undefined}
                  >
                    <input
                      className={adminFieldClass}
                      type="password"
                      autoComplete="new-password"
                      value={draft.apiKey}
                      disabled={!canUpdate}
                      placeholder={draft.id == null ? '' : t('settings.apiKeyKeep')}
                      onChange={(event) => setDraft({ ...draft, apiKey: event.target.value })}
                    />
                  </FormField>
                  <FormField label={t('settings.embeddingSpec')}>
                    <input
                      className={adminFieldClass}
                      value={draft.embeddingSpec}
                      disabled={!canUpdate}
                      placeholder={t('settings.embeddingSpecHint')}
                      onChange={(event) => setDraft({ ...draft, embeddingSpec: event.target.value })}
                    />
                  </FormField>
                  <FormField label={t('settings.baseUrl')}>
                    <input
                      className={adminFieldClass}
                      value={draft.baseUrl}
                      disabled={!canUpdate}
                      placeholder={t('settings.baseUrlHint')}
                      onChange={(event) => setDraft({ ...draft, baseUrl: event.target.value })}
                    />
                  </FormField>
                </>
              )}
              <FormField
                label={t('settings.alias')}
                help={t('settings.aliasHint')}
                error={aliasInvalid ? t('settings.aliasInvalid') : undefined}
              >
                <input
                  className={adminFieldClass}
                  value={draft.alias}
                  disabled={!canUpdate}
                  aria-invalid={aliasInvalid}
                  onChange={(event) => setDraft({ ...draft, alias: event.target.value })}
                />
              </FormField>
              {draft.sourceAgentId != null ? (
                <p className="text-sm text-admin-muted">{t('settings.entryProvenanceNote', { id: draft.sourceAgentId })}</p>
              ) : null}
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
            </div>
            <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
              <div className={adminFormActionsClass}>
                {draft.id != null && draft.mode !== 'CLI' ? (
                  <button
                    type="button"
                    className={cn(adminDangerGhostButtonClass, 'mr-auto')}
                    disabled={deleteMutation.isPending}
                    onClick={() => {
                      setDeleteTarget({
                        id: draft.id!,
                        displayName: draft.displayName,
                        modelSpec: draft.modelSpec,
                        apiKeyConfigured: Boolean(draft.apiKeyHint),
                        apiKeyHint: draft.apiKeyHint ?? '',
                        baseUrl: draft.baseUrl || null,
                        sourceAgentId: draft.sourceAgentId,
                        embeddingSpec: draft.embeddingSpec || null,
                        enabled: draft.enabled,
                        defaultEntry: draft.defaultEntry,
                        version: draft.version ?? 0,
                        updatedAt: null,
                        mode: draft.mode,
                        alias: draft.alias || null,
                        derived: false,
                        derivedSource: null,
                      })
                      setConfirmingDelete(true)
                    }}
                  >
                    {t('settings.remove')}
                  </button>
                ) : null}
                <button type="button" className={adminSecondaryButtonClass} onClick={() => setDraft(null)}>
                  {t('settings.cancel')}
                </button>
                <button type="submit" disabled={saveMutation.isPending || aliasInvalid} className={adminPrimaryButtonClass}>
                  {saveMutation.isPending ? (
                    <>
                      <Spinner />
                      {t('settings.saving')}
                    </>
                  ) : (
                    t('settings.save')
                  )}
                </button>
              </div>
            </PermissionGate>
          </form>
        </FormDialog>
      ) : null}

      {confirmingDelete && deleteTarget ? (
        <FormDialog
          title={t('settings.deleteConfirmTitle')}
          closeLabel={t('settings.cancel')}
          onClose={() => {
            setConfirmingDelete(false)
            setDeleteTarget(null)
          }}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('settings.deleteConfirmBody', {
              name: deleteTarget.displayName.trim() || deleteTarget.modelSpec,
            })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => {
                setConfirmingDelete(false)
                setDeleteTarget(null)
              }}
            >
              {t('settings.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(deleteTarget.id)}
            >
              {deleteMutation.isPending ? <Spinner /> : null}
              {t('settings.remove')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </>
  )
}
