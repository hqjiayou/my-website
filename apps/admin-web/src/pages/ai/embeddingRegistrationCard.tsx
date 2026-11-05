import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { StatusTag } from '@/components/admin'
import {
  adminFieldClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type { AiEmbeddingClass } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'
import { embeddingClassName, MAX_EMBEDDING_CLASSES } from '@/utils/modelSpec'

type DraftRow = {
  entryId: number
  enabled: boolean
  /** 服务端解析快照：草稿期间入口信息不变，保存后按服务端回读刷新。入口已删时为 null。 */
  entryDisplayName: string | null
  embeddingSpec: string | null
  className: string | null
}

function toDraftRows(rows: AiEmbeddingClass[]): DraftRow[] {
  return [...rows]
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map((row) => ({
      entryId: row.entryId,
      enabled: row.enabled,
      entryDisplayName: row.entryDisplayName,
      embeddingSpec: row.embeddingSpec,
      className: row.className,
    }))
}

function sameRows(a: DraftRow[], b: DraftRow[]) {
  return (
    a.length === b.length &&
    a.every((row, index) => row.entryId === b[index].entryId && row.enabled === b[index].enabled)
  )
}

/**
 * 嵌入类登记编辑区（Issue #126 UoW-3）：站点接入页（传 agentId）与 AI 设置页
 * （不传＝全站 scope）共用同形卡片。登记只引用模型入口：候选只列 mode=KEY 且
 * embeddingSpec 非空的入口；行序即类优先级，整表 PUT 保存。
 * 本地草稿用 draft 覆盖服务端数据（null＝未改），不写 effect 同步。
 */
export function EmbeddingRegistrationCard({ agentId }: { agentId?: number }) {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const scopeKey = agentId ?? 'global'
  const [draft, setDraft] = useState<DraftRow[] | null>(null)
  const [candidateId, setCandidateId] = useState('')

  const registrationQuery = useQuery({
    queryKey: ['ai', 'embedding-classes', scopeKey],
    queryFn: () => aiApi.listEmbeddingClasses(agentId),
  })
  const entriesQuery = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
  })

  const saveMutation = useMutation({
    mutationFn: (rows: DraftRow[]) =>
      aiApi.replaceEmbeddingClasses(
        agentId,
        rows.map((row) => ({ entryId: row.entryId, enabled: row.enabled })),
      ),
    onSuccess: () => {
      setDraft(null)
      toast.success(t('settings.embeddingRegistration.saved'))
      void queryClient.invalidateQueries({ queryKey: ['ai', 'embedding-classes', scopeKey] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const serverRows = toDraftRows(registrationQuery.data ?? [])
  const rows = draft ?? serverRows
  const dirty = draft != null && !sameRows(draft, serverRows)
  const orphanCount = rows.filter((row) => row.className == null).length
  const candidates = (entriesQuery.data ?? []).filter(
    (entry) =>
      (entry.mode ?? 'KEY') === 'KEY' &&
      embeddingClassName(entry.embeddingSpec) != null &&
      !rows.some((row) => row.entryId === entry.id),
  )

  function move(index: number, delta: number) {
    const target = index + delta
    if (target < 0 || target >= rows.length) return
    const next = [...rows]
    const [item] = next.splice(index, 1)
    next.splice(target, 0, item)
    setDraft(next)
  }

  function addCandidate() {
    const entry = candidates.find((item) => String(item.id) === candidateId)
    if (!entry) return
    if (rows.length >= MAX_EMBEDDING_CLASSES) {
      toast.warning(t('settings.embeddingRegistration.limitReached'))
      return
    }
    const className = embeddingClassName(entry.embeddingSpec)
    if (className != null && rows.some((row) => row.className === className)) {
      toast.warning(t('settings.embeddingRegistration.duplicateClass', { className }))
      return
    }
    setDraft([
      ...rows,
      {
        entryId: entry.id,
        enabled: true,
        entryDisplayName: entry.displayName,
        embeddingSpec: entry.embeddingSpec,
        className,
      },
    ])
    setCandidateId('')
  }

  function save() {
    if (!canUpdate || saveMutation.isPending) return
    if (orphanCount > 0) {
      toast.warning(t('settings.embeddingRegistration.orphanBlocksSave'))
      return
    }
    if (rows.length > MAX_EMBEDDING_CLASSES) {
      toast.warning(t('settings.embeddingRegistration.limitReached'))
      return
    }
    saveMutation.mutate(rows)
  }

  return (
    <article className="rounded-xl border border-border bg-card p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-semibold text-foreground">
            {t('settings.embeddingRegistration.title')}
          </h3>
          <span className="rounded-md bg-card-muted px-2 py-0.5 font-mono text-[11px] tabular-nums text-muted-foreground">
            {rows.length}/{MAX_EMBEDDING_CLASSES}
          </span>
        </div>
        {canUpdate && draft != null ? (
          <div className="flex items-center gap-2">
            <button
              type="button"
              className={adminGhostButtonClass}
              onClick={() => {
                setDraft(null)
                setCandidateId('')
              }}
            >
              {t('settings.embeddingRegistration.reset')}
            </button>
            <button
              type="button"
              className={adminPrimaryButtonClass}
              disabled={!dirty || saveMutation.isPending}
              onClick={save}
            >
              {saveMutation.isPending
                ? t('settings.embeddingRegistration.saving')
                : t('settings.embeddingRegistration.save')}
            </button>
          </div>
        ) : null}
      </div>
      <p className="mb-4 max-w-3xl text-xs leading-5 text-muted-foreground">
        {t(
          agentId == null
            ? 'settings.embeddingRegistration.descriptionGlobal'
            : 'settings.embeddingRegistration.descriptionSite',
        )}
      </p>

      {registrationQuery.isPending || entriesQuery.isPending ? (
        <p className="text-sm text-muted-foreground">{t('loading')}</p>
      ) : registrationQuery.isError || entriesQuery.isError ? (
        <p className="text-sm text-danger-emphasis">{t('settings.embeddingRegistration.loadError')}</p>
      ) : (
        <div className="space-y-3">
          {rows.length === 0 ? (
            <div className="rounded-lg border border-dashed border-border bg-card-muted/40 px-3 py-4 text-sm text-muted-foreground">
              {t('settings.embeddingRegistration.empty')}
            </div>
          ) : (
            <ul className="space-y-2">
              {rows.map((row, index) => (
                <li
                  key={row.entryId}
                  className="flex items-center gap-3 rounded-lg border border-border bg-card-muted/30 px-3 py-2.5 text-sm"
                >
                  <span className="w-6 shrink-0 text-center font-mono text-xs text-muted-foreground">
                    {index + 1}
                  </span>
                  <span className="min-w-0 flex-1">
                    {row.className != null ? (
                      <>
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="truncate font-mono text-[13px] font-medium text-foreground">
                            {row.className}
                          </span>
                          {!row.enabled ? (
                            <StatusTag
                              status="DISABLED"
                              tone="danger"
                              label={t('settings.disabledBadge')}
                            />
                          ) : null}
                        </span>
                        <span className="mt-0.5 block truncate text-xs text-muted-foreground">
                          {row.entryDisplayName} · {row.embeddingSpec}
                        </span>
                      </>
                    ) : (
                      <>
                        <span className="font-medium text-danger-emphasis">
                          {t('settings.embeddingRegistration.entryMissing')}
                        </span>
                        <span className="mt-0.5 block text-xs text-muted-foreground">
                          {t('settings.embeddingRegistration.entryMissingHint', { entryId: row.entryId })}
                        </span>
                      </>
                    )}
                  </span>
                  <label className="flex shrink-0 items-center gap-2 text-xs text-foreground">
                    <input
                      type="checkbox"
                      className="admin-check"
                      checked={row.enabled}
                      disabled={!canUpdate}
                      onChange={(event) =>
                        setDraft(
                          rows.map((item) =>
                            item.entryId === row.entryId
                              ? { ...item, enabled: event.target.checked }
                              : item,
                          ),
                        )
                      }
                    />
                    {t('settings.enabled')}
                  </label>
                  <span className="flex shrink-0 items-center gap-1">
                    <button
                      type="button"
                      className={cn(adminGhostButtonClass, 'px-2')}
                      disabled={!canUpdate || index === 0}
                      aria-label={t('settings.embeddingRegistration.moveUp')}
                      title={t('settings.embeddingRegistration.moveUp')}
                      onClick={() => move(index, -1)}
                    >
                      <ArrowUp className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      className={cn(adminGhostButtonClass, 'px-2')}
                      disabled={!canUpdate || index === rows.length - 1}
                      aria-label={t('settings.embeddingRegistration.moveDown')}
                      title={t('settings.embeddingRegistration.moveDown')}
                      onClick={() => move(index, 1)}
                    >
                      <ArrowDown className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      className={cn(adminGhostButtonClass, 'px-2')}
                      disabled={!canUpdate}
                      aria-label={t('settings.embeddingRegistration.remove')}
                      title={t('settings.embeddingRegistration.remove')}
                      onClick={() =>
                        setDraft(rows.filter((item) => item.entryId !== row.entryId))
                      }
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </span>
                </li>
              ))}
            </ul>
          )}

          <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
            <div className="rounded-lg border border-border bg-card-muted/30 p-3">
              <p className="mb-2 text-xs font-medium text-muted-foreground">
                {t('settings.embeddingRegistration.addLabel')}
              </p>
              <div className="flex flex-wrap items-center gap-2">
                <select
                  className={cn(adminFieldClass, 'w-72 max-w-full shrink-0 sm:w-80')}
                  value={candidateId}
                  disabled={rows.length >= MAX_EMBEDDING_CLASSES}
                  onChange={(event) => setCandidateId(event.target.value)}
                >
                  <option value="">{t('settings.embeddingRegistration.pickEntry')}</option>
                  {candidates.map((entry) => (
                    <option key={entry.id} value={entry.id}>
                      {entry.displayName} · {entry.embeddingSpec}
                      {entry.sourceAgentId != null && entry.sourceAgentId !== agentId
                        ? ` · ${t('settings.embeddingRegistration.boundOther')}`
                        : ''}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className={adminSecondaryButtonClass}
                  disabled={!candidateId || rows.length >= MAX_EMBEDDING_CLASSES}
                  onClick={addCandidate}
                >
                  {t('settings.embeddingRegistration.add')}
                </button>
              </div>
              <p className="mt-2 text-xs leading-5 text-muted-foreground">
                {candidates.length === 0
                  ? t('settings.embeddingRegistration.noCandidates')
                  : t('settings.embeddingRegistration.addHint', {
                      count: rows.length,
                      max: MAX_EMBEDDING_CLASSES,
                    })}
              </p>
            </div>
          </PermissionGate>
        </div>
      )}
    </article>
  )
}
