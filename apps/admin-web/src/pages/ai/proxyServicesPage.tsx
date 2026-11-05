import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, Check, ExternalLink, Plus } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { agentApi } from '@/api/apps/agentApi'
import { aiApi } from '@/api/apps/aiApi'
import { AdminPageHeader, CopyableText, FormDialog, StatusTag } from '@/components/admin'
import {
  adminDangerGhostButtonClass,
  adminFieldClass,
  adminFormActionsClass,
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

export function ProxyServicesPage() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [creating, setCreating] = useState(false)
  const [choosing, setChoosing] = useState(false)
  const [draftKey, setDraftKey] = useState('')
  const [createdKey, setCreatedKey] = useState<string | null>(null)
  const [pendingLogin, setPendingLogin] = useState<PendingLogin | null>(null)
  const [selectedName, setSelectedName] = useState<string | null>(null)
  const [panel, setPanel] = useState<'cli' | 'key'>('cli')
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

  function startLogin(provider: ProxyLoginProvider) {
    namesAtLogin.current = accounts.map((account) => account.name)
    loginMutation.mutate(provider)
  }

  return (
    <div className="space-y-6">
      <AdminPageHeader
        className="mb-0"
        title={t('proxyServices.title')}
        description={t('proxyServices.description')}
        action={
          status ? (
            status.reachable ? (
              <span className="admin-status-tag inline-flex items-center gap-1.5 rounded-full bg-admin-success-muted px-2 text-xs font-medium text-admin-success">
                <span className="h-1.5 w-1.5 shrink-0 animate-pulse rounded-full bg-admin-success" />
                {t('proxyServices.reachable')}
              </span>
            ) : (
              <StatusTag status="warning" tone="warning" label={t('proxyServices.unreachable')} />
            )
          ) : null
        }
      />

      <div className="inline-flex rounded-[10px] bg-admin-surface-muted p-[3px]" role="tablist">
        {(
          [
            ['cli', t('proxyServices.cliTitle')],
            ['key', t('proxyServices.keyTitle')],
          ] as const
        ).map(([key, label]) => {
          const active = panel === key
          return (
            <button
              key={key}
              type="button"
              role="tab"
              aria-selected={active}
              onClick={() => setPanel(key)}
              className={cn(
                'rounded-md px-3 py-1.5 text-sm font-medium transition admin-focus-ring',
                active ? 'bg-admin-surface font-semibold text-admin shadow-sm' : 'text-admin-muted hover:text-admin',
              )}
            >
              {label}
            </button>
          )
        })}
      </div>

      {panel === 'key' ? <ApiKeyColumn /> : null}

      {panel === 'cli' && statusQuery.isLoading ? <p className="text-sm text-admin-muted">{t('loading')}</p> : null}
      {panel === 'cli' && statusQuery.isError ? (
        <p className="text-sm text-danger-emphasis">
          {t('error')}{' '}
          <button type="button" className="underline" onClick={() => statusQuery.refetch()}>
            {t('retry')}
          </button>
        </p>
      ) : null}

      {panel === 'cli' && status && !status.reachable ? (
        <section className="admin-content-data rounded-xl border border-dashed border-admin bg-admin-surface px-6 py-8">
          <p className="text-base font-medium text-admin">{t('proxyServices.unreachable')}</p>
          <p className="mt-2 font-mono text-sm text-admin-muted">{status.baseUrl || t('proxyServices.port')}</p>
          <p className="mt-2 text-sm text-admin-muted">{t('proxyServices.unreachableHint')}</p>
        </section>
      ) : null}

      {panel === 'cli' && status?.reachable && !status.managementConfigured ? (
        <p className="text-sm text-warning-emphasis text-warning-emphasis">{t('proxyServices.managementOff')}</p>
      ) : null}

      {panel === 'cli' && canManage && status ? (
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
                        <span className="flex min-w-0 flex-1 items-center rounded-lg border border-admin bg-admin-surface-muted py-1 pl-3 pr-1">
                          <CopyableText value={apiKey} className="min-w-0 flex-1 font-mono text-sm" />
                        </span>
                        {canUpdate ? (
                          <button
                            type="button"
                            className={cn(adminDangerGhostButtonClass, 'shrink-0')}
                            onClick={() => {
                              if (!window.confirm(t('proxyServices.confirmDelete'))) return
                              deleteMutation.mutate(apiKey)
                            }}
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
    </div>
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

function ApiKeyColumn() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [creating, setCreating] = useState(false)
  const [displayName, setDisplayName] = useState('')
  const [modelSpec, setModelSpec] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [selectedId, setSelectedId] = useState<number | null>(null)

  const entriesQuery = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
  })
  const agentsQuery = useQuery({
    queryKey: ['agents', 'names-for-keys'],
    queryFn: () => agentApi.listSites({ pageNum: 1, pageSize: 100 }),
    retry: false,
  })
  const entries = (entriesQuery.data ?? []).filter((entry) => (entry.mode ?? 'KEY') === 'KEY')
  const selected = entries.find((entry) => entry.id === selectedId) ?? entries[0] ?? null

  function labelFor(agentId: number | null) {
    if (agentId == null) return t('proxyServices.keyUnbound')
    const name = agentsQuery.data?.records.find((agent) => agent.id === agentId)?.agentName
    return name ? t('proxyServices.keyBound', { name }) : t('proxyServices.keyBoundUnknown')
  }

  function resetDraft() {
    setDisplayName('')
    setModelSpec('')
    setBaseUrl('')
    setApiKey('')
  }

  const createMutation = useMutation({
    mutationFn: () =>
      aiApi.createModelEntry({
        displayName: displayName.trim() || modelSpec.trim(),
        modelSpec: modelSpec.trim(),
        apiKey: apiKey.trim(),
        baseUrl: baseUrl.trim() || null,
        embeddingSpec: null,
        enabled: true,
        defaultEntry: false,
        mode: 'KEY',
        alias: null,
      }),
    onSuccess: (created) => {
      toast.success(t('proxyServices.keySaved'))
      setCreating(false)
      resetDraft()
      setSelectedId(created.id)
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => aiApi.deleteModelEntry(id),
    onSuccess: () => {
      toast.success(t('proxyServices.keyDeleted'))
      setSelectedId(null)
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  return (
    <section className="overflow-hidden rounded-xl border border-admin bg-admin-surface">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-admin px-4 py-3">
        <span className="min-w-0 text-sm text-admin-muted">{t('proxyServices.keyHint')}</span>
        <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
          <button type="button" className={adminPrimaryButtonClass} onClick={() => setCreating(true)}>
            <Plus className="h-4 w-4" />
            {t('proxyServices.keyCreate')}
          </button>
        </PermissionGate>
      </div>
      {entriesQuery.isLoading ? <p className="px-4 py-6 text-sm text-admin-muted">{t('loading')}</p> : null}
      {entriesQuery.isError ? (
        <p className="px-4 py-6 text-sm text-danger-emphasis">
          {t('error')}{' '}
          <button type="button" className="underline" onClick={() => entriesQuery.refetch()}>
            {t('retry')}
          </button>
        </p>
      ) : null}
      {!entriesQuery.isLoading && !entriesQuery.isError && entries.length === 0 ? (
        <p className="px-4 py-10 text-sm text-admin-muted">{t('proxyServices.keyEmpty')}</p>
      ) : null}
      {entries.length > 0 ? (
        <div className="grid lg:grid-cols-[15rem_minmax(0,1fr)]">
          <div className="flex flex-col gap-0.5 border-b border-admin p-2 lg:border-b-0 lg:border-r lg:border-admin">
            {entries.map((entry) => {
              const active = selected?.id === entry.id
              return (
                <button
                  key={entry.id}
                  type="button"
                  aria-pressed={active}
                  onClick={() => setSelectedId(entry.id)}
                  className={cn(
                    'flex w-full flex-col items-start gap-0.5 rounded-[10px] border border-transparent px-2.5 py-2 text-left transition hover:bg-admin-surface-muted admin-focus-ring',
                    active && 'admin-active-inset border-admin bg-admin-surface shadow-sm',
                  )}
                >
                  <span className="w-full truncate text-sm font-semibold text-admin">{entry.displayName}</span>
                  <span className="w-full truncate font-mono text-xs text-admin-muted">{entry.modelSpec}</span>
                </button>
              )
            })}
          </div>
          {selected ? (
            <div className="px-5 py-4">
              <h2 className="text-sm font-medium text-admin">{selected.displayName}</h2>
              <dl className="mt-4 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-4 gap-y-3 text-sm">
                <dt className="text-admin-muted">{t('proxyServices.keySpec')}</dt>
                <dd className="break-all font-mono font-medium text-admin">{selected.modelSpec}</dd>
                <dt className="text-admin-muted">{t('proxyServices.keySecret')}</dt>
                <dd className="font-mono font-medium text-admin">{selected.apiKeyHint || '—'}</dd>
                <dt className="text-admin-muted">{t('proxyServices.keyBaseUrl')}</dt>
                <dd className="break-all font-medium text-admin">{selected.baseUrl || '—'}</dd>
                <dt className="text-admin-muted">{t('proxyServices.keyUse')}</dt>
                <dd className="font-medium text-admin">
                  {selected.sourceAgentId != null ? labelFor(selected.sourceAgentId) : t('proxyServices.keyPlatform')}
                </dd>
              </dl>
              {canUpdate ? (
                <div className="mt-6 flex justify-end">
                  <button
                    type="button"
                    className={adminDangerGhostButtonClass}
                    onClick={() => {
                      if (!window.confirm(t('proxyServices.keyConfirmDelete'))) return
                      deleteMutation.mutate(selected.id)
                    }}
                  >
                    {t('proxyServices.delete')}
                  </button>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}
      {creating ? (
        <FormDialog
          title={t('proxyServices.keyDialogTitle')}
          description={t('proxyServices.keyDialogDescription')}
          closeLabel={t('proxyServices.close')}
          onClose={() => {
            if (createMutation.isPending) return
            setCreating(false)
            resetDraft()
          }}
        >
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              if (!modelSpec.trim() || !apiKey.trim()) return
              createMutation.mutate()
            }}
          >
            <label className="block text-sm text-admin">
              {t('proxyServices.keyName')}
              <input className={`${adminFieldClass} mt-1`} value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
            </label>
            <label className="block text-sm text-admin">
              {t('proxyServices.keySpec')}
              <input
                className={`${adminFieldClass} mt-1`}
                value={modelSpec}
                placeholder={t('proxyServices.keySpecHint')}
                onChange={(event) => setModelSpec(event.target.value)}
                required
              />
            </label>
            <label className="block text-sm text-admin">
              {t('proxyServices.keyBaseUrl')}
              <input
                className={`${adminFieldClass} mt-1`}
                value={baseUrl}
                placeholder={t('proxyServices.keyBaseUrlHint')}
                onChange={(event) => setBaseUrl(event.target.value)}
              />
            </label>
            <label className="block text-sm text-admin">
              {t('proxyServices.keySecret')}
              <input
                className={`${adminFieldClass} mt-1`}
                type="password"
                autoComplete="new-password"
                value={apiKey}
                onChange={(event) => setApiKey(event.target.value)}
                required
              />
            </label>
            <div className={adminFormActionsClass}>
              <button
                type="button"
                className={adminSecondaryButtonClass}
                onClick={() => {
                  setCreating(false)
                  resetDraft()
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
    </section>
  )
}
