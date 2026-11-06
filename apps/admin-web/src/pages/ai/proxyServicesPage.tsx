import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Check, Copy, ExternalLink, Plus } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { CopyableText, FormDialog, StatusTag } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminDangerGhostButtonClass,
  adminFieldClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type { ProxyLoginProvider, ProxyServicesAccount, ProxyServicesAccountPacing } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

import { ProviderIcon } from './providerIcon'

type PendingLogin = { provider: ProxyLoginProvider; url: string; state: string }

type ShownAccount = ProxyServicesAccount & { modelIds: string[] | null | undefined }

const LOGIN_TIMEOUT_MS = 5 * 60 * 1000

function pacingDuration(
  ms: number | null | undefined,
  t: (key: string, options?: Record<string, unknown>) => string,
): string {
  if (ms == null || ms <= 0) return t('proxyServices.pacingOff')
  if (ms % 1000 === 0) return t('proxyServices.pacingSeconds', { count: ms / 1000 })
  return t('proxyServices.pacingMillis', { count: ms })
}

function pacingCount(value: number | null | undefined, t: (key: string) => string): string {
  if (value == null || value <= 0) return t('proxyServices.pacingOff')
  return String(value)
}

const LOGIN_OPTIONS: { id: ProxyLoginProvider; titleKey: string; noteKey: string }[] = [
  { id: 'codex', titleKey: 'proxyServices.providerCodex', noteKey: 'proxyServices.loginCodexNote' },
  { id: 'claude', titleKey: 'proxyServices.providerClaude', noteKey: 'proxyServices.loginClaudeNote' },
  { id: 'antigravity', titleKey: 'proxyServices.providerGemini', noteKey: 'proxyServices.loginGeminiNote' },
  { id: 'meta', titleKey: 'proxyServices.providerMuse', noteKey: 'proxyServices.loginMuseNote' },
]

function isLoginProvider(provider: string): provider is ProxyLoginProvider {
  return LOGIN_OPTIONS.some((option) => option.id === provider)
}

function providerLabel(provider: string, t: (key: string) => string) {
  if (provider === 'codex') return t('proxyServices.providerCodex')
  if (provider === 'claude') return t('proxyServices.providerClaude')
  if (provider === 'antigravity') return t('proxyServices.providerGemini')
  if (provider === 'meta') return t('proxyServices.providerMuse')
  return provider
}

function isReady(account: { disabled: boolean; unavailable: boolean }) {
  return !account.disabled && !account.unavailable
}

/** 家族色映射（设计稿）：GPT=chart-2、Claude=chart-3、Gemini=info、Muse/其他=chart-4。 */
function providerTileClass(provider: string) {
  if (provider === 'codex') return 'bg-[rgb(var(--chart-2)/.13)] text-[rgb(var(--chart-2))]'
  if (provider === 'claude') return 'bg-[rgb(var(--chart-3)/.14)] text-[rgb(var(--chart-3))]'
  if (provider === 'antigravity') return 'bg-[rgb(var(--info)/.12)] text-[rgb(var(--info))]'
  if (provider === 'meta') return 'bg-[rgb(var(--chart-4)/.12)] text-[rgb(var(--chart-4))]'
  return 'bg-admin-surface-muted text-admin-muted'
}

function accountTitle(account: ProxyServicesAccount) {
  if (account.email) return account.email
  const stripped = account.name.replace(/\.json$/i, '')
  const found = stripped.match(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i)
  if (!found) return stripped.replace(/^(codex|claude|antigravity|meta)-/i, '')
  const at = found[0].lastIndexOf('@')
  const local = found[0].slice(0, at).split('-').pop()
  return `${local}@${found[0].slice(at + 1)}`
}

function PacingStrip({
  pacing,
  t,
}: {
  pacing: ProxyServicesAccountPacing | null
  t: (key: string, options?: Record<string, unknown>) => string
}) {
  const interval = pacingDuration(pacing?.minIntervalMs, t)
  const jitter =
    pacing != null && pacing.minIntervalMs > 0 && pacing.minIntervalJitterMs > 0
      ? t('proxyServices.pacingJitter', { value: pacingDuration(pacing.minIntervalJitterMs, t) })
      : null
  const rows = [
    {
      label: t('proxyServices.pacingInterval'),
      value: jitter ? `${interval}（${jitter}）` : interval,
    },
    { label: t('proxyServices.pacingInFlight'), value: pacingCount(pacing?.maxInFlight, t) },
    {
      label: t('proxyServices.pacingQuota'),
      value:
        pacing == null || pacing.quotaUsedPercent <= 0
          ? t('proxyServices.pacingOff')
          : `${pacing.quotaUsedPercent}%`,
    },
    { label: t('proxyServices.pacingMaxWait'), value: pacingDuration(pacing?.maxWaitMs, t) },
  ]
  return (
    <dl className="grid gap-3 border-b border-admin px-4 py-3 sm:grid-cols-2 lg:grid-cols-4">
      {rows.map((row) => (
        <div key={row.label}>
          <dt className="text-[11px] text-admin-muted">{row.label}</dt>
          <dd className="mt-1 text-sm text-admin">{row.value}</dd>
        </div>
      ))}
    </dl>
  )
}

function accountStatus(account: ProxyServicesAccount, t: (key: string) => string) {
  if (account.disabled) return { tone: 'disabled' as const, label: t('proxyServices.disabled') }
  if (account.unavailable) return { tone: 'warning' as const, label: t('proxyServices.unavailable') }
  return { tone: 'active' as const, label: t('proxyServices.accountReady') }
}

function shownModelIds(provider: string, modelIds: string[] | null | undefined) {
  if (modelIds == null || provider === 'claude') return modelIds
  return modelIds.filter((id) => !id.toLowerCase().startsWith('claude'))
}

/** 客户端 key 的脱敏显示：只露尾 4 位，全文不进 DOM（DS §7 密钥字段定案）。 */
function maskApiKey(apiKey: string) {
  return apiKey.length > 4 ? `••••••••${apiKey.slice(-4)}` : '••••'
}

/** 尾号标识：供删除确认文案称呼对象；key 过短时返回 null，文案改用泛称。 */
function apiKeyTail(apiKey: string) {
  return apiKey.length > 4 ? apiKey.slice(-4) : null
}

/** 脱敏 key 旁的复制钮：行为与 CopyableText 一致（clipboard＋toast），但 aria-label 不带 key 本身。 */
function ApiKeyCopyButton({ value }: { value: string }) {
  const { t } = useTranslation('common')
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(true)
      toast.success(t('actions.copied'))
      window.setTimeout(() => setCopied(false), 1500)
    } catch {
      toast.error(t('error.unknown'))
    }
  }

  return (
    <button
      type="button"
      onClick={() => void handleCopy()}
      className={cn(adminGhostButtonClass, 'shrink-0')}
      title={t('actions.copy')}
      aria-label={t('actions.copy')}
    >
      {copied ? <Check className="h-3.5 w-3.5 text-admin-success" /> : <Copy className="h-3.5 w-3.5" />}
    </button>
  )
}

/**
 * CLI 代理面板本体（账号、登录、客户端 key、模型清单与 pacing）：原
 * 「API 管理」页的 CLI 部分整体搬运（Issue #214 UoW-2 起被「模型 API
 * 管理」页的代理来源详情复用），逻辑与文案未改。原页面已于 Issue
 * #215 退役（KEY 页签搬入模型页作分节），本文件只剩本面板。
 */
export function ProxyServicesCliPanel() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [creating, setCreating] = useState(false)
  const [choosing, setChoosing] = useState(false)
  const [draftKey, setDraftKey] = useState('')
  const [createdKey, setCreatedKey] = useState<string | null>(null)
  const [deletingKey, setDeletingKey] = useState<string | null>(null)
  const [pendingLogin, setPendingLogin] = useState<PendingLogin | null>(null)
  const [selectedName, setSelectedName] = useState<string | null>(null)
  const namesAtLogin = useRef<string[] | null>(null)

  const statusQuery = useQuery({
    queryKey: ['admin', 'proxy-services'],
    queryFn: () => aiApi.getProxyServices(),
  })
  const status = statusQuery.data
  const canManage = Boolean(status?.reachable && status.managementConfigured)

  const keysQuery = useQuery({
    queryKey: ['admin', 'proxy-services', 'api-keys'],
    queryFn: () => aiApi.listProxyApiKeys(),
    enabled: canManage,
  })
  const usageQuery = useQuery({
    queryKey: ['admin', 'proxy-services', 'usage'],
    queryFn: () => aiApi.getProxyServicesUsage(),
    enabled: canManage,
  })
  const usage = usageQuery.data

  const createMutation = useMutation({
    mutationFn: (apiKey: string) => aiApi.createProxyApiKey(apiKey),
    onSuccess: (created) => {
      setCreatedKey(created.apiKey)
      setCreating(false)
      setDraftKey('')
      toast.success(t('proxyServices.created'))
      void queryClient.invalidateQueries({ queryKey: ['admin', 'proxy-services', 'api-keys'] })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'proxy-services', 'usage'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const loginMutation = useMutation({
    mutationFn: (provider: ProxyLoginProvider) => aiApi.startProxyLogin(provider),
    onSuccess: (started) => {
      setPendingLogin({ provider: started.provider, url: started.url, state: started.state })
      window.open(started.url, '_blank', 'noopener,noreferrer')
    },
    onError: (error: Error) => toast.error(error.message),
  })

  useEffect(() => {
    if (!pendingLogin) return
    let stopped = false
    const startedAt = Date.now()
    const timer = window.setInterval(() => {
      if (Date.now() - startedAt > LOGIN_TIMEOUT_MS) {
        window.clearInterval(timer)
        if (!stopped) {
          setPendingLogin(null)
          namesAtLogin.current = null
          toast.error(t('proxyServices.loginTimeout'))
        }
        return
      }
      void aiApi
        .proxyLoginStatus(pendingLogin.state)
        .then((result) => {
          if (stopped || result.status === 'wait') return
          window.clearInterval(timer)
          setPendingLogin(null)
          if (result.status === 'ok') {
            toast.success(t('proxyServices.loginOk'))
            void queryClient.invalidateQueries({ queryKey: ['admin', 'proxy-services'] })
            void queryClient.invalidateQueries({ queryKey: ['admin', 'proxy-services', 'usage'] })
            return
          }
          namesAtLogin.current = null
          toast.error(result.error || t('proxyServices.loginError'))
        })
        .catch((error: Error) => {
          if (stopped) return
          window.clearInterval(timer)
          setPendingLogin(null)
          namesAtLogin.current = null
          toast.error(error.message)
        })
    }, 2000)
    return () => {
      stopped = true
      window.clearInterval(timer)
    }
  }, [pendingLogin, queryClient, t])

  const deleteMutation = useMutation({
    mutationFn: (apiKey: string) => aiApi.deleteProxyApiKey(apiKey),
    onSuccess: () => {
      toast.success(t('proxyServices.deleted'))
      void queryClient.invalidateQueries({ queryKey: ['admin', 'proxy-services', 'api-keys'] })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'proxy-services', 'usage'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const apiBaseUrl = usage?.apiBaseUrl || (status?.baseUrl ? `${status.baseUrl.replace(/\/$/, '')}/v1` : '')
  const publishedByName = new Map((usage?.accounts ?? []).map((account) => [account.name, account]))
  const clientKeys = keysQuery.data?.apiKeys ?? []
  const accounts: ShownAccount[] = (status?.accounts ?? []).map((account) => ({
    ...account,
    email: account.email ?? null,
    success: account.success ?? 0,
    failed: account.failed ?? 0,
    usedPercent: account.usedPercent ?? null,
    modelUsedPercent: account.modelUsedPercent ?? {},
    modelIds: usageQuery.isLoading ? undefined : usageQuery.isError || !usage ? null : (publishedByName.get(account.name)?.modelIds ?? []),
  }))
  const readyAccounts = accounts.filter(isReady)
  const selected = accounts.find((account) => account.name === selectedName) ?? accounts[0] ?? null
  const visibleKeys = createdKey && !clientKeys.includes(createdKey) ? [createdKey, ...clientKeys] : clientKeys

  const accountNames = accounts.map((account) => account.name).join('\0')

  useEffect(() => {
    if (!namesAtLogin.current || pendingLogin) return
    const freshName = accountNames.split('\0').find((name) => name.length > 0 && !namesAtLogin.current?.includes(name))
    if (!freshName) return
    namesAtLogin.current = null
    setSelectedName(freshName)
    setChoosing(false)
  }, [accountNames, pendingLogin])

  let usageReason = ''
  if (usage && !usage.callable) {
    if (readyAccounts.length === 0) usageReason = t('proxyServices.usageNeedLogin')
    else if (!keysQuery.isLoading && clientKeys.length === 0) usageReason = t('proxyServices.usageNeedKey')
    else usageReason = t('proxyServices.usageNeedModels')
  }

  const deletingKeyTail = deletingKey ? apiKeyTail(deletingKey) : null

  function startLogin(provider: ProxyLoginProvider) {
    namesAtLogin.current = accounts.map((account) => account.name)
    loginMutation.mutate(provider)
  }

  return (
    <>

      {statusQuery.isLoading ? <p className="text-sm text-admin-muted">{t('loading')}</p> : null}
      {statusQuery.isError ? (
        <p className="text-sm text-danger-emphasis">
          {t('error')}{' '}
          <button type="button" className="underline" onClick={() => statusQuery.refetch()}>
            {t('retry')}
          </button>
        </p>
      ) : null}

      {status && !status.reachable ? (
        <section className="admin-content-data rounded-xl border border-dashed border-admin bg-admin-surface px-6 py-8">
          <p className="text-base font-medium text-admin">{t('proxyServices.unreachable')}</p>
          <p className="mt-2 font-mono text-sm text-admin-muted">{status.baseUrl || t('proxyServices.port')}</p>
          <p className="mt-2 text-sm text-admin-muted">{t('proxyServices.unreachableHint')}</p>
        </section>
      ) : null}

      {status?.reachable && !status.managementConfigured ? (
        <p className="text-sm text-warning-emphasis text-warning-emphasis">{t('proxyServices.managementOff')}</p>
      ) : null}

      {canManage && status ? (
        <>
          <section className="overflow-hidden rounded-xl border border-admin bg-admin-surface">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-admin px-4 py-3">
              <div className="flex min-w-0 items-center gap-3">
                <span className="shrink-0 text-xs font-semibold text-admin-muted">{t('proxyServices.callTitle')}</span>
                {apiBaseUrl ? (
                  <span className="inline-flex min-w-0 items-center gap-2 rounded-lg border border-admin bg-admin-surface-muted py-1 pl-3 pr-1.5">
                    <CopyableText value={apiBaseUrl} className="min-w-0 font-mono text-sm" />
                  </span>
                ) : (
                  <span className="text-sm text-admin-muted">{t('proxyServices.port')}</span>
                )}
              </div>
              {usageQuery.isLoading ? (
                <span className="text-sm text-admin-muted">{t('loading')}</span>
              ) : (
                <StatusTag
                  status={usage?.callable ? 'active' : 'warning'}
                  tone={usage?.callable ? 'active' : 'warning'}
                  label={usage?.callable ? t('proxyServices.usageReady') : t('proxyServices.usageNotReady')}
                />
              )}
            </div>
            {usageReason ? (
              <div className="mx-4 mt-3 flex items-start gap-2.5 rounded-xl border border-warning/30 bg-warning/[0.09] px-3.5 py-2.5 text-sm text-admin">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning" />
                <p>{usageReason}</p>
              </div>
            ) : null}
            <PacingStrip pacing={status.accountPacing} t={t} />

          <div className="grid items-stretch lg:h-[calc(100vh-15.5rem)] lg:grid-cols-[15rem_minmax(0,1fr)]">
            <div className="border-b border-admin lg:border-b-0 lg:border-r lg:border-admin">
            <div className="flex max-h-80 flex-col gap-0.5 overflow-auto p-2 lg:h-full lg:max-h-none">
              {accounts.map((account) => {
                const statusLabel = accountStatus(account, t)
                const active = selected?.name === account.name
                return (
                  <button
                    key={account.name}
                    type="button"
                    aria-pressed={active}
                    onClick={() => setSelectedName(account.name)}
                    className={cn(
                      'flex w-full items-center gap-3 rounded-[10px] border border-transparent px-2.5 py-2 text-left transition hover:bg-admin-surface-muted admin-focus-ring',
                      active && 'admin-active-inset border-admin bg-admin-surface shadow-sm',
                    )}
                  >
                    <span
                      className={cn(
                        'flex h-9 w-9 shrink-0 items-center justify-center rounded-[10px]',
                        isLoginProvider(account.provider) ? providerTileClass(account.provider) : 'bg-admin-surface-muted text-admin-muted',
                      )}
                    >
                      {isLoginProvider(account.provider) ? (
                        <ProviderIcon id={account.provider} className="h-5 w-5" />
                      ) : (
                        <span className="text-xs font-semibold">{account.provider.slice(0, 1).toUpperCase()}</span>
                      )}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center gap-1.5 truncate text-sm font-semibold text-admin">
                        {providerLabel(account.provider, t)}
                        {statusLabel.tone === 'active' ? (
                          <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-admin-success" />
                        ) : (
                          <StatusTag status={statusLabel.tone} tone={statusLabel.tone} label={statusLabel.label} />
                        )}
                      </span>
                      <span className="mt-0.5 block truncate text-xs text-admin-muted">{accountTitle(account)}</span>
                    </span>
                  </button>
                )
              })}
              <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
                <button
                  type="button"
                  onClick={() => setChoosing(true)}
                  className="admin-control admin-focus-ring mt-1 flex w-full items-center justify-center gap-1.5 rounded-[10px] border border-dashed border-admin px-3 text-sm font-medium text-admin-muted transition hover:border-primary hover:bg-primary/[0.04] hover:text-primary"
                >
                  <Plus className="h-4 w-4" />
                  {t('proxyServices.addAccount')}
                </button>
              </PermissionGate>
            </div>
            {accounts.length === 0 ? <p className="mt-3 text-sm text-admin-muted">{t('proxyServices.emptyAccounts')}</p> : null}
            {pendingLogin && !choosing ? (
              <div className="mt-3 rounded-[10px] border border-warning/30 bg-warning/[0.09] px-3 py-2.5 text-sm leading-relaxed text-admin">
                <p className="flex items-center gap-2 font-medium">
                  <span className="h-2 w-2 shrink-0 animate-pulse rounded-full bg-warning" />
                  {providerLabel(pendingLogin.provider, t)} {t('proxyServices.loginWaiting')}
                </p>
                <a
                  className="mt-1 inline-flex items-center gap-1 font-medium text-primary"
                  href={pendingLogin.url}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  <ExternalLink className="h-3.5 w-3.5" />
                  {t('proxyServices.loginOpen')}
                </a>
              </div>
            ) : null}
            </div>
            <div className="flex min-h-0 min-w-0 flex-col lg:h-full">
              {selected ? (
                <>
                  <div className="flex flex-wrap items-center gap-3 border-b border-admin px-4 py-3 text-sm">
                    <span className="min-w-0 flex-1 truncate font-medium text-admin">
                      {providerLabel(selected.provider, t)}
                      <span className="ml-2 font-normal text-admin-muted">{accountTitle(selected)}</span>
                    </span>
                    <div className="flex shrink-0 gap-2">
                      <div className="rounded-lg bg-admin-surface-muted px-3 py-1.5 text-right">
                        <p className="text-base font-bold tabular-nums text-success-emphasis">{selected.success}</p>
                        <p className="text-[11px] text-admin-muted">{t('proxyServices.statSuccess')}</p>
                      </div>
                      <div className="rounded-lg bg-admin-surface-muted px-3 py-1.5 text-right">
                        <p className="text-base font-bold tabular-nums text-danger-emphasis">{selected.failed}</p>
                        <p className="text-[11px] text-admin-muted">{t('proxyServices.statFailed')}</p>
                      </div>
                      <div className="rounded-lg bg-admin-surface-muted px-3 py-1.5 text-right">
                        <p className="text-base font-bold tabular-nums text-admin">
                          {selected.usedPercent != null ? `${selected.usedPercent}%` : t('proxyServices.quotaUnknown')}
                        </p>
                        <p className="text-[11px] text-admin-muted">{t('proxyServices.statQuota')}</p>
                      </div>
                    </div>
                  </div>
                  <div className="min-h-0 flex-1 overflow-hidden">
                    <ModelList account={selected} />
                  </div>
                </>
              ) : (
                <p className="px-4 py-8 text-sm text-admin-muted">{t('proxyServices.emptyAccounts')}</p>
              )}
              <div className="shrink-0 border-t border-admin px-4 py-3">
                <div className="mb-2 flex items-center justify-between gap-3">
                  <span className="text-sm font-medium text-admin">{t('proxyServices.usageKeysTitle')}</span>
                  <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
                    <button type="button" className={adminSecondaryButtonClass} onClick={() => setCreating(true)}>
                      <Plus className="h-4 w-4" />
                      {t('proxyServices.create')}
                    </button>
                  </PermissionGate>
                </div>
                {keysQuery.isLoading ? <p className="text-sm text-admin-muted">{t('loading')}</p> : null}
                {keysQuery.isError ? (
                  <p className="text-sm text-danger-emphasis">
                    {t('error')}{' '}
                    <button type="button" className="underline" onClick={() => keysQuery.refetch()}>
                      {t('retry')}
                    </button>
                  </p>
                ) : null}
                {!keysQuery.isLoading && !keysQuery.isError && visibleKeys.length === 0 ? (
                  <p className="text-sm text-admin-muted">{t('proxyServices.empty')}</p>
                ) : null}
                {visibleKeys.length > 0 ? (
                  <ul className="max-h-32 overflow-auto">
                    {visibleKeys.map((apiKey) => (
                      <li key={apiKey} className="flex items-center gap-3 py-1.5">
                        <span className="flex min-w-0 flex-1 items-center gap-1 rounded-lg border border-admin bg-admin-surface-muted py-1 pl-3 pr-1">
                          <span className="min-w-0 flex-1 truncate font-mono text-[13px]">{maskApiKey(apiKey)}</span>
                          <ApiKeyCopyButton value={apiKey} />
                        </span>
                        {canUpdate ? (
                          <button
                            type="button"
                            className={cn(adminDangerGhostButtonClass, 'shrink-0')}
                            onClick={() => setDeletingKey(apiKey)}
                          >
                            {t('proxyServices.delete')}
                          </button>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                ) : null}
                {createdKey ? (
                  <p className="mt-1 flex items-center gap-1.5 text-xs text-success-emphasis">
                    <Check className="h-3.5 w-3.5" />
                    {t('proxyServices.createdHint')}
                  </p>
                ) : null}
              </div>
            </div>
          </div>
          </section>
        </>
      ) : null}

      {choosing ? (
        <FormDialog
          title={t('proxyServices.chooseTitle')}
          description={pendingLogin ? undefined : t('proxyServices.chooseHint')}
          closeLabel={t('proxyServices.close')}
          onClose={() => setChoosing(false)}
        >
          {pendingLogin ? (
            <div className="flex flex-col items-start gap-3">
              {isLoginProvider(pendingLogin.provider) ? (
                <ProviderIcon id={pendingLogin.provider} className="h-8 w-8 text-admin" />
              ) : null}
              <p className="text-sm text-admin">{providerLabel(pendingLogin.provider, t)}</p>
              <p className="flex items-center gap-2 text-sm text-admin-muted">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-warning" />
                {t('proxyServices.loginWaiting')}
              </p>
              <a className={adminSecondaryButtonClass} href={pendingLogin.url} target="_blank" rel="noopener noreferrer">
                <ExternalLink className="h-4 w-4" />
                {t('proxyServices.loginOpen')}
              </a>
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
              {LOGIN_OPTIONS.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  disabled={loginMutation.isPending || pendingLogin !== null}
                  onClick={() => startLogin(option.id)}
                  className="flex flex-col items-center gap-2 rounded-xl border border-admin bg-admin-surface px-2 py-4 text-admin transition hover:border-primary/55 hover:shadow-sm disabled:opacity-60"
                >
                  <span className={cn('flex h-[42px] w-[42px] items-center justify-center rounded-xl', providerTileClass(option.id))}>
                    <ProviderIcon id={option.id} className="h-[21px] w-[21px]" />
                  </span>
                  <span className="text-sm font-semibold">{t(option.titleKey)}</span>
                  <span className="text-center text-xs text-admin-muted">{t(option.noteKey)}</span>
                </button>
              ))}
            </div>
          )}
          <p className="mt-4 text-xs text-admin-muted">{t('proxyServices.loginCursor')}</p>
        </FormDialog>
      ) : null}

      {creating ? (
        <FormDialog
          title={t('proxyServices.dialog.title')}
          description={t('proxyServices.dialog.description')}
          closeLabel={t('proxyServices.close')}
          onClose={() => {
            if (createMutation.isPending) return
            setCreating(false)
            setDraftKey('')
          }}
        >
          <form
            onSubmit={(event) => {
              event.preventDefault()
              createMutation.mutate(draftKey.trim())
            }}
          >
            <label className="block text-sm text-admin">
              {t('proxyServices.dialog.key')}
              <input
                className={`${adminFieldClass} mt-1`}
                value={draftKey}
                autoComplete="off"
                placeholder={t('proxyServices.dialog.placeholder')}
                onChange={(event) => setDraftKey(event.target.value)}
              />
            </label>
            <div className={adminFormActionsClass}>
              <button
                type="button"
                className={adminSecondaryButtonClass}
                onClick={() => {
                  setCreating(false)
                  setDraftKey('')
                }}
              >
                {t('proxyServices.dialog.cancel')}
              </button>
              <button type="submit" className={adminPrimaryButtonClass} disabled={createMutation.isPending}>
                {t('proxyServices.dialog.save')}
              </button>
            </div>
          </form>
        </FormDialog>
      ) : null}

      {deletingKey ? (
        <FormDialog
          title={t('proxyServices.deleteKeyTitle')}
          closeLabel={t('proxyServices.dialog.cancel')}
          onClose={() => setDeletingKey(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {deletingKeyTail
              ? t('proxyServices.deleteKeyBody', { tail: deletingKeyTail })
              : t('proxyServices.deleteKeyBodyGeneric')}
          </p>
          <div className={adminFormActionsClass}>
            <button type="button" className={adminSecondaryButtonClass} onClick={() => setDeletingKey(null)}>
              {t('proxyServices.dialog.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={deleteMutation.isPending}
              onClick={() =>
                deleteMutation.mutate(deletingKey, { onSuccess: () => setDeletingKey(null) })
              }
            >
              {t('proxyServices.delete')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </>
  )
}

function ModelList({ account }: { account: ShownAccount }) {
  const { t } = useTranslation('aiAdmin')
  const modelIds = shownModelIds(account.provider, account.modelIds)
  if (modelIds == null) {
    return (
      <p className="text-sm text-admin-muted">
        {modelIds === undefined ? t('proxyServices.modelsLoading') : t('proxyServices.modelsMissing')}
      </p>
    )
  }
  if (modelIds.length === 0) {
    return <p className="text-sm text-admin-muted">{t('proxyServices.modelsNone')}</p>
  }
  return (
    <div className="h-full max-h-80 overflow-auto lg:max-h-none">
      <table className="min-w-full text-left text-sm">
        <thead className="sticky top-0 z-10 bg-admin-surface-muted text-[11.5px] font-semibold tracking-wide text-admin-muted">
          <tr>
            <th className="px-4 py-2.5 text-left">{t('proxyServices.columnModel')}</th>
            <th className="w-[190px] px-4 py-2.5 text-left">{t('proxyServices.statQuota')}</th>
            <th className="w-16 px-4 py-2.5 text-right">{t('proxyServices.columnCopy')}</th>
          </tr>
        </thead>
        <tbody>
          {modelIds.map((id) => {
            const usedPercent = account.modelUsedPercent[id]
            const isHigh = usedPercent != null && usedPercent >= 85
            return (
              <tr key={id} className="border-t border-admin hover:bg-admin-surface-muted">
                <td className="px-4 py-2 font-mono text-sm text-admin">{id}</td>
                <td className="px-4 py-2">
                  {usedPercent != null ? (
                    <div className="flex items-center gap-2">
                      <span className="h-[5px] w-[86px] shrink-0 overflow-hidden rounded-full bg-[rgb(var(--muted-foreground)/.1)]">
                        <span
                          className={cn('block h-full rounded-full', isHigh ? 'bg-warning' : 'bg-chart-1')}
                          style={{ width: `${Math.min(usedPercent, 100)}%` }}
                        />
                      </span>
                      <span className={cn('text-xs tabular-nums', isHigh ? 'font-semibold text-warning' : 'text-admin-muted')}>
                        {usedPercent}%
                      </span>
                    </div>
                  ) : (
                    <span className="text-xs text-admin-muted">—</span>
                  )}
                </td>
                <td className="px-4 py-2 text-right">
                  <CopyableText value={id} className="ml-auto [&>span]:sr-only" />
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
