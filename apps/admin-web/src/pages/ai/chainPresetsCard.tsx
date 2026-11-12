import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, X } from 'lucide-react'
import { Fragment, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { FormDialog, FormField, Spinner } from '@/components/admin'
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
import type { AiChainPreset, AiModelEntry } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

type Draft = {
  id: number | null
  name: string
  entryIds: number[]
  version: number | null
}

function entryLabel(entry: AiModelEntry): string {
  return entry.displayName ? `${entry.displayName} · ${entry.modelSpec}` : entry.modelSpec
}

/**
 * 对话链模板卡（Issue #210 UoW-7 引入，#214 UoW-3 降级为模板库）：模型页维护
 * 命名链模板——成员是有序的模型入口 id 名单，顺序即链序。模板不被 agent
 * 引用：agent 在工作台把模板载入自有链时是拷贝，之后改/删模板不影响 agent。
 */
export function ChainPresetsCard() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [deleting, setDeleting] = useState<AiChainPreset | null>(null)

  const presetsQuery = useQuery({
    queryKey: ['ai', 'chain-presets'],
    queryFn: () => aiApi.listChainPresets(),
  })
  const entriesQuery = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
  })
  const entries = entriesQuery.data ?? []
  const entryById = new Map(entries.map((entry) => [entry.id, entry]))

  // 列表呈现按模式分行（DESIGN_SYSTEM §10 组链可视化）：成员按其入口 mode 分组，
  // 组内保持名单原序，模式行只渲染有成员者，行序按成员首次出现的模式。
  function chainGroups(preset: AiChainPreset): Array<{ mode: 'CLI' | 'KEY'; entryIds: number[] }> {
    const groups: Array<{ mode: 'CLI' | 'KEY'; entryIds: number[] }> = []
    for (const entryId of preset.entryIds) {
      const mode = (entryById.get(entryId)?.mode ?? 'KEY') === 'CLI' ? 'CLI' : 'KEY'
      const group = groups.find((item) => item.mode === mode)
      if (group) {
        group.entryIds.push(entryId)
      } else {
        groups.push({ mode, entryIds: [entryId] })
      }
    }
    return groups
  }

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['ai', 'chain-presets'] })
  }

  const saveMutation = useMutation({
    mutationFn: (value: Draft) =>
      value.id == null
        ? aiApi.createChainPreset({ name: value.name.trim(), entryIds: value.entryIds })
        : aiApi.updateChainPreset(value.id, {
            name: value.name.trim(),
            entryIds: value.entryIds,
            version: value.version ?? undefined,
          }),
    onSuccess: () => {
      setDraft(null)
      invalidate()
      toast.success(t('settings.chainPresets.saved'))
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : t('error')),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => aiApi.deleteChainPreset(id),
    onSuccess: () => {
      setDeleting(null)
      invalidate()
      toast.success(t('settings.chainPresets.deleted'))
    },
    // 业务错误（如成员不存在）：服务端文案原样呈现
    onError: (error) => toast.error(error instanceof Error ? error.message : t('error')),
  })

  function toggleMember(entryId: number) {
    setDraft((current) => {
      if (!current) return current
      const selected = current.entryIds.includes(entryId)
      return {
        ...current,
        entryIds: selected
          ? current.entryIds.filter((id) => id !== entryId)
          : [...current.entryIds, entryId],
      }
    })
  }

  function moveMember(index: number, delta: number) {
    setDraft((current) => {
      if (!current) return current
      const target = index + delta
      if (target < 0 || target >= current.entryIds.length) return current
      const ids = [...current.entryIds]
      const [moved] = ids.splice(index, 1)
      ids.splice(target, 0, moved)
      return { ...current, entryIds: ids }
    })
  }

  return (
    <article className="space-y-3 rounded-xl border border-admin bg-admin-surface p-5">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold text-admin">{t('settings.chainPresets.title')}</h3>
          <p className="text-sm text-admin-muted">{t('settings.chainPresets.hint')}</p>
        </div>
        <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
          <button
            type="button"
            className={adminPrimaryButtonClass}
            onClick={() => setDraft({ id: null, name: '', entryIds: [], version: null })}
          >
            {t('settings.chainPresets.add')}
          </button>
        </PermissionGate>
      </div>

      {presetsQuery.isPending ? (
        <div className="space-y-2" aria-hidden>
          {[1, 2, 3].map((n) => (
            <div key={n} className="h-14 animate-pulse rounded-[10px] bg-admin-surface-muted" />
          ))}
        </div>
      ) : presetsQuery.isError ? (
        <p className="text-sm text-danger-emphasis">{t('settings.chainPresets.loadFailed')}</p>
      ) : (presetsQuery.data ?? []).length === 0 ? (
        <p className="text-sm text-admin-muted">{t('settings.chainPresets.empty')}</p>
      ) : (
        <ul className="space-y-2">
          {(presetsQuery.data ?? []).map((preset) => (
            <li
              key={preset.id}
              className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-admin bg-admin-surface-muted px-3 py-2"
            >
              <span className="text-sm font-semibold text-admin">{preset.name}</span>
              <span className="ml-auto flex gap-1">
                <button
                  type="button"
                  className={adminGhostButtonClass}
                  disabled={!canUpdate}
                  onClick={() =>
                    setDraft({
                      id: preset.id,
                      name: preset.name,
                      entryIds: [...preset.entryIds],
                      version: preset.version,
                    })
                  }
                >
                  {t('settings.chainPresets.edit')}
                </button>
                <button
                  type="button"
                  className={adminDangerGhostButtonSmClass}
                  disabled={!canUpdate}
                  onClick={() => setDeleting(preset)}
                >
                  {t('settings.chainPresets.delete')}
                </button>
              </span>
              <div className="basis-full">
                {preset.entryIds.length === 0 ? (
                  <p className="text-sm text-admin-muted">{t('settings.chainPresets.noMembers')}</p>
                ) : (
                  <div className="space-y-2">
                    {chainGroups(preset).map((group) => (
                      <div key={group.mode} className="flex flex-wrap items-center gap-2">
                        <span className="w-16 shrink-0 self-center text-xs font-semibold uppercase text-admin-muted">
                          {group.mode}
                        </span>
                        {group.entryIds.map((entryId, index) => {
                          const entry = entryById.get(entryId)
                          const primary = index === 0
                          const disabled = entry != null && !entry.enabled
                          return (
                            <Fragment key={entryId}>
                              {index > 0 ? (
                                <span
                                  className="flex flex-col items-center gap-1 self-center px-1"
                                  aria-hidden
                                >
                                  <span className="w-8 border-t border-admin" />
                                  <span className="whitespace-nowrap text-xs text-admin-muted">
                                    {t('settings.chainPresets.chainTrigger')}
                                  </span>
                                </span>
                              ) : null}
                              <div
                                className={cn(
                                  'rounded-[10px] border border-admin bg-admin-surface px-3 py-2',
                                  primary && 'border-primary/50 bg-primary/5',
                                  disabled && 'border-dashed opacity-55',
                                )}
                              >
                                <div className="flex items-center gap-1.5">
                                  <span className="truncate text-[13px] font-semibold text-admin">
                                    {entry ? entry.displayName || entry.modelSpec : `#${entryId}`}
                                  </span>
                                  {primary ? (
                                    <span className="rounded-full bg-primary/10 px-1.5 py-0.5 text-xs font-medium text-primary">
                                      {t('settings.chainPrimary')}
                                    </span>
                                  ) : null}
                                </div>
                                <div className="truncate text-xs text-admin-muted">
                                  {entry ? entry.modelSpec : `#${entryId}`}
                                </div>
                              </div>
                            </Fragment>
                          )
                        })}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}

      {draft ? (
        <FormDialog
          title={
            draft.id == null
              ? t('settings.chainPresets.editorNew')
              : t('settings.chainPresets.editorEdit')
          }
          closeLabel={t('settings.chainPresets.cancel')}
          onClose={() => setDraft(null)}
        >
          <div className="space-y-4">
            <FormField required label={t('settings.chainPresets.name')}>
              <input
                className={adminFieldClass}
                value={draft.name}
                onChange={(event) => setDraft({ ...draft, name: event.target.value })}
              />
            </FormField>
            <div className="space-y-1">
              <span className="text-xs font-medium text-admin-muted">
                {t('settings.chainPresets.candidates')}
              </span>
              <div className="max-h-48 space-y-1 overflow-y-auto rounded-lg border border-admin p-2">
                {entries.map((entry) => (
                  <label key={entry.id} className="flex cursor-pointer items-center gap-2 text-sm text-admin">
                    <input
                      type="checkbox"
                      checked={draft.entryIds.includes(entry.id)}
                      onChange={() => toggleMember(entry.id)}
                    />
                    <span className={cn(!entry.enabled && 'text-admin-muted')}>
                      {entryLabel(entry)}
                      <span className="ml-1 text-xs text-admin-muted">
                        {(entry.mode ?? 'KEY') === 'CLI' ? 'CLI' : 'KEY'}
                        {entry.defaultEntry ? ` · ${t('settings.chainPresets.defaultMark')}` : ''}
                        {entry.enabled ? '' : ` · ${t('settings.chainPresets.disabledMark')}`}
                      </span>
                    </span>
                  </label>
                ))}
              </div>
            </div>
            <div className="space-y-1">
              <span className="text-xs font-medium text-admin-muted">
                {t('settings.chainPresets.members')}
              </span>
              {draft.entryIds.length === 0 ? (
                <p className="text-sm text-admin-muted">{t('settings.chainPresets.noMembers')}</p>
              ) : (
                <ol className="space-y-1">
                  {draft.entryIds.map((entryId, index) => (
                    <li key={entryId} className="flex items-center gap-2 text-sm text-admin">
                      <span className="w-6 text-admin-muted">{index + 1}.</span>
                      <span className="flex-1">
                        {entryById.get(entryId) ? entryLabel(entryById.get(entryId)!) : `#${entryId}`}
                      </span>
                      <button
                        type="button"
                        className={adminGhostButtonClass}
                        aria-label={t('settings.chainPresets.moveUp')}
                        disabled={index === 0}
                        onClick={() => moveMember(index, -1)}
                      >
                        <ArrowUp className="h-3.5 w-3.5" aria-hidden />
                      </button>
                      <button
                        type="button"
                        className={adminGhostButtonClass}
                        aria-label={t('settings.chainPresets.moveDown')}
                        disabled={index === draft.entryIds.length - 1}
                        onClick={() => moveMember(index, 1)}
                      >
                        <ArrowDown className="h-3.5 w-3.5" aria-hidden />
                      </button>
                      <button
                        type="button"
                        className={adminGhostButtonClass}
                        aria-label={t('settings.chainPresets.remove')}
                        onClick={() => toggleMember(entryId)}
                      >
                        <X className="h-3.5 w-3.5" aria-hidden />
                      </button>
                    </li>
                  ))}
                </ol>
              )}
            </div>
            <div className={adminFormActionsClass}>
              <button
                type="button"
                className={adminSecondaryButtonClass}
                onClick={() => setDraft(null)}
              >
                {t('settings.chainPresets.cancel')}
              </button>
              <button
                type="button"
                className={adminPrimaryButtonClass}
                disabled={!canUpdate || draft.name.trim() === '' || saveMutation.isPending}
                onClick={() => saveMutation.mutate(draft)}
              >
                {saveMutation.isPending ? <Spinner /> : null}
                {saveMutation.isPending
                  ? t('settings.chainPresets.saving')
                  : t('settings.chainPresets.save')}
              </button>
            </div>
          </div>
        </FormDialog>
      ) : null}

      {deleting ? (
        <FormDialog
          title={t('settings.chainPresets.deleteConfirmTitle')}
          closeLabel={t('settings.chainPresets.cancel')}
          onClose={() => setDeleting(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('settings.chainPresets.deleteConfirmBody', { name: deleting.name })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setDeleting(null)}
            >
              {t('settings.chainPresets.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(deleting.id)}
            >
              {deleteMutation.isPending ? <Spinner /> : null}
              {t('settings.chainPresets.delete')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </article>
  )
}
