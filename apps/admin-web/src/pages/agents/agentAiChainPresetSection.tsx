import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowDown, ArrowUp, Link2, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { FilterTabs } from '@/components/admin'
import {
  adminFieldClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { PermissionGate } from '@/router/permission'
import type { AiAgentChain, AiChainMode, AiModelEntry } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

const MODES: AiChainMode[] = ['KEY', 'CLI']

function entryLabel(entry: AiModelEntry): string {
  return entry.displayName ? `${entry.displayName} · ${entry.modelSpec}` : entry.modelSpec
}

function serverIds(chain: AiAgentChain | undefined, mode: AiChainMode): number[] {
  if (!chain) return []
  return mode === 'KEY' ? chain.keyEntryIds : chain.cliEntryIds
}

/**
 * 对话链编辑器（Issue #214 UoW-3 链内置化）：链是 agent 自有字段，按模式
 * （Key/CLI）各一份有序成员名单——顺序即组链顺序，名单内第一个默认入口是
 * 主用、其余按序备援，备援不跨模式；某模式名单为空即该模式无链、对话诚实
 * BLOCKED。模板（模型页维护）只能整表载入（拷贝）或把当前已保存的链另存，
 * 载入后与模板脱钩。取代 #210 的「引用预设」形态。
 */
export function AgentAiChainPresetSection({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const [mode, setMode] = useState<AiChainMode>('KEY')
  // 草稿为 null 表示跟随服务端值；编辑后才落草稿，保存/载入成功后回到跟随。
  const [drafts, setDrafts] = useState<Record<AiChainMode, number[] | null>>({ KEY: null, CLI: null })
  const [addSelection, setAddSelection] = useState('')
  const [templateSelection, setTemplateSelection] = useState('')
  const [templateName, setTemplateName] = useState('')

  const chainQueryKey = ['sites', agentId, 'ai', 'chain'] as const
  const chainQuery = useQuery({
    queryKey: chainQueryKey,
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

  const draft = drafts[mode]
  const serverList = serverIds(chainQuery.data, mode)
  const currentIds = draft ?? serverList
  const dirty = draft != null && draft.join(',') !== serverList.join(',')
  const candidates = entries.filter(
    (entry) => (entry.mode ?? 'KEY') === mode && !currentIds.includes(entry.id),
  )

  const setDraft = (ids: number[]) =>
    setDrafts((previous) => ({ ...previous, [mode]: ids }))

  const moveMember = (index: number, delta: number) => {
    const target = index + delta
    if (target < 0 || target >= currentIds.length) return
    const ids = [...currentIds]
    const [moved] = ids.splice(index, 1)
    ids.splice(target, 0, moved)
    setDraft(ids)
  }

  const applySavedChain = (chain: AiAgentChain) => {
    queryClient.setQueryData(chainQueryKey, chain)
    setDrafts((previous) => ({ ...previous, [mode]: null }))
    void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai'] })
  }

  const saveMutation = useMutation({
    mutationFn: () => aiApi.updateAgentChain(agentId, mode, currentIds),
    onSuccess: (chain) => {
      applySavedChain(chain)
      toast.success(t('aiWorkspace.chainPreset.saved'))
    },
    onError: (saveError: Error) => toast.error(saveError.message),
  })

  const loadMutation = useMutation({
    mutationFn: () => aiApi.loadAgentChainTemplate(agentId, mode, Number(templateSelection)),
    onSuccess: (chain) => {
      applySavedChain(chain)
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
    <section className="mt-3 rounded-xl border border-border bg-card-muted/40 p-4">
      <div className="mb-3 flex items-center gap-2">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-info-muted text-info-emphasis">
          <Link2 className="h-4 w-4" aria-hidden />
        </span>
        <h3 className="text-sm font-semibold text-foreground">{t('aiWorkspace.chainPreset.title')}</h3>
      </div>
      <p className="mb-3 text-xs text-muted-foreground">{t('aiWorkspace.chainPreset.hint')}</p>
      {chainQuery.isError || entriesQuery.isError ? (
        <p className="text-sm text-danger-emphasis">{t('aiWorkspace.chainPreset.loadError')}</p>
      ) : (
        <div className="space-y-3">
          <FilterTabs
            tabs={MODES.map((value) => ({
              key: value,
              label: value === 'KEY' ? t('aiWorkspace.chainPreset.tabKey') : t('aiWorkspace.chainPreset.tabCli'),
              count: (drafts[value] ?? serverIds(chainQuery.data, value)).length,
            }))}
            activeKey={mode}
            onChange={(key) => {
              setMode(key as AiChainMode)
              setAddSelection('')
            }}
          />

          {currentIds.length === 0 ? (
            <p className="text-sm text-muted-foreground">{t('aiWorkspace.chainPreset.noMembers')}</p>
          ) : (
            <ol className="space-y-1">
              {currentIds.map((entryId, index) => {
                const entry = entryById.get(entryId)
                return (
                  <li key={entryId} className="flex items-center gap-2 text-sm text-foreground">
                    <span className="w-6 text-muted-foreground">{index + 1}.</span>
                    <span className={cn('flex-1', entry && !entry.enabled && 'text-muted-foreground')}>
                      {entry ? entryLabel(entry) : `#${entryId}`}
                      {entry ? (
                        <span className="ml-1 text-[11px] text-muted-foreground">
                          {entry.defaultEntry ? t('aiWorkspace.chainPreset.defaultMark') : ''}
                          {entry.enabled ? '' : ` · ${t('aiWorkspace.chainPreset.disabledMark')}`}
                        </span>
                      ) : null}
                    </span>
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
                      onClick={() => setDraft(currentIds.filter((id) => id !== entryId))}
                    >
                      <X className="h-3.5 w-3.5" aria-hidden />
                    </button>
                  </li>
                )
              })}
            </ol>
          )}

          <div className="flex flex-wrap items-center gap-2">
            <select
              className={adminFieldClass}
              style={{ maxWidth: 320 }}
              value={addSelection}
              disabled={chainQuery.isPending || entriesQuery.isPending}
              onChange={(event) => setAddSelection(event.target.value)}
              aria-label={t('aiWorkspace.chainPreset.addPlaceholder')}
            >
              <option value="">{t('aiWorkspace.chainPreset.addPlaceholder')}</option>
              {candidates.map((entry) => (
                <option key={entry.id} value={entry.id}>
                  {entryLabel(entry)}
                  {entry.defaultEntry ? ` · ${t('aiWorkspace.chainPreset.defaultMark')}` : ''}
                  {entry.enabled ? '' : ` · ${t('aiWorkspace.chainPreset.disabledMark')}`}
                </option>
              ))}
            </select>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              disabled={addSelection === ''}
              onClick={() => {
                setDraft([...currentIds, Number(addSelection)])
                setAddSelection('')
              }}
            >
              {t('aiWorkspace.chainPreset.add')}
            </button>
            <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
              <button
                type="button"
                className={adminPrimaryButtonClass}
                disabled={!dirty || saveMutation.isPending}
                onClick={() => saveMutation.mutate()}
              >
                {t('aiWorkspace.chainPreset.save')}
              </button>
            </PermissionGate>
          </div>

          <div className="flex flex-wrap items-center gap-2 border-t border-border pt-3">
            <span className="text-xs font-medium text-muted-foreground">
              {t('aiWorkspace.chainPreset.templateLabel')}
            </span>
            <select
              className={adminFieldClass}
              style={{ maxWidth: 260 }}
              value={templateSelection}
              disabled={presetsQuery.isPending}
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
                onClick={() => loadMutation.mutate()}
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
        </div>
      )}
    </section>
  )
}
