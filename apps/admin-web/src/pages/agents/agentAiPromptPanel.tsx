import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Loader2, Settings2, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { FormDialog } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminFieldClass,
  adminFilterFieldClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { ROUTES } from '@/router/config/routePaths'
import { PermissionGate } from '@/router/permission'
import type { AiAgentPromptConfig } from '@/types/ai'
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
  const [skillInput, setSkillInput] = useState('')
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
  const skillsQuery = useQuery({
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
  // temperature 非法（非数或超出 0–2）走字段级错误：错误文字替换字段下帮助位，不再只弹 toast。
  const temperatureInvalid = current != null && parseTemperature(current.temperature) === undefined
  const templates = templatesQuery.data ?? []
  const skillCatalog = skillsQuery.data ?? []

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

  function toggleSkill(skillId: string) {
    if (!current) return
    const selected = current.defaultSkillIds.includes(skillId)
    setDraft({
      ...current,
      defaultSkillIds: selected
        ? current.defaultSkillIds.filter((id) => id !== skillId)
        : [...current.defaultSkillIds, skillId],
    })
  }

  function addSkillByInput() {
    const skillId = skillInput.trim()
    if (!skillId || !current) return
    setSkillInput('')
    if (!current.defaultSkillIds.includes(skillId)) {
      setDraft({ ...current, defaultSkillIds: [...current.defaultSkillIds, skillId] })
    }
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
      <div className="rounded-lg border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis">
        <p>{configQuery.error instanceof Error ? configQuery.error.message : tSites('aiWorkspace.error')}</p>
        <button type="button" className="mt-2 underline" onClick={() => void configQuery.refetch()}>
          {tSites('aiWorkspace.retry')}
        </button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-xs text-admin-muted">{t('prompts.hint')}</p>

      <div>
        <p className="text-xs font-medium text-admin-muted">{t('prompts.sourceLabel')}</p>
        <p className="mt-1.5 text-sm text-admin">
          {server.promptTemplateId == null
            ? t('prompts.sourceNone')
            : (server.promptTemplateName ?? `#${server.promptTemplateId}`)}
        </p>
      </div>

      <label className="block space-y-1.5">
        <span className="text-xs font-medium text-admin-muted">{t('prompts.systemPrompt')}</span>
        <textarea
          className={adminFieldClass}
          rows={8}
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
          <span className="block text-xs text-danger-emphasis">{t('prompts.temperatureInvalid')}</span>
        ) : (
          <span className="block text-xs text-admin-muted">{t('prompts.temperatureHint')}</span>
        )}
      </label>

      <div className="space-y-2">
        <span className="text-xs font-medium text-admin-muted">{t('prompts.defaultSkills')}</span>
        {current.defaultSkillIds.length === 0 ? (
          <p className="text-sm text-admin-muted">{t('prompts.noSkills')}</p>
        ) : (
          <ul className="flex flex-wrap gap-1.5">
            {current.defaultSkillIds.map((skillId) => (
              <li
                key={skillId}
                className="admin-status-tag inline-flex items-center gap-1.5 rounded-full bg-admin-surface-muted px-2 text-xs font-medium text-admin"
              >
                <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-admin-muted-foreground" aria-hidden />
                <span className="font-mono">{skillId}</span>
                <button
                  type="button"
                  className="admin-focus-ring -mr-1 inline-flex rounded-full p-0.5 text-admin-muted transition hover:bg-admin-surface hover:text-admin-danger"
                  aria-label={t('prompts.removeSkill')}
                  onClick={() => toggleSkill(skillId)}
                >
                  <X className="h-3.5 w-3.5" aria-hidden />
                </button>
              </li>
            ))}
          </ul>
        )}
        {skillCatalog.length > 0 ? (
          <div className="max-h-48 space-y-1 overflow-y-auto rounded-lg border border-admin p-2">
            {skillCatalog.map((skill) => (
              <div key={skill.id} className="flex items-start gap-2">
                <input
                  type="checkbox"
                  className="admin-check mt-0.5 shrink-0"
                  checked={current.defaultSkillIds.includes(skill.id)}
                  onChange={() => toggleSkill(skill.id)}
                  aria-label={skill.id}
                />
                <div className="min-w-0 flex-1">
                  <SkillCatalogItem id={skill.id} version={skill.version} description={skill.description} />
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-xs text-admin-muted">{t('prompts.catalogUnavailable')}</p>
        )}
        <div className="flex gap-2">
          <input
            className={adminFieldClass}
            value={skillInput}
            placeholder={t('prompts.skillInputHint')}
            onChange={(event) => setSkillInput(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                event.preventDefault()
                addSkillByInput()
              }
            }}
          />
          <button
            type="button"
            className={adminGhostButtonClass}
            disabled={skillInput.trim() === ''}
            onClick={addSkillByInput}
          >
            {t('prompts.addSkill')}
          </button>
        </div>
      </div>

      <div>
        <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
          <button
            type="button"
            className={adminPrimaryButtonClass}
            disabled={!dirty || saveMutation.isPending}
            onClick={submitDraft}
          >
            {saveMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : null}
            {t('prompts.save')}
          </button>
        </PermissionGate>
      </div>

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
              {t('prompts.loadConfirmAction')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </div>
  )
}
