import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, KeyRound, Loader2, Plus, Zap } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { FilterTabs, FormDialog, StatusTag } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminDangerGhostButtonClass,
  adminFieldClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
  adminSecondaryButtonSmClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type { AiModelEntry, AiModelEntryWriteInput, ProxyLoginProvider, ProxyServicesAccount } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'
import { modelIdFromSpec } from '@/utils/modelSpec'

import { ProviderIcon } from './providerIcon'
import { ProxyClientKeysPane, ProxyLoginFlow } from './proxyServicesPage'
import {
  accountStatus,
  accountTitle,
  isLoginProvider,
  providerLabel,
  providerTileClass,
  shownModelIds,
} from './proxyServicesShared'

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

function emptyDraft(makeDefault: boolean, specPrefix = ''): Draft {
  return {
    id: null,
    displayName: '',
    modelSpec: specPrefix,
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

const KEY_VENDOR_NAMES: Record<string, string> = {
  google: 'Google',
  openai: 'OpenAI',
  anthropic: 'Anthropic',
  azure: 'Azure',
}
const KNOWN_KEY_VENDORS = ['google', 'openai', 'anthropic', 'azure']
const CLI_VENDOR_ORDER = ['codex', 'claude', 'antigravity', 'meta']
const KEYS_PANE = '__keys'
const UNASSIGNED = '__unassigned'

function keyVendorName(prefix: string) {
  return KEY_VENDOR_NAMES[prefix] ?? (prefix ? prefix.charAt(0).toUpperCase() + prefix.slice(1) : prefix)
}

/** 家族图标砖：按 spec 协议前缀映射色板 token。CLI 厂商砖走 ProviderIcon，不经这里。 */
function familyTile(prefix: string): { letter: string; className: string } {
  if (prefix === 'google') return { letter: 'G', className: 'bg-[rgb(var(--info)/.12)] text-info' }
  if (prefix === 'openai') return { letter: 'O', className: 'bg-[rgb(var(--chart-2)/.12)] text-chart-2' }
  if (prefix === 'anthropic') return { letter: 'A', className: 'bg-[rgb(var(--chart-3)/.12)] text-chart-3' }
  if (prefix === 'azure') return { letter: 'Az', className: 'bg-[rgb(var(--chart-1)/.12)] text-chart-1' }
  return { letter: 'M', className: 'bg-admin-surface-muted text-admin-muted' }
}

function cliModelSpec(modelId: string) {
  const trimmed = modelId.trim()
  if (!trimmed) return trimmed
  return trimmed.includes(':') ? trimmed : `openai:${trimmed}`
}

function specPrefix(spec: string) {
  return spec.split(':')[0]?.trim().toLowerCase() || 'other'
}

/**
 * 模型入口管理（模型页改版，kou 2026-11-07 定形态）：上方水平页签只有
 * CLI / API Key 两种模式，右端一颗「添加」总钮先选添加方式（登录 CLI
 * 账号 / 填 API key 表单）再进弹窗；展示区用竖排厂商页签，点厂商才展
 * 该厂商的条目。CLI 条目按账号放出的模型归到账号所属厂商下，派生行
 * 只读；历史手工 CLI 条目仍可编辑（存量编辑分支），但新建只走 KEY。
 * 客户端 key 与网关状态收在 CLI 左栏末位页签（ProxyClientKeysPane）。
 */
export function ModelEntriesCard() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [mode, setMode] = useState<'CLI' | 'KEY'>('CLI')
  const [keyVendor, setKeyVendor] = useState<string | null>(null)
  const [cliVendor, setCliVendor] = useState<string | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [formTouched, setFormTouched] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [addOpen, setAddOpen] = useState(false)
  const [loginOpen, setLoginOpen] = useState(false)
  const [loginProvider, setLoginProvider] = useState<ProxyLoginProvider | null>(null)

  // 页面挂载同步一次 CLI 派生条目（Issue #214 UoW-2）：派生行随代理侧
  // 账号/模型构成自动物化；同步失败不打断页面，派生行保持现状。
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
        // 同步端点失败时静默：入口列表仍可用，派生行下次打开页面再同步。
      })
  }, [queryClient, t])

  const { data, isPending, isError, error, refetch } = useQuery({
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
    onSuccess: (entry, input) => {
      toast.success(t('settings.saved'))
      setDraft(null)
      setFormTouched(false)
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
      if (input.id == null && (entry.mode ?? 'KEY') !== 'CLI') {
        // 新建落点：切到 KEY 模式并选中新条目所在厂商（审计口径：追加成功按类型区分反馈）。
        setMode('KEY')
        setKeyVendor(specPrefix(entry.modelSpec))
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
      setDraft(null)
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (deleteError: Error) => {
      toast.error(deleteError.message)
    },
  })

  if (isPending) {
    return (
      <div className="space-y-4" aria-hidden>
        <div className="admin-skeleton h-9 w-64" />
        <div className="admin-skeleton h-72 w-full rounded-xl" />
      </div>
    )
  }
  if (isError) {
    return (
      <div className="flex items-start gap-3 rounded-xl border border-danger/35 bg-danger-muted px-4 py-3">
        <AlertCircle className="mt-0.5 h-[18px] w-[18px] shrink-0 text-danger-emphasis" aria-hidden />
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium text-danger-emphasis">
            {error instanceof Error ? error.message : t('error')}
          </p>
          <button type="button" className={cn(adminSecondaryButtonSmClass, 'mt-2')} onClick={() => void refetch()}>
            {t('retry')}
          </button>
        </div>
      </div>
    )
  }

  const entries = data ?? []
  const keyEntries = entries.filter((entry) => (entry.mode ?? 'KEY') !== 'CLI')
  const cliEntries = entries.filter((entry) => (entry.mode ?? 'KEY') === 'CLI')

  // KEY 厂商：按 spec 前缀聚合；四个常见厂商恒列出（空厂商给空态与首加入口）。
  const directGroups = new Map<string, AiModelEntry[]>()
  for (const entry of keyEntries) {
    const prefix = specPrefix(entry.modelSpec)
    const group = directGroups.get(prefix) ?? []
    group.push(entry)
    directGroups.set(prefix, group)
  }
  const keyVendors = [
    ...KNOWN_KEY_VENDORS,
    ...[...directGroups.keys()].filter((prefix) => !KNOWN_KEY_VENDORS.includes(prefix)).sort(),
  ]
  const activeKeyVendor =
    keyVendor != null && keyVendors.includes(keyVendor)
      ? keyVendor
      : (keyVendors.find((prefix) => (directGroups.get(prefix) ?? []).length > 0) ?? keyVendors[0])
  const activeKeyEntries = directGroups.get(activeKeyVendor) ?? []

  // CLI 厂商：按代理账号的 provider 聚合；条目按账号放出的模型 id 归属账号。
  const accounts = statusQuery.data?.accounts ?? []
  const publishedByName = new Map((usageQuery.data?.accounts ?? []).map((account) => [account.name, account]))
  function accountEntries(account: ProxyServicesAccount): AiModelEntry[] {
    if (usageQuery.isPending) return []
    const published = publishedByName.get(account.name)
    const ids = shownModelIds(account.provider, published?.modelIds ?? []) ?? []
    return cliEntries.filter((entry) => ids.includes(modelIdFromSpec(entry.modelSpec)))
  }
  const cliProviders = [
    ...CLI_VENDOR_ORDER.filter((provider) => accounts.some((account) => account.provider === provider)),
    ...[...new Set(accounts.map((account) => account.provider))].filter(
      (provider) => !CLI_VENDOR_ORDER.includes(provider),
    ),
  ]
  const matchedEntryIds = new Set<number>()
  for (const account of accounts) {
    for (const entry of accountEntries(account)) matchedEntryIds.add(entry.id)
  }
  const unassignedCliEntries = usageQuery.isPending ? [] : cliEntries.filter((entry) => !matchedEntryIds.has(entry.id))
  const cliRailKeys = [
    ...cliProviders,
    ...(unassignedCliEntries.length > 0 ? [UNASSIGNED] : []),
    KEYS_PANE,
  ]
  const activeCliVendor =
    cliVendor != null && cliRailKeys.includes(cliVendor) ? cliVendor : (cliProviders[0] ?? KEYS_PANE)

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

  // 存量手工 CLI 条目编辑分支的就绪判定：区分加载中 / 加载失败 / 真未配置（原 H6）。
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

  function entryRow(entry: AiModelEntry) {
    const entryMode = entry.mode ?? 'KEY'
    const usedPercent =
      entryMode === 'CLI'
        ? (accounts.find((account) => accountEntries(account).includes(entry))?.modelUsedPercent[
            modelIdFromSpec(entry.modelSpec)
          ] ?? null)
        : null
    return (
      <li key={entry.id} className="flex items-stretch gap-2">
        <button
          type="button"
          className="flex flex-1 items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition hover:bg-admin-surface-muted admin-focus-ring"
          onClick={() => openEntryEdit(entry)}
        >
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
              {entry.modelSpec}
              {entryMode !== 'CLI' && entry.embeddingSpec ? ` · ${entry.embeddingSpec}` : ''}
            </span>
          </span>
          <span className="flex flex-wrap items-center justify-end gap-1.5">
            {entry.defaultEntry ? (
              <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                {entryMode === 'CLI' ? t('settings.defaultBadgeCli') : t('settings.defaultBadgeKey')}
              </span>
            ) : null}
            {entry.derived ? (
              <StatusTag status="neutral" tone="neutral" label={t('modelManagement.derivedBadge')} />
            ) : null}
            {!entry.enabled ? <StatusTag status="DISABLED" tone="danger" label={t('settings.disabledBadge')} /> : null}
            {usedPercent != null ? (
              <span className="font-mono text-xs tabular-nums text-admin-muted" title={t('proxyServices.statQuota')}>
                {usedPercent}%
              </span>
            ) : null}
            {entry.apiKeyConfigured ? (
              <span className="rounded-md bg-admin-surface-muted px-1.5 py-0.5 font-mono text-xs text-admin-muted">
                {entry.apiKeyHint}
              </span>
            ) : null}
          </span>
        </button>
        {canUpdate && entry.enabled && !entry.defaultEntry && !entry.derived ? (
          <button
            type="button"
            className={cn(adminGhostButtonClass, 'shrink-0 self-center')}
            disabled={setDefaultMutation.isPending}
            onClick={() => setDefaultMutation.mutate(entry)}
          >
            {t('settings.setDefault')}
          </button>
        ) : null}
      </li>
    )
  }

  const railItemClass = (active: boolean) =>
    cn(
      'flex w-full items-center gap-3 rounded-[10px] border border-transparent px-2.5 py-2 text-left transition hover:bg-admin-surface-muted admin-focus-ring',
      active && 'admin-active-inset border-admin bg-admin-surface shadow-sm',
    )

  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <FilterTabs
          tabs={[
            { key: 'CLI', label: t('modelManagement.tabCli'), count: cliEntries.length },
            { key: 'KEY', label: t('modelManagement.tabKey'), count: keyEntries.length },
          ]}
          activeKey={mode}
          onChange={(key) => setMode(key === 'KEY' ? 'KEY' : 'CLI')}
        />
        <span className="flex-1" />
        <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
          <button type="button" className={adminPrimaryButtonClass} onClick={() => setAddOpen(true)}>
            <Plus className="h-4 w-4" />
            {t('modelManagement.addButton')}
          </button>
        </PermissionGate>
      </div>

      <article className="overflow-hidden rounded-xl border border-admin bg-admin-surface">
        <div className="grid items-start lg:grid-cols-[15rem_minmax(0,1fr)]">
          {mode === 'KEY' ? (
            <div className="flex flex-col gap-0.5 p-2" aria-label={t('modelManagement.sourcesTitle')}>
              <p className="px-2.5 pb-1 pt-1.5 text-[11px] font-semibold uppercase tracking-wide text-admin-muted">
                {t('modelManagement.directSource')}
              </p>
              {keyVendors.map((prefix) => {
                const tile = familyTile(prefix)
                const count = (directGroups.get(prefix) ?? []).length
                return (
                  <button
                    key={prefix}
                    type="button"
                    aria-pressed={activeKeyVendor === prefix}
                    onClick={() => setKeyVendor(prefix)}
                    className={railItemClass(activeKeyVendor === prefix)}
                  >
                    <span className={cn('flex h-9 w-9 shrink-0 items-center justify-center rounded-admin-md text-sm font-bold', tile.className)}>
                      {tile.letter}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-semibold text-admin">{keyVendorName(prefix)}</span>
                      <span className="mt-0.5 block truncate text-xs text-admin-muted">
                        {t('modelManagement.modelsCount', { count })}
                      </span>
                    </span>
                  </button>
                )
              })}
            </div>
          ) : (
            <div className="flex flex-col gap-0.5 p-2" aria-label={t('modelManagement.sourcesTitle')}>
              <p className="px-2.5 pb-1 pt-1.5 text-[11px] font-semibold uppercase tracking-wide text-admin-muted">
                {t('modelManagement.proxySource')}
              </p>
              {statusQuery.isPending ? (
                <div className="space-y-2 px-1 py-1" aria-hidden>
                  <div className="admin-skeleton h-11 w-full" />
                  <div className="admin-skeleton h-11 w-full" />
                </div>
              ) : null}
              {cliProviders.map((provider) => {
                const providerAccounts = accounts.filter((account) => account.provider === provider)
                const count = new Set(providerAccounts.flatMap((account) => accountEntries(account).map((entry) => entry.id))).size
                return (
                  <button
                    key={provider}
                    type="button"
                    aria-pressed={activeCliVendor === provider}
                    onClick={() => setCliVendor(provider)}
                    className={railItemClass(activeCliVendor === provider)}
                  >
                    <span className={cn('flex h-9 w-9 shrink-0 items-center justify-center rounded-admin-md', providerTileClass(provider))}>
                      {isLoginProvider(provider) ? (
                        <ProviderIcon id={provider} className="h-5 w-5" />
                      ) : (
                        <span className="text-sm font-bold">{provider.slice(0, 1).toUpperCase()}</span>
                      )}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-semibold text-admin">{providerLabel(provider, t)}</span>
                      <span className="mt-0.5 block truncate text-xs text-admin-muted">
                        {t('modelManagement.proxyAccounts', { count: providerAccounts.length })} ·{' '}
                        {t('modelManagement.modelsCount', { count })}
                      </span>
                    </span>
                  </button>
                )
              })}
              {unassignedCliEntries.length > 0 ? (
                <button
                  type="button"
                  aria-pressed={activeCliVendor === UNASSIGNED}
                  onClick={() => setCliVendor(UNASSIGNED)}
                  className={railItemClass(activeCliVendor === UNASSIGNED)}
                >
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-admin-md bg-admin-surface-muted text-sm font-bold text-admin-muted">
                    ?
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold text-admin">
                      {t('modelManagement.unassignedVendor')}
                    </span>
                    <span className="mt-0.5 block truncate text-xs text-admin-muted">
                      {t('modelManagement.modelsCount', { count: unassignedCliEntries.length })}
                    </span>
                  </span>
                </button>
              ) : null}
              <div className="mx-1 my-1.5 border-t border-admin" />
              <button
                type="button"
                aria-pressed={activeCliVendor === KEYS_PANE}
                onClick={() => setCliVendor(KEYS_PANE)}
                className={railItemClass(activeCliVendor === KEYS_PANE)}
              >
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-admin-md bg-primary/10 text-primary">
                  <KeyRound className="h-4 w-4" aria-hidden />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-admin">{t('proxyServices.usageKeysTitle')}</span>
                  <span className="mt-0.5 block truncate text-xs text-admin-muted">{t('modelManagement.keysPaneHint')}</span>
                </span>
              </button>
            </div>
          )}

          <div className="min-w-0 border-t border-admin lg:border-l lg:border-t-0">
            {mode === 'KEY' ? (
              <div className="p-4">
                <div className="mb-2 flex flex-wrap items-center gap-3">
                  <span className="text-base font-semibold text-admin">{keyVendorName(activeKeyVendor)}</span>
                  <span className="text-xs text-admin-muted">
                    {t('modelManagement.directSource')} · {t('modelManagement.modelsCount', { count: activeKeyEntries.length })}
                  </span>
                  <span className="flex-1" />
                  <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
                    <button
                      type="button"
                      className={adminPrimaryButtonClass}
                      onClick={() => {
                        setFormTouched(false)
                        setDraft(emptyDraft(keyEntries.length === 0, `${activeKeyVendor}:`))
                      }}
                    >
                      <Plus className="h-4 w-4" />
                      {t('settings.add')}
                    </button>
                  </PermissionGate>
                </div>
                {activeKeyEntries.length === 0 ? (
                  <div className="px-2 py-10 text-center">
                    <span className="mx-auto flex h-[52px] w-[52px] items-center justify-center rounded-[14px] bg-admin-surface-muted text-admin-muted">
                      <Plus className="h-5 w-5" aria-hidden />
                    </span>
                    <p className="mt-3 text-sm font-medium text-admin">
                      {t('modelManagement.emptyVendorTitle', { vendor: keyVendorName(activeKeyVendor) })}
                    </p>
                    <p className="mt-1 text-xs text-admin-muted">{t('modelManagement.emptyVendorHint')}</p>
                  </div>
                ) : (
                  <ul className="space-y-2">{activeKeyEntries.map(entryRow)}</ul>
                )}
              </div>
            ) : activeCliVendor === KEYS_PANE ? (
              <ProxyClientKeysPane />
            ) : (
              <div className="p-4">
                {statusQuery.isError ? (
                  <div className="flex items-start gap-3 rounded-xl border border-danger/35 bg-danger-muted px-4 py-3">
                    <AlertCircle className="mt-0.5 h-[18px] w-[18px] shrink-0 text-danger-emphasis" aria-hidden />
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium text-danger-emphasis">{t('error')}</p>
                      <button
                        type="button"
                        className={cn(adminSecondaryButtonSmClass, 'mt-2')}
                        onClick={() => void statusQuery.refetch()}
                      >
                        {t('retry')}
                      </button>
                    </div>
                  </div>
                ) : null}
                {activeCliVendor === UNASSIGNED ? (
                  <>
                    <p className="mb-2 text-sm text-admin-muted">{t('modelManagement.unassignedHint')}</p>
                    <ul className="space-y-2">{unassignedCliEntries.map(entryRow)}</ul>
                  </>
                ) : (
                  accounts
                    .filter((account) => account.provider === activeCliVendor)
                    .map((account) => {
                      const status = accountStatus(account, t)
                      const rows = accountEntries(account)
                      return (
                        <div key={account.name} className="mb-5 last:mb-0">
                          <div className="mb-2 flex flex-wrap items-center gap-3">
                            <span className={cn('flex h-9 w-9 shrink-0 items-center justify-center rounded-admin-md', providerTileClass(account.provider))}>
                              {isLoginProvider(account.provider) ? (
                                <ProviderIcon id={account.provider} className="h-5 w-5" />
                              ) : (
                                <span className="text-sm font-bold">{account.provider.slice(0, 1).toUpperCase()}</span>
                              )}
                            </span>
                            <span className="min-w-0">
                              <span className="flex items-center gap-1.5 text-sm font-semibold text-admin">
                                {accountTitle(account)}
                                {status.tone === 'active' ? (
                                  <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-admin-success" />
                                ) : (
                                  <StatusTag status={status.tone} tone={status.tone} label={status.label} />
                                )}
                              </span>
                              <span className="block text-xs text-admin-muted">
                                {providerLabel(account.provider, t)} ·{' '}
                                {t('modelManagement.modelsCount', { count: rows.length })}
                              </span>
                            </span>
                            <span className="flex-1" />
                            <div className="flex shrink-0 gap-2">
                              <div className="rounded-lg bg-admin-surface-muted px-3 py-1.5 text-right">
                                <p className="text-base font-bold tabular-nums text-success-emphasis">{account.success}</p>
                                <p className="text-[11px] text-admin-muted">{t('proxyServices.statSuccess')}</p>
                              </div>
                              <div className="rounded-lg bg-admin-surface-muted px-3 py-1.5 text-right">
                                <p className="text-base font-bold tabular-nums text-danger-emphasis">{account.failed}</p>
                                <p className="text-[11px] text-admin-muted">{t('proxyServices.statFailed')}</p>
                              </div>
                              <div className="rounded-lg bg-admin-surface-muted px-3 py-1.5 text-right">
                                <p className="text-base font-bold tabular-nums text-admin">
                                  {account.usedPercent != null ? `${account.usedPercent}%` : t('proxyServices.quotaUnknown')}
                                </p>
                                <p className="text-[11px] text-admin-muted">{t('proxyServices.statQuota')}</p>
                              </div>
                            </div>
                            {canUpdate && status.tone !== 'active' ? (
                              <button
                                type="button"
                                className={cn(adminGhostButtonClass, 'shrink-0')}
                                onClick={() => startLogin(isLoginProvider(account.provider) ? account.provider : null)}
                              >
                                {t('modelManagement.relogin')}
                              </button>
                            ) : null}
                          </div>
                          {usageQuery.isPending ? (
                            <p className="px-2 py-3 text-sm text-admin-muted">{t('loading')}</p>
                          ) : rows.length === 0 ? (
                            <p className="px-2 py-3 text-sm text-admin-muted">{t('modelManagement.noCliModels')}</p>
                          ) : (
                            <ul className="space-y-2">{rows.map(entryRow)}</ul>
                          )}
                        </div>
                      )
                    })
                )}
                {activeCliVendor !== UNASSIGNED &&
                statusQuery.isPending === false &&
                accounts.filter((account) => account.provider === activeCliVendor).length === 0 ? (
                  <p className="px-2 py-6 text-sm text-admin-muted">{t('proxyServices.emptyAccounts')}</p>
                ) : null}
              </div>
            )}
          </div>
        </div>
      </article>

      {addOpen ? (
        <FormDialog
          title={t('modelManagement.addTitle')}
          closeLabel={t('settings.cancel')}
          onClose={() => setAddOpen(false)}
        >
          <div className="space-y-3">
            <button
              type="button"
              className="flex w-full items-start gap-3 rounded-xl border border-admin bg-admin-surface p-4 text-left transition hover:border-primary/55 admin-focus-ring"
              onClick={() => {
                setAddOpen(false)
                startLogin(null)
              }}
            >
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-admin-md bg-[rgb(var(--chart-4)/.12)] text-chart-4">
                <Zap className="h-4 w-4" aria-hidden />
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-semibold text-admin">{t('modelManagement.addCliTitle')}</span>
                <span className="mt-0.5 block text-xs text-admin-muted">{t('modelManagement.addCliDesc')}</span>
              </span>
            </button>
            <button
              type="button"
              className="flex w-full items-start gap-3 rounded-xl border border-admin bg-admin-surface p-4 text-left transition hover:border-primary/55 admin-focus-ring"
              onClick={() => {
                setAddOpen(false)
                setFormTouched(false)
                setDraft(emptyDraft(keyEntries.length === 0))
              }}
            >
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-admin-md bg-[rgb(var(--info)/.12)] text-info">
                <KeyRound className="h-4 w-4" aria-hidden />
              </span>
              <span className="min-w-0">
                <span className="block text-sm font-semibold text-admin">{t('modelManagement.addKeyTitle')}</span>
                <span className="mt-0.5 block text-xs text-admin-muted">{t('modelManagement.addKeyDesc')}</span>
              </span>
            </button>
          </div>
          <div className={adminFormActionsClass}>
            <button type="button" className={adminSecondaryButtonClass} onClick={() => setAddOpen(false)}>
              {t('settings.cancel')}
            </button>
          </div>
        </FormDialog>
      ) : null}

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
                    <label className="block text-xs font-medium text-admin-muted">
                      {t('settings.cliModel')}
                      <select
                        className={`${adminFieldClass} mt-1.5`}
                        value={cliModelId}
                        disabled={!canUpdate}
                        onChange={(event) => setDraft({ ...draft, modelSpec: event.target.value })}
                      >
                        {cliEditAccounts.map((account) => {
                          const ids = shownModelIds(account.provider, account.modelIds) ?? []
                          if (ids.length === 0) return null
                          return (
                            <optgroup key={account.name} label={providerLabel(account.provider, t)}>
                              {ids.map((id) => (
                                <option key={`${account.name}-${id}`} value={id}>
                                  {id}
                                </option>
                              ))}
                            </optgroup>
                          )
                        })}
                      </select>
                      <span className="mt-1 block text-xs font-normal text-admin-muted">{t('settings.cliModelHint')}</span>
                    </label>
                    <label className="block text-xs font-medium text-admin-muted">
                      {t('settings.cliKey')}
                      <select
                        className={`${adminFieldClass} mt-1.5`}
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
                      <span className="mt-1 block text-xs font-normal text-admin-muted">{t('settings.cliKeyHint')}</span>
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
                  <label className="block text-xs font-medium text-admin-muted">
                    {t('settings.name')} <span className="text-danger-emphasis">*</span>
                    <input
                      className={`${adminFieldClass} mt-1.5`}
                      value={draft.displayName}
                      disabled={!canUpdate}
                      aria-invalid={nameInvalid}
                      onChange={(event) => setDraft({ ...draft, displayName: event.target.value })}
                    />
                    {nameInvalid ? (
                      <span className="mt-1 block text-xs font-normal text-danger-emphasis">
                        {t('modelManagement.nameRequired')}
                      </span>
                    ) : null}
                  </label>
                  <label className="block text-xs font-medium text-admin-muted">
                    {t('settings.spec')} <span className="text-danger-emphasis">*</span>
                    <input
                      className={`${adminFieldClass} mt-1.5`}
                      value={draft.modelSpec}
                      disabled={!canUpdate}
                      aria-invalid={specInvalid}
                      placeholder={t('settings.specHint')}
                      onChange={(event) => setDraft({ ...draft, modelSpec: event.target.value })}
                    />
                    {specInvalid ? (
                      <span className="mt-1 block text-xs font-normal text-danger-emphasis">
                        {t('modelManagement.specRequired')}
                      </span>
                    ) : null}
                  </label>
                  <label className="block text-xs font-medium text-admin-muted">
                    {t('settings.apiKey')}
                    {draft.id == null ? <span className="text-danger-emphasis"> *</span> : null}
                    <input
                      className={`${adminFieldClass} mt-1.5`}
                      type="password"
                      autoComplete="new-password"
                      value={draft.apiKey}
                      disabled={!canUpdate}
                      placeholder={draft.id == null ? '' : t('settings.apiKeyKeep')}
                      onChange={(event) => setDraft({ ...draft, apiKey: event.target.value })}
                    />
                    {draft.apiKeyHint ? (
                      <span className="mt-1 block text-xs font-normal text-admin-muted">
                        {t('settings.apiKeyHint', { hint: draft.apiKeyHint })}
                      </span>
                    ) : null}
                  </label>
                  <label className="block text-xs font-medium text-admin-muted">
                    {t('settings.embeddingSpec')}
                    <input
                      className={`${adminFieldClass} mt-1.5`}
                      value={draft.embeddingSpec}
                      disabled={!canUpdate}
                      placeholder={t('settings.embeddingSpecHint')}
                      onChange={(event) => setDraft({ ...draft, embeddingSpec: event.target.value })}
                    />
                  </label>
                  <label className="block text-xs font-medium text-admin-muted">
                    {t('settings.baseUrl')}
                    <input
                      className={`${adminFieldClass} mt-1.5`}
                      value={draft.baseUrl}
                      disabled={!canUpdate}
                      placeholder={t('settings.baseUrlHint')}
                      onChange={(event) => setDraft({ ...draft, baseUrl: event.target.value })}
                    />
                  </label>
                </>
              )}
              <label className="block text-xs font-medium text-admin-muted">
                {t('settings.alias')}
                <input
                  className={`${adminFieldClass} mt-1.5`}
                  value={draft.alias}
                  disabled={!canUpdate}
                  aria-invalid={aliasInvalid}
                  onChange={(event) => setDraft({ ...draft, alias: event.target.value })}
                />
                <span className="mt-1 block text-xs font-normal text-admin-muted">{t('settings.aliasHint')}</span>
                {aliasInvalid ? (
                  <span className="mt-1 block text-xs font-normal text-danger-emphasis">{t('settings.aliasInvalid')}</span>
                ) : null}
              </label>
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
                {draft.id != null ? (
                  <button
                    type="button"
                    className={cn(adminDangerGhostButtonClass, 'mr-auto')}
                    disabled={deleteMutation.isPending}
                    onClick={() => setConfirmingDelete(true)}
                  >
                    {t('settings.remove')}
                  </button>
                ) : null}
                <button
                  type="button"
                  className={adminSecondaryButtonClass}
                  onClick={() => setDraft(null)}
                >
                  {t('settings.cancel')}
                </button>
                <button type="submit" disabled={saveMutation.isPending || aliasInvalid} className={adminPrimaryButtonClass}>
                  {saveMutation.isPending ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
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

      {confirmingDelete && draft && draft.id != null ? (
        <FormDialog
          title={t('settings.deleteConfirmTitle')}
          closeLabel={t('settings.cancel')}
          onClose={() => setConfirmingDelete(false)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('settings.deleteConfirmBody', { name: draft.displayName.trim() || draft.modelSpec })}
          </p>
          <div className={adminFormActionsClass}>
            <button type="button" className={adminSecondaryButtonClass} onClick={() => setConfirmingDelete(false)}>
              {t('settings.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={deleteMutation.isPending}
              onClick={() => {
                if (draft.id == null) return
                deleteMutation.mutate(draft.id)
              }}
            >
              {t('settings.remove')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </>
  )
}
