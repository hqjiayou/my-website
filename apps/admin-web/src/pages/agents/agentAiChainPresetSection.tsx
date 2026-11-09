import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertCircle, ArrowDown, ArrowUp, Plus, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { FormDialog, ResourcePickerDrawer, StatusTag } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminFieldClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
  adminSecondaryButtonSmClass,
} from '@/components/admin/controls'
import { useUnsavedDirtyKey } from '@/hooks/useUnsavedChangesGuard'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type { AiAgentChain, AiChainMode, AiChainPreset, AiModelEntry } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

const ROW_ORDER: AiChainMode[] = ['CLI', 'KEY']

function serverIds(chain: AiAgentChain | undefined, mode: AiChainMode): number[] {
  if (!chain) return []
  return mode === 'KEY' ? chain.keyEntryIds : chain.cliEntryIds
}

interface ChainModeRowProps {
  agentId: number
  mode: AiChainMode
  chain: AiAgentChain | undefined
  chainPending: boolean
  entries: AiModelEntry[]
  entryById: Map<number, AiModelEntry>
  entriesPending: boolean
  presets: AiChainPreset[]
  presetsPending: boolean
}

/**
 * 单个模式的链行（§10 组链可视化）：行首模式分组标，行内节点卡序列——
 * 第一个节点为主用、已停用入口虚线半透明，节点间连线带触发条件小字；
 * 行下方是该模式自己的增删/排序入口、模板载入与另存控件，与原页签下
 * 的 per-mode 逻辑一一对应。
 */
function ChainModeRow({
  agentId,
  mode,
  chain,
  chainPending,
  entries,
  entryById,
  entriesPending,
  presets,
  presetsPending,
}: ChainModeRowProps) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  // 草稿为 null 表示跟随服务端值；编辑后才落草稿，保存/载入成功后回到跟随。
  const [draft, setDraftState] = useState<number[] | null>(null)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [templateSelection, setTemplateSelection] = useState('')
  const [templateName, setTemplateName] = useState('')
  const [confirmingLoad, setConfirmingLoad] = useState(false)

  const chainQueryKey = ['sites', agentId, 'ai', 'chain'] as const
  const serverList = serverIds(chain, mode)
  const currentIds = draft ?? serverList
  const dirty = draft != null && draft.join(',') !== serverList.join(',')
  useUnsavedDirtyKey(`agent-chain-${agentId}-${mode}`, dirty)
  const modeEntries = entries.filter((entry) => (entry.mode ?? 'KEY') === mode)

  const moveMember = (index: number, delta: number) => {
    const target = index + delta
    if (target < 0 || target >= currentIds.length) return
    const ids = [...currentIds]
    const [moved] = ids.splice(index, 1)
    ids.splice(target, 0, moved)
    setDraftState(ids)
  }

  const applySavedChain = (saved: AiAgentChain) => {
    queryClient.setQueryData(chainQueryKey, saved)
    setDraftState(null)
    void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai'] })
  }

  const saveMutation = useMutation({
    mutationFn: () => aiApi.updateAgentChain(agentId, mode, currentIds),
    onSuccess: (saved) => {
      applySavedChain(saved)
      toast.success(t('aiWorkspace.chainPreset.saved'))
    },
    onError: (saveError: Error) => toast.error(saveError.message),
  })

  const loadMutation = useMutation({
    mutationFn: () => aiApi.loadAgentChainTemplate(agentId, mode, Number(templateSelection)),
    onSuccess: (saved) => {
      setConfirmingLoad(false)
      applySavedChain(saved)
      toast.success(t('aiWorkspace.chainPreset.templateLoaded'))
    },
    onError: (loadError: Error) => toast.error(loadError.message),
  })

  const saveAsMutation = useMutation({
    mutationFn: () => aiApi.saveAgentChainAsTemplate(agentId, mode, templateName.trim()),
    onSuccess: () => {
      setTemplateName('')
      void queryClient.invalidateQueries({ queryKey: ['ai', 'chain-presets'] })
      toast.success(t('aiWorkspace.chainPreset.templateSaved'))
    },
    onError: (saveAsError: Error) => toast.error(saveAsError.message),
  })

  return (
    <div className="space-y-3">
      <div className="flex items-start gap-2">
        <span className="w-16 shrink-0 pt-2 text-xs font-semibold uppercase text-admin-muted">
          {mode}
        </span>
        {chainPending ? (
          <div className="flex flex-1 flex-wrap items-center gap-2" aria-hidden>
            {[0, 1].map((index) => (
              <div
                key={index}
                className="h-14 w-40 animate-pulse rounded-[10px] border border-admin bg-admin-surface-muted"
              />
            ))}
          </div>
        ) : currentIds.length === 0 ? (
          <p className="pt-2 text-sm text-admin-muted">{t('aiWorkspace.chainPreset.noMembers')}</p>
        ) : (
          <div className="flex flex-1 flex-wrap items-center gap-2">
            {currentIds.flatMap((entryId, index) => {
              const entry = entryById.get(entryId)
              const isPrimary = index === 0
              const card = (
                <div
                  key={`entry-${entryId}`}
                  className={cn(
                    'rounded-[10px] border border-admin bg-admin-surface px-3 py-2',
                    isPrimary && 'border-primary/50 bg-primary/5',
                    entry && !entry.enabled && 'border-dashed opacity-55',
                  )}
                >
                  <div className="flex items-center gap-1.5">
                    <span className="truncate text-[13px] font-semibold">
                      {entry ? entry.displayName || entry.modelSpec : `#${entryId}`}
                    </span>
                    {isPrimary ? (
                      <span className="shrink-0 rounded-full bg-primary/10 px-1.5 py-0.5 text-xs font-medium text-primary">
                        {t('aiWorkspace.chainPreset.primaryMark')}
                      </span>
                    ) : null}
                    {!entry ? (
                      <StatusTag status="warning" tone="warning" label={t('aiWorkspace.chainPreset.missingMark')} />
                    ) : !entry.enabled ? (
                      <StatusTag status="DISABLED" tone="danger" label={t('aiWorkspace.chainPreset.disabledMark')} />
                    ) : null}
                  </div>
                  {entry ? (
                    <div className="truncate font-mono text-xs text-admin-muted">{entry.modelSpec}</div>
                  ) : (
                    <div className="text-xs text-warning-emphasis">{t('aiWorkspace.chainPreset.missingHint')}</div>
                  )}
                  <div className="mt-1.5 flex gap-1">
                    <button
                      type="button"
                      className={adminGhostButtonClass}
                      aria-label={t('aiWorkspace.chainPreset.moveUp')}
                      disabled={index === 0}
                      onClick={() => moveMember(index, -1)}
                    >
                      <ArrowUp className="h-3.5 w-3.5" aria-hidden />
                    </button>
                    <button
                      type="button"
                      className={adminGhostButtonClass}
                      aria-label={t('aiWorkspace.chainPreset.moveDown')}
                      disabled={index === currentIds.length - 1}
                      onClick={() => moveMember(index, 1)}
                    >
                      <ArrowDown className="h-3.5 w-3.5" aria-hidden />
                    </button>
                    <button
                      type="button"
                      className={adminGhostButtonClass}
                      aria-label={t('aiWorkspace.chainPreset.remove')}
                      onClick={() => setDraftState(currentIds.filter((id) => id !== entryId))}
                    >
                      <X className="h-3.5 w-3.5" aria-hidden />
                    </button>
                  </div>
                </div>
              )
              if (index === 0) return [card]
              return [
                <div
                  key={`link-${entryId}`}
                  className="flex flex-col items-center gap-1 self-center px-1"
                >
                  <span className="w-8 border-t border-admin" />
                  <span className="whitespace-nowrap text-xs text-admin-muted">
                    {t('aiWorkspace.chainPreset.chainTrigger')}
                  </span>
                </div>,
                card,
              ]
            })}
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
          <button
            type="button"
            className={adminSecondaryButtonClass}
            disabled={chainPending || entriesPending || !canUpdate}
            onClick={() => setPickerOpen(true)}
          >
            <Plus className="h-4 w-4" aria-hidden />
            {t('aiWorkspace.chainPreset.addFromPicker')}
          </button>
        </PermissionGate>
        <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
          <button
            type="button"
            className={adminPrimaryButtonClass}
            disabled={!dirty || saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t('aiWorkspace.chainPreset.saving') : t('aiWorkspace.chainPreset.save')}
          </button>
        </PermissionGate>
        {dirty ? (
          <button
            type="button"
            className={adminGhostButtonClass}
            disabled={saveMutation.isPending}
            onClick={() => setDraftState(null)}
          >
            {t('aiWorkspace.chainPreset.discard')}
          </button>
        ) : null}
      </div>

      <ResourcePickerDrawer
        open={pickerOpen}
        title={t('aiWorkspace.chainPreset.pickerTitle', { mode })}
        description={t('aiWorkspace.chainPreset.pickerHint')}
        items={modeEntries}
        getId={(entry) => String(entry.id)}
        alreadySelectedIds={currentIds.map(String)}
        alreadySelectedLabel={t('aiWorkspace.chainPreset.alreadyInChain')}
        disabledReason={(entry) =>
          entry.enabled ? null : t('aiWorkspace.chainPreset.disabledMark')
        }
        searchText={(entry) => `${entry.displayName} ${entry.modelSpec} ${entry.mode}`}
        columns={[
          {
            id: 'name',
            header: t('aiWorkspace.chainPreset.colName'),
            cell: (entry) => (
              <div>
                <div className="font-medium text-admin">{entry.displayName || entry.modelSpec}</div>
                {entry.defaultEntry ? (
                  <span className="text-xs text-admin-muted">{t('aiWorkspace.chainPreset.platformDefaultHint')}</span>
                ) : null}
              </div>
            ),
          },
          {
            id: 'spec',
            header: t('aiWorkspace.chainPreset.colSpec'),
            cell: (entry) => <span className="font-mono text-xs">{entry.modelSpec}</span>,
          },
          {
            id: 'mode',
            header: t('aiWorkspace.chainPreset.colMode'),
            cell: (entry) => entry.mode ?? mode,
          },
          {
            id: 'status',
            header: t('aiWorkspace.chainPreset.colStatus'),
            cell: (entry) =>
              entry.enabled ? t('status.ACTIVE') : t('aiWorkspace.chainPreset.disabledMark'),
          },
        ]}
        isLoading={entriesPending}
        confirmLabel={t('aiWorkspace.chainPreset.add')}
        onConfirm={(ids) => {
          const next = [...currentIds]
          for (const id of ids) {
            const numeric = Number(id)
            if (!Number.isFinite(numeric) || next.includes(numeric)) continue
            const entry = entryById.get(numeric)
            if (!entry || !entry.enabled) continue
            next.push(numeric)
          }
          setDraftState(next)
          setPickerOpen(false)
        }}
        onClose={() => setPickerOpen(false)}
      />

      <div className="flex flex-wrap items-center gap-2 border-t border-admin pt-3">
        <span className="text-xs font-medium text-admin-muted">
          {t('aiWorkspace.chainPreset.templateLabel')}
        </span>
        <select
          className={adminFieldClass}
          style={{ maxWidth: 260 }}
          value={templateSelection}
          disabled={presetsPending}
          onChange={(event) => setTemplateSelection(event.target.value)}
          aria-label={t('aiWorkspace.chainPreset.templateLabel')}
        >
          <option value="">{t('aiWorkspace.chainPreset.templateNone')}</option>
          {presets.map((preset) => (
            <option key={preset.id} value={preset.id}>
              {preset.name}
            </option>
          ))}
        </select>
        <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
          <button
            type="button"
            className={adminSecondaryButtonClass}
            disabled={templateSelection === '' || loadMutation.isPending}
            onClick={() => setConfirmingLoad(true)}
          >
            {t('aiWorkspace.chainPreset.loadTemplate')}
          </button>
        </PermissionGate>
        <input
          className={adminFieldClass}
          style={{ maxWidth: 200 }}
          value={templateName}
          onChange={(event) => setTemplateName(event.target.value)}
          placeholder={t('aiWorkspace.chainPreset.templateNamePlaceholder')}
          aria-label={t('aiWorkspace.chainPreset.templateNamePlaceholder')}
        />
        <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
          <button
            type="button"
            className={adminSecondaryButtonClass}
            disabled={dirty || templateName.trim() === '' || saveAsMutation.isPending}
            onClick={() => saveAsMutation.mutate()}
          >
            {t('aiWorkspace.chainPreset.saveAsTemplate')}
          </button>
        </PermissionGate>
      </div>

      {confirmingLoad ? (
        <FormDialog
          title={t('aiWorkspace.chainPreset.loadConfirmTitle')}
          closeLabel={t('aiWorkspace.chainPreset.loadConfirmCancel')}
          onClose={() => setConfirmingLoad(false)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('aiWorkspace.chainPreset.loadConfirmBody', {
              name: presets.find((preset) => preset.id === Number(templateSelection))?.name ?? '',
            })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setConfirmingLoad(false)}
            >
              {t('aiWorkspace.chainPreset.loadConfirmCancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={loadMutation.isPending}
              onClick={() => loadMutation.mutate()}
            >
              {t('aiWorkspace.chainPreset.loadConfirmAction')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </div>
  )
}

/**
 * 对话链编辑器（Issue #214 UoW-3 链内置化）：链是 agent 自有字段，按模式
 * （Key/CLI）各一份有序成员名单——顺序即组链顺序，名单内第一个默认入口是
 * 主用、其余按序备援，备援不跨模式；某模式名单为空即该模式无链、对话诚实
 * BLOCKED。模板（模型页维护）只能整表载入（拷贝）或把当前已保存的链另存，
 * 载入后与模板脱钩。取代 #210 的「引用预设」形态。呈现在 #214 UI 修复批改
 * 为双行同屏的组链可视化（§10），不再用页签切换。
 */
export function AgentAiChainPresetSection({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const { t: tCommon } = useTranslation('common')

  const chainQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'chain'],
    queryFn: () => aiApi.getAgentChain(agentId),
  })
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
  const presets = presetsQuery.data ?? []

  const retryLoad = () => {
    if (chainQuery.isError) void chainQuery.refetch()
    if (entriesQuery.isError) void entriesQuery.refetch()
  }

  return (
    <div className="space-y-4">
      <p className="text-xs text-admin-muted">{t('aiWorkspace.chainPreset.hint')}</p>
      {chainQuery.isError || entriesQuery.isError ? (
        <div className="flex items-center gap-2.5 rounded-xl border border-danger/35 bg-danger-muted px-4 py-3">
          <AlertCircle className="h-4.5 w-4.5 shrink-0 text-danger-emphasis" aria-hidden />
          <p className="min-w-0 flex-1 text-sm text-danger-emphasis">
            {t('aiWorkspace.chainPreset.loadError')}
          </p>
          <button
            type="button"
            className={cn(adminSecondaryButtonSmClass, 'shrink-0')}
            onClick={retryLoad}
          >
            {tCommon('table.retry')}
          </button>
        </div>
      ) : (
        <div className="space-y-5">
          {ROW_ORDER.map((rowMode) => (
            <ChainModeRow
              key={`${agentId}-${rowMode}`}
              agentId={agentId}
              mode={rowMode}
              chain={chainQuery.data}
              chainPending={chainQuery.isPending}
              entries={entries}
              entryById={entryById}
              entriesPending={entriesQuery.isPending}
              presets={presets}
              presetsPending={presetsQuery.isPending}
            />
          ))}
        </div>
      )}
    </div>
  )
}
