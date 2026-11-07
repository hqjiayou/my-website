import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, AlertTriangle, Check, Copy, ExternalLink, Plus } from 'lucide-react'
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
  adminSecondaryButtonSmClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type { ProxyLoginProvider, ProxyServicesAccountPacing } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

import { ProviderIcon } from './providerIcon'
import {
  LOGIN_OPTIONS,
  isLoginProvider,
  isReady,
  providerLabel,
  providerTileClass,
} from './proxyServicesShared'

type PendingLogin = { provider: ProxyLoginProvider; url: string; state: string }

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
 * CLI 代理登录流程（模型页「添加 → 添加 CLI 账号」打开）：供应商选择弹窗、
 * 跳转上游登录、轮询登录结果；登录成功后失效代理与条目缓存并触发一次
 * 派生同步，新账号的厂商页签与派生条目随之出现。原为代理面板内嵌流程，
 * 模型页改版（双模式页签）后抽为页级组件。
 */
export function ProxyLoginFlow({
  open,
  provider,
  onClose,
}: {
  open: boolean
  /** 给定供应商时打开即直接发起该供应商的登录（账号面板的「重新登录」）；null 时先选供应商。 */
  provider?: ProxyLoginProvider | null
  onClose: () => void
}) {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const [pendingLogin, setPendingLogin] = useState<PendingLogin | null>(null)

  const loginMutation = useMutation({
    mutationFn: (loginProvider: ProxyLoginProvider) => aiApi.startProxyLogin(loginProvider),
    onSuccess: (started) => {
      setPendingLogin({ provider: started.provider, url: started.url, state: started.state })
      window.open(started.url, '_blank', 'noopener,noreferrer')
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const { mutate: startLoginMutate } = loginMutation
  const autoStartedFor = useRef<string | null>(null)
  useEffect(() => {
    if (!open) {
      autoStartedFor.current = null
      return
    }
    if (provider && autoStartedFor.current !== provider) {
      autoStartedFor.current = provider
      startLoginMutate(provider)
    }
  }, [open, provider, startLoginMutate])

  useEffect(() => {
    if (!pendingLogin) return
    let stopped = false
    const startedAt = Date.now()
    const timer = window.setInterval(() => {
      if (Date.now() - startedAt > LOGIN_TIMEOUT_MS) {
        window.clearInterval(timer)
        if (!stopped) {
          setPendingLogin(null)
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
            void aiApi
              .syncCliModelEntries()
              .then(() => queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] }))
              .catch(() => {
                // 同步失败静默：下次打开模型页会再同步。
              })
            onClose()
            return
          }
          toast.error(result.error || t('proxyServices.loginError'))
        })
        .catch((error: Error) => {
          if (stopped) return
          window.clearInterval(timer)
          setPendingLogin(null)
          toast.error(error.message)
        })
    }, 2000)
    return () => {
      stopped = true
      window.clearInterval(timer)
    }
  }, [pendingLogin, queryClient, t, onClose])

  if (!open) return null

  return (
    <FormDialog
      title={t('proxyServices.chooseTitle')}
      description={pendingLogin ? undefined : t('proxyServices.chooseHint')}
      closeLabel={t('proxyServices.close')}
      onClose={onClose}
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
              onClick={() => loginMutation.mutate(option.id)}
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
  )
}

/**
 * 客户端 key 面板（模型页 CLI 模式左栏末位页签的内容）：代理调用地址与
 * 就绪状态、pacing 条、客户端 key 的登记/删除（列表脱敏）。账号本身的
 * 管理（登录、派生条目）在厂商页签里，本面板只管调用凭证与网关状态。
 */
export function ProxyClientKeysPane() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [creating, setCreating] = useState(false)
  const [draftKey, setDraftKey] = useState('')
  const [createdKey, setCreatedKey] = useState<string | null>(null)
  const [deletingKey, setDeletingKey] = useState<string | null>(null)

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
  const clientKeys = keysQuery.data?.apiKeys ?? []
  const readyAccounts = (status?.accounts ?? []).filter(isReady)
  const visibleKeys = createdKey && !clientKeys.includes(createdKey) ? [createdKey, ...clientKeys] : clientKeys

  let usageReason = ''
  if (usage && !usage.callable) {
    if (readyAccounts.length === 0) usageReason = t('proxyServices.usageNeedLogin')
    else if (!keysQuery.isLoading && clientKeys.length === 0) usageReason = t('proxyServices.usageNeedKey')
    else usageReason = t('proxyServices.usageNeedModels')
  }

  const deletingKeyTail = deletingKey ? apiKeyTail(deletingKey) : null

  return (
    <>
      {statusQuery.isLoading ? (
        <div className="space-y-3 px-4 py-4" aria-hidden>
          <div className="admin-skeleton h-5 w-56" />
          <div className="admin-skeleton h-16 w-full" />
          <div className="admin-skeleton h-5 w-40" />
        </div>
      ) : null}
      {statusQuery.isError ? (
        <div className="px-4 py-4">
          <div className="flex items-start gap-3 rounded-xl border border-danger/35 bg-danger-muted px-4 py-3">
            <AlertCircle className="mt-0.5 h-[18px] w-[18px] shrink-0 text-danger-emphasis" aria-hidden />
            <div className="min-w-0 flex-1">
              <p className="text-sm font-medium text-danger-emphasis">{t('error')}</p>
              <button type="button" className={cn(adminSecondaryButtonSmClass, 'mt-2')} onClick={() => void statusQuery.refetch()}>
                {t('retry')}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {status && !status.reachable ? (
        <div className="px-4 py-6">
          <p className="text-base font-medium text-admin">{t('proxyServices.unreachable')}</p>
          <p className="mt-2 font-mono text-sm text-admin-muted">{status.baseUrl || t('proxyServices.port')}</p>
          <p className="mt-2 text-sm text-admin-muted">{t('proxyServices.unreachableHint')}</p>
        </div>
      ) : null}

      {status?.reachable && !status.managementConfigured ? (
        <p className="px-4 py-4 text-sm text-warning-emphasis">{t('proxyServices.managementOff')}</p>
      ) : null}

      {canManage && status ? (
        <>
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
          <div className="px-4 py-3">
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
                <button type="button" className="underline" onClick={() => void keysQuery.refetch()}>
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
        </>
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
            <label className="block text-xs font-medium text-admin-muted">
              {t('proxyServices.dialog.key')}
              <input
                className={`${adminFieldClass} mt-1.5`}
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
              onClick={() => deleteMutation.mutate(deletingKey, { onSuccess: () => setDeletingKey(null) })}
            >
              {t('proxyServices.delete')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </>
  )
}
