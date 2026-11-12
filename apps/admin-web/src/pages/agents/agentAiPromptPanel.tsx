import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Settings2, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { FormDialog, ResourcePickerDrawer, Spinner } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminFieldClass,
  adminFilterFieldClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { useUnsavedDirtyKey } from '@/hooks/useUnsavedChangesGuard'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'
import { PermissionGate } from '@/router/permission'
import type { AiAgentPromptConfig, AiPreviewSkill } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

import { SkillCatalogItem } from './agentAiSkillCatalog'

type Draft = {
  systemPrompt: string
  temperature: string
  defaultSkillIds: string[]
}

function draftFromConfig(config: AiAgentPromptConfig): Draft {
  return {
    systemPrompt: config.systemPrompt ?? '',
    temperature: config.temperature == null ? '' : String(config.temperature),
    defaultSkillIds: [...config.defaultSkillIds],
  }
}

/** temperature 输入解析：空＝null（用 Provider 默认）；非法（非数或超出 0–2）返回 undefined。 */
function parseTemperature(raw: string): number | null | undefined {
  const trimmed = raw.trim()
  if (trimmed === '') return null
  const value = Number(trimmed)
  if (!Number.isFinite(value) || value < 0 || value > 2) return undefined
  return value
}

/**
 * 智能体 Prompt 配置（Issue #214 UoW-4）：Prompt 是智能体自有数据——本地副本
 * （systemPrompt＋temperature）与自有默认 skill 集都存 agent 行，本面板整表
 * 编辑保存即生效（正式对话/试聊同口径）。模板只提供拷贝：下拉选模板「套用」
 * 把模板的 prompt 部分覆入本地副本并刷新来源留痕，「另存为模板」把当前已保存
 * 的副本存入模板库；套用后改模板不影响本地副本。取代 #213 的配置预设引用面。
 */
export function AgentAiPromptPanel({ agentId }: { agentId: number }) {
  const { t } = useTranslation('aiAdmin')
  const { t: tSites } = useTranslation('sites')
  const queryClient = useQueryClient()
  // 草稿为 null 表示跟随服务端值；编辑后才落草稿，保存/套用成功后回到跟随。
  const [draft, setDraft] = useState<Draft | null>(null)
  const [skillPickerOpen, setSkillPickerOpen] = useState(false)
  const [templateSelection, setTemplateSelection] = useState('')
  const [templateName, setTemplateName] = useState('')
  const [confirmingLoad, setConfirmingLoad] = useState(false)

  const configQueryKey = ['sites', agentId, 'ai', 'prompt'] as const
  const configQuery = useQuery({
    queryKey: configQueryKey,
    queryFn: () => aiApi.getAgentPromptConfig(agentId),
  })
  const templatesQuery = useQuery({
    queryKey: ['ai', 'prompt-templates'],
    queryFn: () => aiApi.listPromptTemplates(),
  })
  const catalogQuery = useQuery({
    queryKey: ['ai', 'skill-catalog'],
    queryFn: () => aiApi.getSkillCatalog(),
  })
  const previewSkillsQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'preview', 'skills'],
    queryFn: () => aiApi.listPreviewSkills(agentId),
  })

  const server = configQuery.data
  const serverDraft = server ? draftFromConfig(server) : null
  const current = draft ?? serverDraft
  const dirty =
    draft != null &&
    serverDraft != null &&
    (draft.systemPrompt !== serverDraft.systemPrompt ||
      draft.temperature !== serverDraft.temperature ||
      draft.defaultSkillIds.join(',') !== serverDraft.defaultSkillIds.join(','))
  useUnsavedDirtyKey(`agent-prompt-${agentId}`, dirty)
  // temperature 非法（非数或超出 0–2）走字段级错误：错误文字替换字段下帮助位，不再只弹 toast。
  const temperatureInvalid = current != null && parseTemperature(current.temperature) === undefined
  const templates = templatesQuery.data ?? []
  const skillCatalog: AiPreviewSkill[] = useMemo(() => {
    const fromPlatform = catalogQuery.data?.skills ?? []
    if (fromPlatform.length > 0) {
      return fromPlatform.map((skill) => ({
        id: skill.id,
        description: skill.description,
        version: skill.version,
      }))
    }
    return previewSkillsQuery.data ?? []
  }, [catalogQuery.data, previewSkillsQuery.data])
  const catalogById = useMemo(
    () => new Map(skillCatalog.map((skill) => [skill.id, skill])),
    [skillCatalog],
  )

  const applySavedConfig = (config: AiAgentPromptConfig) => {
    queryClient.setQueryData(configQueryKey, config)
    setDraft(null)
    void queryClient.invalidateQueries({ queryKey: ['sites'] })
  }

  const saveMutation = useMutation({
    mutationFn: (value: Draft & { temperatureValue: number | null }) =>
      aiApi.updateAgentPromptConfig(agentId, {
        systemPrompt: value.systemPrompt.trim() === '' ? null : value.systemPrompt,
        temperature: value.temperatureValue,
        defaultSkillIds: value.defaultSkillIds,
      }),
    onSuccess: (config) => {
      applySavedConfig(config)
      toast.success(t('prompts.saved'))
    },
    onError: (saveError: Error) => toast.error(saveError.message),
  })

  const loadMutation = useMutation({
    mutationFn: () => aiApi.loadAgentPromptTemplate(agentId, Number(templateSelection)),
    onSuccess: (config) => {
      setConfirmingLoad(false)
      applySavedConfig(config)
      toast.success(t('prompts.templateLoaded'))
    },
    onError: (loadError: Error) => toast.error(loadError.message),
  })

  const saveAsMutation = useMutation({
    mutationFn: () => aiApi.saveAgentPromptAsTemplate(agentId, templateName.trim()),
    onSuccess: () => {
      setTemplateName('')
      void queryClient.invalidateQueries({ queryKey: ['ai', 'prompt-templates'] })
      void queryClient.invalidateQueries({ queryKey: configQueryKey })
      void queryClient.invalidateQueries({ queryKey: ['sites'] })
      toast.success(t('prompts.templateSaved'))
    },
    onError: (saveAsError: Error) => toast.error(saveAsError.message),
  })

  function removeSkill(skillId: string) {
    if (!current) return
    setDraft({
      ...current,
      defaultSkillIds: current.defaultSkillIds.filter((id) => id !== skillId),
    })
  }

  function submitDraft() {
    if (!current) return
    const temperatureValue = parseTemperature(current.temperature)
    if (temperatureValue === undefined) {
      // 非法时字段下已显示错误文字（temperatureInvalid），此处只拦截提交。
      return
    }
    saveMutation.mutate({ ...current, temperatureValue })
  }

  if (configQuery.isPending) {
    return <p className="text-sm text-admin-muted">{tSites('aiWorkspace.loading')}</p>
  }
  if (configQuery.isError || !current || !server) {
    return (
      <div className="rounded-lg border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis" role="alert">
        <p>{configQuery.error instanceof Error ? configQuery.error.message : tSites('aiWorkspace.error')}</p>
        <button type="button" className="admin-focus-ring mt-2 underline" onClick={() => void configQuery.refetch()}>
          {tSites('aiWorkspace.retry')}
        </button>
      </div>
    )
  }

  return (
    <div className="flex max-w-[880px] flex-col gap-4">
      {dirty ? (
        <p
          className="rounded-lg bg-warning-muted px-2.5 py-1 text-xs font-medium text-warning-emphasis"
          role="status"
        >
          {tSites('aiWorkspace.chainPreset.dirty')}
        </p>
      ) : null}

      <div className="rounded-lg border border-admin bg-admin-surface-muted/40 px-3 py-2">
        <p className="text-xs font-medium text-admin-muted">{t('prompts.sourceLabel')}</p>
        <p className="mt-1 text-sm text-admin">
          {server.promptTemplateId == null
            ? t('prompts.sourceNone')
            : (server.promptTemplateName ?? `#${server.promptTemplateId}`)}
        </p>
        <p className="mt-1 text-xs text-admin-muted">{t('prompts.sourceCopyHint')}</p>
      </div>

      <label className="block space-y-1.5">
        <span className="text-xs font-medium text-admin-muted">{t('prompts.systemPrompt')}</span>
        <textarea
          className={cn(adminFieldClass, 'min-h-[12rem] font-mono text-sm leading-6')}
          rows={10}
          maxLength={8000}
          value={current.systemPrompt}
          placeholder={t('prompts.systemPromptHint')}
          onChange={(event) => setDraft({ ...current, systemPrompt: event.target.value })}
        />
        <span className="flex items-baseline justify-between gap-2">
          <span className="text-xs text-admin-muted">{t('prompts.systemPromptHint')}</span>
          <span className="shrink-0 text-xs text-admin-muted">
            {current.systemPrompt.length} / 8000
          </span>
        </span>
      </label>

      <label className="block space-y-1.5">
        <span className="text-xs font-medium text-admin-muted">{t('prompts.temperature')}</span>
        <input
          className={adminFilterFieldClass}
          type="number"
          min={0}
          max={2}
          step={0.1}
          value={current.temperature}
          placeholder={t('prompts.temperatureHint')}
          aria-invalid={temperatureInvalid || undefined}
          onChange={(event) => setDraft({ ...current, temperature: event.target.value })}
        />
        {temperatureInvalid ? (
          <span className="block text-xs text-danger-emphasis" role="alert">
            {t('prompts.temperatureInvalid')}
          </span>
        ) : (
          <span className="block text-xs text-admin-muted">{t('prompts.temperatureHint')}</span>
        )}
      </label>

      <div className="space-y-2">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="text-xs font-medium text-admin-muted">{t('prompts.defaultSkills')}</span>
          <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setSkillPickerOpen(true)}
            >
              <Plus className="h-4 w-4" aria-hidden />
              {t('prompts.pickSkills')}
            </button>
          </PermissionGate>
        </div>
        <p className="text-xs text-admin-muted">{t('prompts.defaultSkillsHint')}</p>
        {current.defaultSkillIds.length === 0 ? (
          <p className="text-sm text-admin-muted">{t('prompts.noSkills')}</p>
        ) : (
          <ul className="space-y-2">
            {current.defaultSkillIds.map((skillId) => {
              const meta = catalogById.get(skillId)
              return (
                <li
                  key={skillId}
                  className="flex items-start justify-between gap-2 rounded-lg border border-admin px-3 py-2"
                >
                  <div className="min-w-0 flex-1">
                    {meta ? (
                      <SkillCatalogItem id={meta.id} version={meta.version} description={meta.description} />
                    ) : (
                      <div>
                        <span className="font-mono text-sm text-admin">{skillId}</span>
                        <p className="text-xs text-warning-emphasis">{t('prompts.skillMissing')}</p>
                      </div>
                    )}
                  </div>
                  <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
                    <button
                      type="button"
                      className={adminGhostButtonClass}
                      aria-label={t('prompts.removeSkill')}
                      onClick={() => removeSkill(skillId)}
                    >
                      <X className="h-3.5 w-3.5" aria-hidden />
                    </button>
                  </PermissionGate>
                </li>
              )
            })}
          </ul>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
          <button
            type="button"
            className={adminPrimaryButtonClass}
            disabled={!dirty || saveMutation.isPending || temperatureInvalid}
            onClick={submitDraft}
          >
            {saveMutation.isPending ? <Spinner /> : null}
            {t('prompts.save')}
          </button>
        </PermissionGate>
        {dirty ? (
          <button
            type="button"
            className={adminGhostButtonClass}
            disabled={saveMutation.isPending}
            onClick={() => setDraft(null)}
          >
            {t('prompts.discard')}
          </button>
        ) : null}
      </div>

      <ResourcePickerDrawer
        open={skillPickerOpen}
        title={t('prompts.skillPickerTitle')}
        description={t('prompts.skillPickerHint')}
        items={skillCatalog}
        getId={(skill) => skill.id}
        getAccessibleName={(skill) =>
          skill.version ? `${skill.id} v${skill.version}` : skill.id
        }
        initialSelectedIds={current.defaultSkillIds.filter((id) => catalogById.has(id))}
        alreadySelectedIds={[]}
        alreadySelectedSelectable
        searchText={(skill) => `${skill.id} ${skill.description ?? ''} ${skill.version ?? ''}`}
        columns={[
          {
            id: 'id',
            header: t('prompts.skillColId'),
            cell: (skill) => (
              <SkillCatalogItem id={skill.id} version={skill.version} description={skill.description} />
            ),
          },
          {
            id: 'version',
            header: t('prompts.skillColVersion'),
            cell: (skill) => skill.version ?? '—',
          },
          {
            id: 'description',
            header: t('prompts.skillColDescription'),
            cell: (skill) => (
              <span className="line-clamp-2 text-xs text-admin-muted">{skill.description ?? '—'}</span>
            ),
          },
        ]}
        isLoading={catalogQuery.isPending && previewSkillsQuery.isPending}
        isError={catalogQuery.isError && previewSkillsQuery.isError}
        errorMessage={t('prompts.catalogUnavailable')}
        onRetry={() => {
          void catalogQuery.refetch()
          void previewSkillsQuery.refetch()
        }}
        confirmLabel={t('prompts.applySkills')}
        onConfirm={(ids) => {
          const known = ids.filter((id) => catalogById.has(id))
          const preservedUnknown = current.defaultSkillIds.filter((id) => !catalogById.has(id))
          setDraft({ ...current, defaultSkillIds: [...known, ...preservedUnknown] })
          setSkillPickerOpen(false)
        }}
        onClose={() => setSkillPickerOpen(false)}
      />

      <div className="flex flex-wrap items-center gap-2 border-t border-admin pt-3">
        <span className="text-xs font-medium text-admin-muted">{t('prompts.templateLabel')}</span>
        <select
          className={adminFilterFieldClass}
          value={templateSelection}
          disabled={templatesQuery.isPending}
          onChange={(event) => setTemplateSelection(event.target.value)}
          aria-label={t('prompts.templateLabel')}
        >
          <option value="">{t('prompts.templateNone')}</option>
          {templates.map((template) => (
            <option key={template.id} value={template.id}>
              {template.name}
            </option>
          ))}
        </select>
        <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
          <button
            type="button"
            className={adminSecondaryButtonClass}
            disabled={templateSelection === '' || loadMutation.isPending}
            onClick={() => setConfirmingLoad(true)}
          >
            {t('prompts.loadTemplate')}
          </button>
        </PermissionGate>
        <input
          className={adminFilterFieldClass}
          value={templateName}
          onChange={(event) => setTemplateName(event.target.value)}
          placeholder={t('prompts.templateNamePlaceholder')}
          aria-label={t('prompts.templateNamePlaceholder')}
        />
        <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
          <button
            type="button"
            className={adminSecondaryButtonClass}
            disabled={dirty || templateName.trim() === '' || saveAsMutation.isPending}
            onClick={() => saveAsMutation.mutate()}
          >
            {saveAsMutation.isPending ? <Spinner /> : null}
            {t('prompts.saveAsTemplate')}
          </button>
        </PermissionGate>
        <Link
          to={ROUTES.ai.promptTemplates}
          className="inline-flex items-center gap-1.5 text-sm font-medium text-primary underline-offset-2 hover:underline"
        >
          <Settings2 className="h-4 w-4" aria-hidden />
          {t('prompts.manageTemplates')}
        </Link>
      </div>

      {confirmingLoad ? (
        <FormDialog
          title={t('prompts.loadConfirmTitle')}
          closeLabel={t('prompts.loadConfirmCancel')}
          onClose={() => setConfirmingLoad(false)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('prompts.loadConfirmBody', {
              name: templates.find((template) => template.id === Number(templateSelection))?.name ?? '',
            })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setConfirmingLoad(false)}
            >
              {t('prompts.loadConfirmCancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={loadMutation.isPending}
              onClick={() => loadMutation.mutate()}
            >
              {loadMutation.isPending ? <Spinner /> : null}
              {t('prompts.loadConfirmAction')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </div>
  )
}
