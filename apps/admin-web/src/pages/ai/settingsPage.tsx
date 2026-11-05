import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Info, KeyRound, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { AdminPageHeader, CopyableText, FormDialog, StatusTag } from '@/components/admin'
import {
  adminDangerGhostButtonClass,
  adminDangerGhostButtonSmClass,
  adminFieldClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type {
  AiModelEntry,
  AiModelEntryWriteInput,
  AiRuntimeConfigUpdateInput,
  GatewayAccessKey,
  GatewayAccessKeyIssueResponse,
} from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'
import { modelIdFromSpec } from '@/utils/modelSpec'

import { ChainPresetsCard } from './chainPresetsCard'
import { EmbeddingRegistrationCard } from './embeddingRegistrationCard'
import { ExperienceLessonsCard } from './experienceLessonsCard'
import { McpServersCard } from './mcpServersCard'

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

type GatewayKeyStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'

function gatewayKeyStatus(key: GatewayAccessKey): GatewayKeyStatus {
  if (key.revokedAt) return 'REVOKED'
  if (key.expiresAt && new Date(key.expiresAt).getTime() <= Date.now()) return 'EXPIRED'
  return 'ACTIVE'
}

/** 网关 Key 明文固定以此为前缀签发（见 AdminGatewayAccessKeyServiceImpl.KEY_PREFIX），keyHint 只是尾 4 位。 */
const GATEWAY_KEY_PREFIX = 'mwgw_'

/** 家族图标砖：按 modelSpec 协议前缀映射色板 token，字母取家族名首字母。前缀未知时归为「其他」。 */
function familyTile(modelSpec: string): { letter: string; className: string } {
  const prefix = modelSpec.split(':')[0]?.trim().toLowerCase() ?? ''
  if (prefix === 'google') return { letter: 'G', className: 'bg-[rgb(var(--info)/.12)] text-info' }
  if (prefix === 'openai') return { letter: 'G', className: 'bg-[rgb(var(--chart-2)/.12)] text-chart-2' }
  if (prefix === 'anthropic') return { letter: 'C', className: 'bg-[rgb(var(--chart-3)/.12)] text-chart-3' }
  if (prefix === 'azure') return { letter: 'A', className: 'bg-[rgb(var(--chart-1)/.12)] text-chart-1' }
  return { letter: 'M', className: 'bg-admin-surface-muted text-admin-muted' }
}

const GATEWAY_KEY_STATUS_TONE: Record<GatewayKeyStatus, 'active' | 'danger' | 'warning'> = {
  ACTIVE: 'active',
  REVOKED: 'danger',
  EXPIRED: 'warning',
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
    <article className="space-y-3 rounded-xl border border-admin bg-admin-surface p-5">
      <h3 className="text-sm font-semibold text-admin">{t('settings.gatewayKeys.title')}</h3>
      <p className="text-sm text-admin-muted">{t('settings.gatewayKeys.description')}</p>

      {issuedKey ? (
        <div className="rounded-lg border border-warning/35 bg-warning-muted px-4 py-3 text-sm text-warning-emphasis">
          <p className="font-medium">{t('settings.gatewayKeys.oneTimeWarning')}</p>
          <div className="mt-2 flex items-center gap-2 rounded bg-admin-surface px-3 py-2">
            <CopyableText value={issuedKey.apiKey} className="font-mono text-xs" />
          </div>
          <button
            type="button"
            className="mt-2 text-xs text-warning-emphasis underline"
            onClick={() => setIssuedKey(null)}
          >
            {t('settings.gatewayKeys.close')}
          </button>
        </div>
      ) : null}

      {isPending ? (
        <p className="text-sm text-admin-muted">{t('loading')}</p>
      ) : keys.length === 0 ? (
        <p className="text-sm text-admin-muted">{t('settings.gatewayKeys.empty')}</p>
      ) : (
        <ul className="space-y-2">
          {keys.map((key) => {
            const status = gatewayKeyStatus(key)
            return (
              <li
                key={key.id}
                className="flex items-center gap-3 rounded-lg border border-admin px-3 py-2.5 text-sm"
              >
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-admin-md bg-admin-surface-muted text-admin-muted">
                  <KeyRound className="h-4 w-4" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="truncate font-medium text-admin">{key.name}</span>
                    <span className="truncate font-mono text-xs text-admin-muted">
                      {GATEWAY_KEY_PREFIX}
                      {'•'.repeat(12)}
                      {key.keyHint}
                    </span>
                  </span>
                  <span className="mt-0.5 block text-xs text-admin-muted">
                    {t('settings.gatewayKeys.createdAtTag')} {new Date(key.createdAt).toLocaleString()} ·{' '}
                    {key.expiresAt
                      ? `${t('settings.gatewayKeys.expiresAtTag')} ${new Date(key.expiresAt).toLocaleString()}`
                      : t('settings.gatewayKeys.neverExpires')}
                  </span>
                </span>
                <StatusTag status={status} tone={GATEWAY_KEY_STATUS_TONE[status]} label={statusLabel[status]} />
                {canUpdate && status === 'ACTIVE' ? (
                  <button
                    type="button"
                    className={cn(adminDangerGhostButtonSmClass, 'shrink-0')}
                    disabled={revokeMutation.isPending}
                    onClick={() => {
                      if (!window.confirm(t('settings.gatewayKeys.confirmRevoke'))) return
                      revokeMutation.mutate(key.id)
                    }}
                  >
                    {t('settings.gatewayKeys.revoke')}
                  </button>
                ) : null}
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
          <label className="block text-sm text-admin">
            {t('settings.gatewayKeys.name')}
            <input
              className={`${adminFieldClass} mt-1`}
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </label>
          <label className="block text-sm text-admin">
            {t('settings.gatewayKeys.expiresAt')}
            <input
              type="datetime-local"
              className={`${adminFieldClass} mt-1`}
              value={expiresAt}
              onChange={(event) => setExpiresAt(event.target.value)}
            />
            <span className="mt-1 block text-xs text-admin-muted">
              {t('settings.gatewayKeys.expiresAtHint')}
            </span>
          </label>
          <button
            type="submit"
            disabled={issueMutation.isPending || !name.trim()}
            className={adminPrimaryButtonClass}
          >
            {issueMutation.isPending ? t('settings.gatewayKeys.issuing') : t('settings.gatewayKeys.issue')}
          </button>
        </form>
      </PermissionGate>
    </article>
  )
}

/**
 * agent-services 运行配置（Issue #146 UoW-3）：四项配置的真相源在服务端单行表，
 * 保存后由 Java 推送 Python 热替换。密钥字段只写不回填：DSN 只显示服务端脱敏形、
 * Gemini key 只显示末 4 位 hint；输入留空＝保留原值（后端语义）。
 * 推没推成功看 lastPush* 状态，不看保存结果本身。
 */
function RuntimeConfigCard() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  // 运行配置写接口后端校验 admin:ai-model:update，与模型入口的 read 口径不同，单独用 aiRuntimeConfigUpdate。
  const canUpdate = useHasPermission(PERMISSION_CODES.aiRuntimeConfigUpdate)
  const [dsn, setDsn] = useState('')
  const [embeddingModel, setEmbeddingModel] = useState('')
  const [dimensions, setDimensions] = useState('')
  const [geminiKey, setGeminiKey] = useState('')
  const [syncedVersion, setSyncedVersion] = useState<number | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['ai', 'runtime-config'],
    queryFn: () => aiApi.getRuntimeConfig(),
  })

  // 配置版本变化（首次加载、保存后回读）时把非密钥字段同步进表单；密钥字段永远清空、不回填。
  // 用渲染期调整而非 effect：版本不变时不覆盖用户正在编辑的输入。
  if (data && data.configVersion !== syncedVersion) {
    setEmbeddingModel(data.aiEmbeddingModel ?? '')
    setDimensions(data.aiEmbeddingDimensions == null ? '' : String(data.aiEmbeddingDimensions))
    setDsn('')
    setGeminiKey('')
    setSyncedVersion(data.configVersion)
  }

  const saveMutation = useMutation({
    mutationFn: (input: AiRuntimeConfigUpdateInput) => aiApi.updateRuntimeConfig(input),
    onSuccess: () => {
      toast.success(t('settings.saved'))
      setConfirmOpen(false)
      setDsn('')
      setGeminiKey('')
      void queryClient.invalidateQueries({ queryKey: ['ai', 'runtime-config'] })
    },
    onError: (saveError: Error) => toast.error(saveError.message),
  })

  const retryMutation = useMutation({
    mutationFn: () => aiApi.retryRuntimeConfigPush(),
    onSuccess: () => {
      toast.success(t('settings.runtimeConfig.retryDone'))
      void queryClient.invalidateQueries({ queryKey: ['ai', 'runtime-config'] })
    },
    onError: (retryError: Error) => toast.error(retryError.message),
  })

  /** 维度输入解析：空＝清空（null）；不是 1–10000 的整数返回 undefined 表示非法。 */
  function parsedDimensions(): number | null | undefined {
    const trimmed = dimensions.trim()
    if (trimmed === '') return null
    const value = Number(trimmed)
    if (!Number.isInteger(value) || value <= 0 || value > 10000) return undefined
    return value
  }

  function buildInput(parsed: number | null): AiRuntimeConfigUpdateInput {
    const input: AiRuntimeConfigUpdateInput = {
      aiEmbeddingModel: embeddingModel.trim() || null,
      aiEmbeddingDimensions: parsed,
    }
    if (dsn.trim()) input.retrievalDatabaseUrl = dsn.trim()
    if (geminiKey.trim()) input.geminiApiKey = geminiKey.trim()
    return input
  }

  function handleSubmit() {
    if (!canUpdate || !data) return
    const parsed = parsedDimensions()
    if (parsed === undefined) {
      toast.error(t('settings.runtimeConfig.dimensionsInvalid'))
      return
    }
    // 只有维度变更才弹确认：变更后存量索引要全量重嵌、完成前检索 BLOCKED。
    if (parsed !== data.aiEmbeddingDimensions) {
      setConfirmOpen(true)
      return
    }
    saveMutation.mutate(buildInput(parsed))
  }

  function confirmSave() {
    const parsed = parsedDimensions()
    if (parsed === undefined) {
      setConfirmOpen(false)
      toast.error(t('settings.runtimeConfig.dimensionsInvalid'))
      return
    }
    saveMutation.mutate(buildInput(parsed))
  }

  const pushStatus = data?.lastPushStatus ?? null
  const pendingDimensions = parsedDimensions()

  return (
    <article className="space-y-3 rounded-xl border border-admin bg-admin-surface p-5">
      <h3 className="text-sm font-semibold text-admin">{t('settings.runtimeConfig.title')}</h3>
      <p className="text-sm text-admin-muted">{t('settings.runtimeConfig.description')}</p>

      {isPending ? (
        <p className="text-sm text-admin-muted">{t('loading')}</p>
      ) : isError ? (
        <div className="rounded-lg border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis">
          <p>{error instanceof Error ? error.message : t('error')}</p>
          <button type="button" className="mt-2 underline" onClick={() => void refetch()}>
            {t('retry')}
          </button>
        </div>
      ) : data ? (
        <>
          <div className="space-y-2 rounded-lg border border-admin px-4 py-3">
            {data.configVersion === 0 ? (
              <p className="text-sm text-admin-muted">{t('settings.runtimeConfig.pushNone')}</p>
            ) : (
              <>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-medium text-admin">{t('settings.runtimeConfig.pushTitle')}</span>
                  {pushStatus === 'APPLIED' ? (
                    <StatusTag
                      status="APPLIED"
                      tone="active"
                      label={t('settings.runtimeConfig.pushApplied', { version: data.configVersion })}
                    />
                  ) : null}
                  {pushStatus === 'BLOCKED' ? (
                    <StatusTag status="BLOCKED" tone="warning" label={t('settings.runtimeConfig.pushBlocked')} />
                  ) : null}
                  {pushStatus === 'FAILED' ? (
                    <StatusTag status="FAILED" tone="danger" label={t('settings.runtimeConfig.pushFailed')} />
                  ) : null}
                  {data.lastPushedAt ? (
                    <span className="text-xs text-admin-muted">
                      {t('settings.runtimeConfig.lastPushAtTag')} {new Date(data.lastPushedAt).toLocaleString()}
                    </span>
                  ) : null}
                </div>
                {data.lastPushMessage ? <p className="text-xs text-admin-muted">{data.lastPushMessage}</p> : null}
                {(pushStatus === 'BLOCKED' || pushStatus === 'FAILED') && canUpdate ? (
                  <button
                    type="button"
                    className={adminGhostButtonClass}
                    disabled={retryMutation.isPending}
                    onClick={() => retryMutation.mutate()}
                  >
                    <RefreshCw className="h-3.5 w-3.5" aria-hidden />
                    {retryMutation.isPending
                      ? t('settings.runtimeConfig.retrying')
                      : t('settings.runtimeConfig.retryPush')}
                  </button>
                ) : null}
              </>
            )}
          </div>

          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              handleSubmit()
            }}
          >
            <label className="block text-sm text-admin">
              {t('settings.runtimeConfig.dsn')}
              <input
                className={`${adminFieldClass} mt-1`}
                type="password"
                autoComplete="new-password"
                value={dsn}
                disabled={!canUpdate}
                placeholder={
                  data.retrievalDatabaseUrlMasked ??
                  (data.retrievalDatabaseUrlConfigured ? t('settings.apiKeyKeep') : '')
                }
                onChange={(event) => setDsn(event.target.value)}
              />
              <span className="mt-1 block text-xs text-admin-muted">
                {data.retrievalDatabaseUrlConfigured
                  ? data.retrievalDatabaseUrlMasked
                    ? t('settings.runtimeConfig.dsnHintMasked', { masked: data.retrievalDatabaseUrlMasked })
                    : t('settings.runtimeConfig.dsnHintConfigured')
                  : t('settings.runtimeConfig.dsnHintEmpty')}
              </span>
            </label>
            <label className="block text-sm text-admin">
              {t('settings.runtimeConfig.embeddingModel')}
              <input
                className={`${adminFieldClass} mt-1`}
                value={embeddingModel}
                disabled={!canUpdate}
                onChange={(event) => setEmbeddingModel(event.target.value)}
              />
              <span className="mt-1 block text-xs text-admin-muted">
                {t('settings.runtimeConfig.embeddingModelHint')}
              </span>
            </label>
            <label className="block text-sm text-admin">
              {t('settings.runtimeConfig.dimensions')}
              <input
                className={`${adminFieldClass} mt-1`}
                type="number"
                min={1}
                max={10000}
                step={1}
                value={dimensions}
                disabled={!canUpdate}
                onChange={(event) => setDimensions(event.target.value)}
              />
              <span className="mt-1 block text-xs text-admin-muted">
                {t('settings.runtimeConfig.dimensionsHint')}
              </span>
            </label>
            <label className="block text-sm text-admin">
              {t('settings.runtimeConfig.geminiKey')}
              <input
                className={`${adminFieldClass} mt-1`}
                type="password"
                autoComplete="new-password"
                value={geminiKey}
                disabled={!canUpdate}
                placeholder={data.geminiApiKeyConfigured ? t('settings.apiKeyKeep') : ''}
                onChange={(event) => setGeminiKey(event.target.value)}
              />
              <span className="mt-1 block text-xs text-admin-muted">
                {data.geminiApiKeyConfigured
                  ? data.geminiApiKeyHint
                    ? t('settings.runtimeConfig.geminiKeyHint', { hint: data.geminiApiKeyHint })
                    : t('settings.runtimeConfig.geminiKeyConfigured')
                  : t('settings.runtimeConfig.geminiKeyEmpty')}
              </span>
            </label>
            <PermissionGate code={PERMISSION_CODES.aiRuntimeConfigUpdate}>
              <div className={adminFormActionsClass}>
                <button type="submit" disabled={saveMutation.isPending} className={adminPrimaryButtonClass}>
                  {saveMutation.isPending ? t('settings.saving') : t('settings.save')}
                </button>
              </div>
            </PermissionGate>
          </form>
        </>
      ) : null}

      {confirmOpen && data ? (
        <FormDialog
          title={t('settings.runtimeConfig.dimensionConfirmTitle')}
          closeLabel={t('settings.runtimeConfig.close')}
          onClose={() => setConfirmOpen(false)}
          size="s"
        >
          <p className="text-sm text-admin">
            {t('settings.runtimeConfig.dimensionConfirmBody', {
              from:
                data.aiEmbeddingDimensions == null
                  ? t('settings.runtimeConfig.dimensionsUnset')
                  : String(data.aiEmbeddingDimensions),
              to:
                pendingDimensions == null
                  ? t('settings.runtimeConfig.dimensionsUnset')
                  : String(pendingDimensions),
            })}
          </p>
          <div className={adminFormActionsClass}>
            <button type="button" className={adminSecondaryButtonClass} onClick={() => setConfirmOpen(false)}>
              {t('settings.runtimeConfig.cancel')}
            </button>
            <button
              type="button"
              className={adminPrimaryButtonClass}
              disabled={saveMutation.isPending}
              onClick={confirmSave}
            >
              {saveMutation.isPending ? t('settings.saving') : t('settings.runtimeConfig.confirmSave')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </article>
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
    <section className="admin-content-form">
      <AdminPageHeader title={t('settings.title')} description={t('settings.description')} />
      <div className="space-y-4">
        <div className="flex gap-2 rounded-xl border border-info/28 bg-[rgb(var(--info)/.07)] px-4 py-3 text-sm text-admin">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-info" aria-hidden />
          <p>{t('settings.fallbackHint')}</p>
        </div>
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

        <GatewayAccessKeysCard />

        <RuntimeConfigCard />

        <McpServersCard />

        <ExperienceLessonsCard />

        <EmbeddingRegistrationCard />

        <ChainPresetsCard />

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
      </div>
    </section>
  )
}
