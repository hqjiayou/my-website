import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, Info, Plus, RefreshCw, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { AdminPageHeader, FormDialog, StatusTag } from '@/components/admin'
import {
  adminDangerButtonMdClass,
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
  AiMcpSecretKvInput,
  AiMcpServerEntry,
  AiMcpServerEntryInput,
} from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

type McpTransport = 'http' | 'sse' | 'stdio'

const NAME_PATTERN = /^[a-zA-Z0-9_-]+$/

/**
 * 密钥 kv 行草稿：value 留空＝保留原值（仅对服务端已存的键成立，新键必须填值）；
 * clear＝显式删键。isNew 标记本次新建的行（服务端没有这个键）。
 */
type KvDraft = {
  key: string
  value: string
  clear: boolean
  isNew: boolean
  valueHint: string | null
}

type EntryDraft = {
  localId: number
  isNew: boolean
  name: string
  transport: McpTransport
  /** url 输入框：留空＝保留原值（密钥语义）；导入/新建填了就是新值。 */
  url: string
  urlConfigured: boolean
  urlHint: string | null
  enabled: boolean
  toolPrefix: string
  timeoutText: string
  command: string
  args: string[]
  cwd: string
  headers: KvDraft[]
  headersTouched: boolean
  env: KvDraft[]
  envTouched: boolean
}

let nextLocalId = 1

function kvDraftsFrom(entry: AiMcpServerEntry, which: 'headers' | 'env'): KvDraft[] {
  return entry[which].map((kv) => ({
    key: kv.key,
    value: '',
    clear: false,
    isNew: false,
    valueHint: kv.valueHint,
  }))
}

function draftFromEntry(entry: AiMcpServerEntry): EntryDraft {
  const transport: McpTransport =
    entry.transport === 'sse' || entry.transport === 'stdio' ? entry.transport : 'http'
  return {
    localId: nextLocalId++,
    isNew: false,
    name: entry.name,
    transport,
    url: '',
    urlConfigured: entry.urlConfigured,
    urlHint: entry.urlHint,
    enabled: entry.enabled,
    toolPrefix: entry.toolPrefix ?? '',
    timeoutText: entry.initTimeoutSeconds == null ? '' : String(entry.initTimeoutSeconds),
    command: entry.command ?? '',
    args: [...entry.args],
    cwd: entry.cwd ?? '',
    headers: kvDraftsFrom(entry, 'headers'),
    headersTouched: false,
    env: kvDraftsFrom(entry, 'env'),
    envTouched: false,
  }
}

function emptyEntryDraft(): EntryDraft {
  return {
    localId: nextLocalId++,
    isNew: true,
    name: '',
    transport: 'http',
    url: '',
    urlConfigured: false,
    urlHint: null,
    enabled: true,
    toolPrefix: '',
    timeoutText: '',
    command: '',
    args: [],
    cwd: '',
    headers: [],
    headersTouched: false,
    env: [],
    envTouched: false,
  }
}

/**
 * kv 表 → 提交形。未触碰返回 null（保留整表）；触碰后列表即最终键集合：
 * 已存键留空＝保留、clear＝删键、新键带值。
 */
function buildKvInputs(rows: KvDraft[], touched: boolean): AiMcpSecretKvInput[] | null {
  if (!touched) return null
  return rows
    .filter((row) => row.key.trim() !== '' || row.clear)
    .map((row) => {
      if (row.clear) return { key: row.key.trim(), clear: true }
      const value = row.value.trim()
      return { key: row.key.trim(), value: value === '' ? null : value }
    })
}

function buildEntryInput(draft: EntryDraft): AiMcpServerEntryInput {
  const isStdio = draft.transport === 'stdio'
  const timeout = draft.timeoutText.trim()
  const url = draft.url.trim()
  return {
    name: draft.name.trim(),
    transport: draft.transport,
    // url 按密钥语义：不显示时（stdio）与留空时都传 null＝保留/不设。
    url: isStdio ? null : url === '' ? null : url,
    enabled: draft.enabled,
    toolPrefix: draft.toolPrefix.trim() || null,
    initTimeoutSeconds: timeout === '' ? null : Number(timeout),
    command: isStdio ? draft.command.trim() || null : null,
    args: isStdio ? draft.args.map((arg) => arg.trim()).filter((arg) => arg !== '') : null,
    cwd: isStdio ? draft.cwd.trim() || null : null,
    headers: isStdio ? null : buildKvInputs(draft.headers, draft.headersTouched),
    env: isStdio ? buildKvInputs(draft.env, draft.envTouched) : null,
  }
}

/** 校验一张 kv 表：键必填不重复；留空保留只对已存键成立，新键必须填值（clear 行除外）。 */
function validateKvRows(rows: KvDraft[], t: (key: string) => string): string | null {
  const seen = new Set<string>()
  for (const row of rows) {
    const key = row.key.trim()
    if (key === '') return t('settings.mcp.kvKeyRequired')
    if (seen.has(key)) return t('settings.mcp.kvDuplicate')
    seen.add(key)
    if (!row.clear && row.isNew && row.value.trim() === '') return t('settings.mcp.kvValueRequired')
  }
  return null
}

function validateDrafts(drafts: EntryDraft[], t: (key: string) => string): string | null {
  const names = new Set<string>()
  for (const draft of drafts) {
    const name = draft.name.trim()
    if (name === '') return t('settings.mcp.nameRequired')
    if (!NAME_PATTERN.test(name) || name.length > 64) return t('settings.mcp.nameInvalid')
    if (names.has(name)) return t('settings.mcp.nameDuplicate')
    names.add(name)
    const timeout = draft.timeoutText.trim()
    if (timeout !== '') {
      const value = Number(timeout)
      if (!Number.isInteger(value) || value < 1 || value > 600) return t('settings.mcp.timeoutInvalid')
    }
    if (draft.transport === 'stdio') {
      if (draft.command.trim() === '') return t('settings.mcp.commandRequired')
      if (draft.args.length > 32) return t('settings.mcp.argsTooMany')
      const envError = validateKvRows(draft.env, t)
      if (envError) return envError
    } else {
      if (!draft.urlConfigured && draft.url.trim() === '') return t('settings.mcp.urlRequired')
      const headerError = validateKvRows(draft.headers, t)
      if (headerError) return headerError
    }
  }
  return null
}

/** env MCP_SERVERS_JSON 形状（与 Python parse_mcp_server_configs 字段集对齐）的一项。 */
type ImportedEntry = Record<string, unknown>

function importKvRows(raw: unknown, entryName: string): KvDraft[] {
  if (raw == null) return []
  const rows: KvDraft[] = []
  const push = (key: string, value: unknown) => {
    rows.push({ key, value: value == null ? '' : String(value), clear: false, isNew: true, valueHint: null })
  }
  if (Array.isArray(raw)) {
    for (const item of raw) {
      if (typeof item !== 'object' || item === null) throw new Error(entryName)
      const record = item as Record<string, unknown>
      if (typeof record.key !== 'string' || record.key.trim() === '') throw new Error(entryName)
      push(record.key, record.value)
    }
    return rows
  }
  if (typeof raw === 'object') {
    for (const [key, value] of Object.entries(raw as Record<string, unknown>)) push(key, value)
    return rows
  }
  throw new Error(entryName)
}

/** 把粘贴的 MCP_SERVERS_JSON 解析成草稿；任何一条形状不对整批失败（调用方提示、不提交）。 */
function parseImportedServers(text: string): EntryDraft[] {
  const parsed: unknown = JSON.parse(text)
  if (!Array.isArray(parsed)) throw new Error('root')
  return parsed.map((raw, index) => {
    if (typeof raw !== 'object' || raw === null) throw new Error(String(index))
    const item = raw as ImportedEntry
    const name = typeof item.name === 'string' ? item.name.trim() : ''
    if (name === '' || !NAME_PATTERN.test(name) || name.length > 64) throw new Error(String(index))
    let transport: McpTransport = 'http'
    if (item.transport === 'sse' || item.transport === 'stdio' || item.transport === 'http') {
      transport = item.transport
    } else if (typeof item.command === 'string' && item.command.trim() !== '') {
      transport = 'stdio'
    }
    const draft = emptyEntryDraft()
    draft.name = name
    draft.transport = transport
    draft.url = typeof item.url === 'string' ? item.url : ''
    draft.enabled = typeof item.enabled === 'boolean' ? item.enabled : true
    const prefix = item.toolPrefix ?? item.tool_prefix
    draft.toolPrefix = typeof prefix === 'string' ? prefix : ''
    const timeout = item.initTimeoutSeconds ?? item.init_timeout_seconds
    draft.timeoutText =
      typeof timeout === 'number' ? String(timeout) : typeof timeout === 'string' ? timeout : ''
    draft.command = typeof item.command === 'string' ? item.command : ''
    if (Array.isArray(item.args)) {
      draft.args = item.args.map((arg) => String(arg))
    } else if (typeof item.args === 'string' && item.args !== '') {
      draft.args = [item.args]
    }
    draft.cwd = typeof item.cwd === 'string' ? item.cwd : ''
    draft.headers = importKvRows(item.headers, name)
    draft.headersTouched = draft.headers.length > 0
    draft.env = importKvRows(item.env, name)
    draft.envTouched = draft.env.length > 0
    return draft
  })
}

function KvEditor({
  rows,
  disabled,
  onChange,
}: {
  rows: KvDraft[]
  disabled: boolean
  onChange: (rows: KvDraft[]) => void
}) {
  const { t } = useTranslation('aiAdmin')

  function updateRow(index: number, patch: Partial<KvDraft>) {
    onChange(rows.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  }

  return (
    <div className="space-y-2">
      {rows.map((row, index) => (
        <div key={index} className="flex flex-wrap items-center gap-2">
          <input
            className={`${adminFieldClass} w-44 shrink-0 font-mono`}
            value={row.key}
            disabled={disabled || row.clear}
            placeholder={t('settings.mcp.kvKey')}
            onChange={(event) => updateRow(index, { key: event.target.value })}
          />
          <input
            className={`${adminFieldClass} min-w-40 flex-1 font-mono`}
            type="password"
            autoComplete="new-password"
            value={row.value}
            disabled={disabled || row.clear}
            placeholder={
              row.clear
                ? t('settings.mcp.kvCleared')
                : row.isNew
                  ? t('settings.mcp.kvValue')
                  : t('settings.mcp.kvValueKeep')
            }
            onChange={(event) => updateRow(index, { value: event.target.value })}
          />
          {!row.isNew && row.valueHint ? (
            <span className="shrink-0 font-mono text-xs text-admin-muted">••••{row.valueHint}</span>
          ) : null}
          {row.isNew ? (
            <button
              type="button"
              className={cn(adminDangerGhostButtonSmClass, 'shrink-0')}
              disabled={disabled}
              onClick={() => onChange(rows.filter((_, i) => i !== index))}
            >
              {t('settings.mcp.kvRemove')}
            </button>
          ) : (
            <button
              type="button"
              className={cn(adminGhostButtonClass, 'shrink-0')}
              disabled={disabled}
              onClick={() => updateRow(index, { clear: !row.clear })}
            >
              {row.clear ? t('settings.mcp.kvUndo') : t('settings.mcp.kvClear')}
            </button>
          )}
        </div>
      ))}
      <button
        type="button"
        className={adminGhostButtonClass}
        disabled={disabled}
        onClick={() =>
          onChange([...rows, { key: '', value: '', clear: false, isNew: true, valueHint: null }])
        }
      >
        <Plus className="h-3.5 w-3.5" aria-hidden />
        {t('settings.mcp.kvAdd')}
      </button>
    </div>
  )
}

/**
 * MCP 服务器全局注册表（Issue #192 UoW-4；Issue #214 UoW-5 自 AI 设置页搬为
 * 工具组独立页）：与运行配置共用同一行与推送管线。整表保存（顺序即注册
 * 顺序）；url 与 headers/env 的值按密钥语义处理：GET 只回 hint、编辑留空＝
 * 保留、显式动作才删键。推送状态同运行配置口径。
 * 写接口后端校验 admin:ai-model:update（aiRuntimeConfigUpdate）。
 */
function McpServersCard() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiRuntimeConfigUpdate)
  const [drafts, setDrafts] = useState<EntryDraft[]>([])
  const [syncedVersion, setSyncedVersion] = useState<number | null>(null)
  const [importOpen, setImportOpen] = useState(false)
  const [importText, setImportText] = useState('')
  const [removing, setRemoving] = useState<EntryDraft | null>(null)
  const [clearAllOpen, setClearAllOpen] = useState(false)
  const [pendingImport, setPendingImport] = useState<EntryDraft[] | null>(null)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['ai', 'mcp-config'],
    queryFn: () => aiApi.getMcpConfig(),
  })

  // 版本变化（首次加载、保存后回读）时用服务端脱敏视图重建草稿；编辑中不覆盖。
  if (data && data.configVersion !== syncedVersion) {
    setDrafts(data.servers.map(draftFromEntry))
    setSyncedVersion(data.configVersion)
  }

  const saveMutation = useMutation({
    mutationFn: (servers: AiMcpServerEntryInput[]) => aiApi.updateMcpConfig(servers),
    onSuccess: () => {
      toast.success(t('settings.saved'))
      void queryClient.invalidateQueries({ queryKey: ['ai', 'mcp-config'] })
      void queryClient.invalidateQueries({ queryKey: ['ai', 'runtime-config'] })
    },
    onError: (saveError: Error) => toast.error(saveError.message),
  })

  const retryMutation = useMutation({
    mutationFn: () => aiApi.retryRuntimeConfigPush(),
    onSuccess: () => {
      toast.success(t('settings.runtimeConfig.retryDone'))
      void queryClient.invalidateQueries({ queryKey: ['ai', 'mcp-config'] })
      void queryClient.invalidateQueries({ queryKey: ['ai', 'runtime-config'] })
    },
    onError: (retryError: Error) => toast.error(retryError.message),
  })

  function updateDraft(localId: number, patch: Partial<EntryDraft>) {
    setDrafts((current) =>
      current.map((draft) => (draft.localId === localId ? { ...draft, ...patch } : draft)),
    )
  }

  function moveDraft(index: number, delta: number) {
    setDrafts((current) => {
      const target = index + delta
      if (target < 0 || target >= current.length) return current
      const next = [...current]
      const [moved] = next.splice(index, 1)
      next.splice(target, 0, moved)
      return next
    })
  }

  function handleSave() {
    if (!canUpdate || !data) return
    const validationError = validateDrafts(drafts, t)
    if (validationError) {
      toast.error(validationError)
      return
    }
    if (drafts.length === 0) {
      setClearAllOpen(true)
      return
    }
    saveMutation.mutate(drafts.map(buildEntryInput))
  }

  function handleImport() {
    let imported: EntryDraft[]
    try {
      imported = parseImportedServers(importText)
    } catch {
      toast.error(t('settings.mcp.importInvalid'))
      return
    }
    if (drafts.length > 0) {
      setPendingImport(imported)
      return
    }
    applyImport(imported)
  }

  function applyImport(imported: EntryDraft[]) {
    setDrafts(imported)
    setImportOpen(false)
    setImportText('')
    toast.success(t('settings.mcp.importDone', { count: imported.length }))
  }

  const pushStatus = data?.lastPushStatus ?? null
  const hasStdio = drafts.some((draft) => draft.transport === 'stdio')

  return (
    <article className="space-y-3 rounded-xl border border-admin bg-admin-surface p-5">
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
                  <span className="text-sm font-medium text-admin">
                    {t('settings.runtimeConfig.pushTitle')}
                  </span>
                  {pushStatus === 'APPLIED' ? (
                    <StatusTag
                      status="APPLIED"
                      tone="active"
                      label={t('settings.runtimeConfig.pushApplied', { version: data.configVersion })}
                    />
                  ) : null}
                  {pushStatus === 'BLOCKED' ? (
                    <StatusTag
                      status="BLOCKED"
                      tone="warning"
                      label={t('settings.runtimeConfig.pushBlocked')}
                    />
                  ) : null}
                  {pushStatus === 'FAILED' ? (
                    <StatusTag status="FAILED" tone="danger" label={t('settings.runtimeConfig.pushFailed')} />
                  ) : null}
                  {data.lastPushedAt ? (
                    <span className="text-xs text-admin-muted">
                      {t('settings.runtimeConfig.lastPushAtTag')}{' '}
                      {new Date(data.lastPushedAt).toLocaleString()}
                    </span>
                  ) : null}
                </div>
                {data.lastPushMessage ? (
                  <p className="text-xs text-admin-muted">{data.lastPushMessage}</p>
                ) : null}
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

          {hasStdio ? (
            <div className="flex gap-2 rounded-lg border border-warning/35 bg-warning-muted px-4 py-3 text-sm text-warning-emphasis">
              <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <p>{t('settings.mcp.stdioWarning')}</p>
            </div>
          ) : null}

          {drafts.length === 0 ? (
            <p className="text-sm text-admin-muted">{t('settings.mcp.empty')}</p>
          ) : (
            <div className="space-y-3">
              {drafts.map((draft, index) => (
                <section key={draft.localId} className="space-y-3 rounded-lg border border-admin p-4">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="min-w-0 flex-1 truncate text-sm font-medium text-admin">
                      {draft.name.trim() || t('settings.mcp.unnamed')}
                      <span className="ml-2 font-mono text-xs font-normal text-admin-muted">
                        {draft.transport}
                      </span>
                    </span>
                    {!draft.enabled ? (
                      <StatusTag status="DISABLED" tone="danger" label={t('settings.mcp.disabledBadge')} />
                    ) : null}
                    {canUpdate ? (
                      <>
                        <button
                          type="button"
                          className={cn(adminGhostButtonClass, 'shrink-0')}
                          disabled={index === 0}
                          aria-label={t('settings.mcp.moveUp')}
                          onClick={() => moveDraft(index, -1)}
                        >
                          <ArrowUp className="h-3.5 w-3.5" aria-hidden />
                        </button>
                        <button
                          type="button"
                          className={cn(adminGhostButtonClass, 'shrink-0')}
                          disabled={index === drafts.length - 1}
                          aria-label={t('settings.mcp.moveDown')}
                          onClick={() => moveDraft(index, 1)}
                        >
                          <ArrowDown className="h-3.5 w-3.5" aria-hidden />
                        </button>
                        <button
                          type="button"
                          className={cn(adminDangerGhostButtonSmClass, 'shrink-0')}
                          onClick={() => setRemoving(draft)}
                        >
                          <Trash2 className="h-3.5 w-3.5" aria-hidden />
                          {t('settings.mcp.remove')}
                        </button>
                      </>
                    ) : null}
                  </div>

                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="block text-sm text-admin">
                      {t('settings.mcp.name')}
                      <input
                        className={`${adminFieldClass} mt-1 font-mono`}
                        value={draft.name}
                        disabled={!canUpdate || !draft.isNew}
                        placeholder="my-server"
                        onChange={(event) => updateDraft(draft.localId, { name: event.target.value })}
                      />
                      <span className="mt-1 block text-xs text-admin-muted">
                        {draft.isNew ? t('settings.mcp.nameHint') : t('settings.mcp.nameLocked')}
                      </span>
                    </label>
                    <label className="block text-sm text-admin">
                      {t('settings.mcp.transport')}
                      <select
                        className={`${adminFieldClass} mt-1`}
                        value={draft.transport}
                        disabled={!canUpdate}
                        onChange={(event) =>
                          updateDraft(draft.localId, {
                            transport: event.target.value as McpTransport,
                          })
                        }
                      >
                        <option value="http">http（Streamable HTTP）</option>
                        <option value="sse">sse</option>
                        <option value="stdio">stdio</option>
                      </select>
                    </label>
                  </div>

                  {draft.transport === 'stdio' ? (
                    <>
                      <label className="block text-sm text-admin">
                        {t('settings.mcp.command')}
                        <input
                          className={`${adminFieldClass} mt-1 font-mono`}
                          value={draft.command}
                          disabled={!canUpdate}
                          placeholder="npx"
                          onChange={(event) =>
                            updateDraft(draft.localId, { command: event.target.value })
                          }
                        />
                      </label>
                      <div className="space-y-2">
                        <span className="block text-sm text-admin">{t('settings.mcp.args')}</span>
                        {draft.args.map((arg, argIndex) => (
                          <div key={argIndex} className="flex items-center gap-2">
                            <input
                              className={`${adminFieldClass} flex-1 font-mono`}
                              value={arg}
                              disabled={!canUpdate}
                              onChange={(event) => {
                                const args = [...draft.args]
                                args[argIndex] = event.target.value
                                updateDraft(draft.localId, { args })
                              }}
                            />
                            <button
                              type="button"
                              className={cn(adminDangerGhostButtonSmClass, 'shrink-0')}
                              disabled={!canUpdate}
                              onClick={() =>
                                updateDraft(draft.localId, {
                                  args: draft.args.filter((_, i) => i !== argIndex),
                                })
                              }
                            >
                              {t('settings.mcp.kvRemove')}
                            </button>
                          </div>
                        ))}
                        <button
                          type="button"
                          className={adminGhostButtonClass}
                          disabled={!canUpdate}
                          onClick={() => updateDraft(draft.localId, { args: [...draft.args, ''] })}
                        >
                          <Plus className="h-3.5 w-3.5" aria-hidden />
                          {t('settings.mcp.argsAdd')}
                        </button>
                      </div>
                      <label className="block text-sm text-admin">
                        {t('settings.mcp.cwd')}
                        <input
                          className={`${adminFieldClass} mt-1 font-mono`}
                          value={draft.cwd}
                          disabled={!canUpdate}
                          onChange={(event) => updateDraft(draft.localId, { cwd: event.target.value })}
                        />
                      </label>
                    </>
                  ) : (
                    <label className="block text-sm text-admin">
                      {t('settings.mcp.url')}
                      <input
                        className={`${adminFieldClass} mt-1 font-mono`}
                        type="password"
                        autoComplete="new-password"
                        value={draft.url}
                        disabled={!canUpdate}
                        placeholder={
                          draft.urlConfigured ? t('settings.mcp.urlKeep') : 'https://example.com/mcp'
                        }
                        onChange={(event) => updateDraft(draft.localId, { url: event.target.value })}
                      />
                      <span className="mt-1 block text-xs text-admin-muted">
                        {draft.urlConfigured
                          ? draft.urlHint
                            ? t('settings.mcp.urlHintConfigured', { hint: draft.urlHint })
                            : t('settings.mcp.urlConfigured')
                          : t('settings.mcp.urlHintEmpty')}
                      </span>
                    </label>
                  )}

                  <div className="grid gap-3 sm:grid-cols-2">
                    <label className="block text-sm text-admin">
                      {t('settings.mcp.toolPrefix')}
                      <input
                        className={`${adminFieldClass} mt-1 font-mono`}
                        value={draft.toolPrefix}
                        disabled={!canUpdate}
                        onChange={(event) =>
                          updateDraft(draft.localId, { toolPrefix: event.target.value })
                        }
                      />
                      <span className="mt-1 block text-xs text-admin-muted">
                        {t('settings.mcp.toolPrefixHint')}
                      </span>
                    </label>
                    <label className="block text-sm text-admin">
                      {t('settings.mcp.timeout')}
                      <input
                        className={`${adminFieldClass} mt-1`}
                        type="number"
                        min={1}
                        max={600}
                        step={1}
                        value={draft.timeoutText}
                        disabled={!canUpdate}
                        onChange={(event) =>
                          updateDraft(draft.localId, { timeoutText: event.target.value })
                        }
                      />
                      <span className="mt-1 block text-xs text-admin-muted">
                        {t('settings.mcp.timeoutHint')}
                      </span>
                    </label>
                  </div>

                  <label className="flex items-center gap-2 text-sm text-admin">
                    <input
                      type="checkbox"
                      className="admin-check"
                      checked={draft.enabled}
                      disabled={!canUpdate}
                      onChange={(event) =>
                        updateDraft(draft.localId, { enabled: event.target.checked })
                      }
                    />
                    {t('settings.mcp.enabled')}
                  </label>

                  {draft.transport === 'stdio' ? (
                    <div className="space-y-1">
                      <span className="block text-sm text-admin">{t('settings.mcp.env')}</span>
                      <KvEditor
                        rows={draft.env}
                        disabled={!canUpdate}
                        onChange={(env) =>
                          updateDraft(draft.localId, { env, envTouched: true })
                        }
                      />
                    </div>
                  ) : (
                    <div className="space-y-1">
                      <span className="block text-sm text-admin">{t('settings.mcp.headers')}</span>
                      <KvEditor
                        rows={draft.headers}
                        disabled={!canUpdate}
                        onChange={(headers) =>
                          updateDraft(draft.localId, { headers, headersTouched: true })
                        }
                      />
                    </div>
                  )}
                </section>
              ))}
            </div>
          )}

          <PermissionGate code={PERMISSION_CODES.aiRuntimeConfigUpdate}>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                className={adminSecondaryButtonClass}
                onClick={() => setDrafts((current) => [...current, emptyEntryDraft()])}
              >
                <Plus className="h-4 w-4" aria-hidden />
                {t('settings.mcp.add')}
              </button>
              <button
                type="button"
                className={adminSecondaryButtonClass}
                onClick={() => setImportOpen(true)}
              >
                {t('settings.mcp.importJson')}
              </button>
            </div>
            <div className={adminFormActionsClass}>
              <button
                type="button"
                className={adminPrimaryButtonClass}
                disabled={saveMutation.isPending}
                onClick={handleSave}
              >
                {saveMutation.isPending ? t('settings.saving') : t('settings.save')}
              </button>
            </div>
          </PermissionGate>
        </>
      ) : null}

      {importOpen ? (
        <FormDialog
          title={t('settings.mcp.importTitle')}
          closeLabel={t('settings.mcp.importCancel')}
          onClose={() => setImportOpen(false)}
          size="m"
        >
          <p className="text-sm text-admin-muted">{t('settings.mcp.importDescription')}</p>
          <textarea
            className={`${adminFieldClass} mt-3 min-h-40 font-mono text-xs`}
            value={importText}
            placeholder={t('settings.mcp.importPlaceholder')}
            onChange={(event) => setImportText(event.target.value)}
          />
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setImportOpen(false)}
            >
              {t('settings.mcp.importCancel')}
            </button>
            <button
              type="button"
              className={adminPrimaryButtonClass}
              disabled={importText.trim() === ''}
              onClick={handleImport}
            >
              {t('settings.mcp.importApply')}
            </button>
          </div>
        </FormDialog>
      ) : null}

      {removing ? (
        <FormDialog
          title={t('settings.mcp.removeConfirmTitle')}
          closeLabel={t('settings.mcp.cancel')}
          onClose={() => setRemoving(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('settings.mcp.removeConfirmBody', {
              name: removing.name.trim() || t('settings.mcp.unnamed'),
            })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setRemoving(null)}
            >
              {t('settings.mcp.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              onClick={() => {
                setDrafts((current) =>
                  current.filter((item) => item.localId !== removing.localId),
                )
                setRemoving(null)
              }}
            >
              {t('settings.mcp.remove')}
            </button>
          </div>
        </FormDialog>
      ) : null}

      {clearAllOpen ? (
        <FormDialog
          title={t('settings.mcp.clearAllConfirmTitle')}
          closeLabel={t('settings.mcp.cancel')}
          onClose={() => setClearAllOpen(false)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">{t('settings.mcp.confirmClearAll')}</p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setClearAllOpen(false)}
            >
              {t('settings.mcp.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={saveMutation.isPending}
              onClick={() => {
                setClearAllOpen(false)
                saveMutation.mutate(drafts.map(buildEntryInput))
              }}
            >
              {t('settings.mcp.clearAllConfirmAction')}
            </button>
          </div>
        </FormDialog>
      ) : null}

      {pendingImport ? (
        <FormDialog
          title={t('settings.mcp.importReplaceConfirmTitle')}
          closeLabel={t('settings.mcp.cancel')}
          onClose={() => setPendingImport(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('settings.mcp.importReplaceConfirm', { count: pendingImport.length })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setPendingImport(null)}
            >
              {t('settings.mcp.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              onClick={() => {
                const imported = pendingImport
                setPendingImport(null)
                applyImport(imported)
              }}
            >
              {t('settings.mcp.importReplaceConfirmAction')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </article>
  )
}

/**
 * MCP 独立页（Issue #214 UoW-5）：工具组页面，内容即上面的注册表卡片；
 * 页头承接原卡片内的标题与说明（settings.mcp.title / description），
 * AI 设置页原位置只留跳转卡。后端沿用 #192 的 /admin/ai/mcp-config 通道。
 */
export function AiMcpServersPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section className="admin-content-form">
      <AdminPageHeader title={t('settings.mcp.title')} description={t('settings.mcp.description')} />
      <div className="space-y-4">
        <McpServersCard />
      </div>
    </section>
  )
}
