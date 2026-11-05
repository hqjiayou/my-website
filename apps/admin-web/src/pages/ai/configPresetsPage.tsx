import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { agentApi } from '@/api/apps/agentApi'
import { aiApi } from '@/api/apps/aiApi'
import { AdminPageHeader, FormDialog } from '@/components/admin'
import {
  adminDangerGhostButtonSmClass,
  adminFieldClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type { AiConfigPreset } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

type Draft = {
  id: number | null
  name: string
  systemPrompt: string
  temperature: string
  defaultSkillIds: string[]
  version: number | null
}

function draftFromPreset(preset: AiConfigPreset): Draft {
  return {
    id: preset.id,
    name: preset.name,
    systemPrompt: preset.systemPrompt ?? '',
    temperature: preset.temperature == null ? '' : String(preset.temperature),
    defaultSkillIds: [...preset.defaultSkillIds],
    version: preset.version,
  }
}

/** temperature 输入解析：空＝null（不覆盖）；非法（非数或超出 0–2）返回 undefined。 */
function parseTemperature(raw: string): number | null | undefined {
  const trimmed = raw.trim()
  if (trimmed === '') return null
  const value = Number(trimmed)
  if (!Number.isFinite(value) || value < 0 || value > 2) return undefined
  return value
}

/**
 * AI 配置预设管理页（Issue #213 UoW-C；后端 CRUD 为 #211 UoW-8）：命名预设＝
 * systemPrompt 覆盖＋temperature＋默认 skill 集，智能体在基础信息/列表里引用。
 * 删除为软删：引用它的智能体解析时回退为无配置（服务端既有口径），不拦截。
 * skill 候选目录是平台级的（agent-services 技能目录），FE 取数接口挂在
 * 站点 preview 路径下、需带任一站点 id（后端仅以此校验站点存在、目录不按站过滤），
 * 故取站点列表首个站点借道；无站点或目录不可用时退化为已选 id 展示＋手工录入。
 */
export function AiConfigPresetsPage() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiPromptUpdate)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [deleting, setDeleting] = useState<AiConfigPreset | null>(null)
  const [skillInput, setSkillInput] = useState('')

  const presetsQuery = useQuery({
    queryKey: ['ai', 'config-presets'],
    queryFn: () => aiApi.listConfigPresets(),
  })

  const sitesQuery = useQuery({
    queryKey: ['ai', 'config-presets', 'skill-catalog-site'],
    queryFn: () => agentApi.listSites({ pageNum: 1, pageSize: 1 }),
    enabled: draft != null,
  })
  const catalogAgentId = sitesQuery.data?.records?.[0]?.id
  const skillsQuery = useQuery({
    queryKey: ['ai', 'config-presets', 'skill-catalog', catalogAgentId],
    queryFn: () => aiApi.listPreviewSkills(catalogAgentId!),
    enabled: draft != null && catalogAgentId != null,
  })
  const skillCatalog = skillsQuery.data ?? []

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['ai', 'config-presets'] })
  }

  const saveMutation = useMutation({
    mutationFn: (value: Draft & { temperatureValue: number | null }) =>
      value.id == null
        ? aiApi.createConfigPreset({
            name: value.name.trim(),
            systemPrompt: value.systemPrompt.trim() === '' ? null : value.systemPrompt,
            temperature: value.temperatureValue,
            defaultSkillIds: value.defaultSkillIds,
          })
        : aiApi.updateConfigPreset(value.id, {
            name: value.name.trim(),
            systemPrompt: value.systemPrompt.trim() === '' ? null : value.systemPrompt,
            temperature: value.temperatureValue,
            defaultSkillIds: value.defaultSkillIds,
            version: value.version ?? 0,
          }),
    onSuccess: () => {
      setDraft(null)
      invalidate()
      toast.success(t('configPresets.saved'))
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : t('error')),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => aiApi.deleteConfigPreset(id),
    onSuccess: () => {
      setDeleting(null)
      invalidate()
      toast.success(t('configPresets.deleted'))
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : t('error')),
  })

  function toggleSkill(skillId: string) {
    setDraft((current) => {
      if (!current) return current
      const selected = current.defaultSkillIds.includes(skillId)
      return {
        ...current,
        defaultSkillIds: selected
          ? current.defaultSkillIds.filter((id) => id !== skillId)
          : [...current.defaultSkillIds, skillId],
      }
    })
  }

  function addSkillByInput() {
    const skillId = skillInput.trim()
    if (!skillId) return
    setSkillInput('')
    setDraft((current) =>
      current && !current.defaultSkillIds.includes(skillId)
        ? { ...current, defaultSkillIds: [...current.defaultSkillIds, skillId] }
        : current,
    )
  }

  function submitDraft() {
    if (!draft) return
    const temperatureValue = parseTemperature(draft.temperature)
    if (temperatureValue === undefined) {
      toast.error(t('configPresets.temperatureInvalid'))
      return
    }
    saveMutation.mutate({ ...draft, temperatureValue })
  }

  const presets = presetsQuery.data ?? []

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('configPresets.title')} description={t('configPresets.description')} />
      <article className="space-y-3 rounded-xl border border-admin bg-admin-surface p-5">
        <div className="flex items-start justify-between gap-3">
          <p className="text-sm text-admin-muted">{t('configPresets.hint')}</p>
          <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
            <button
              type="button"
              className={adminPrimaryButtonClass}
              onClick={() => {
                setSkillInput('')
                setDraft({ id: null, name: '', systemPrompt: '', temperature: '', defaultSkillIds: [], version: null })
              }}
            >
              {t('configPresets.add')}
            </button>
          </PermissionGate>
        </div>

        {presetsQuery.isPending ? (
          <p className="text-sm text-admin-muted">{t('loading')}</p>
        ) : presetsQuery.isError ? (
          <p className="text-sm text-danger-emphasis">{t('configPresets.loadFailed')}</p>
        ) : presets.length === 0 ? (
          <p className="text-sm text-admin-muted">{t('configPresets.empty')}</p>
        ) : (
          <ul className="space-y-2">
            {presets.map((preset) => (
              <li
                key={preset.id}
                className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-admin bg-admin-surface-muted px-3 py-2"
              >
                <span className="text-sm font-semibold text-admin">{preset.name}</span>
                <span className="min-w-0 max-w-xl flex-1 truncate text-xs text-admin-muted">
                  {preset.systemPrompt?.trim()
                    ? preset.systemPrompt.trim()
                    : t('configPresets.noPrompt')}
                </span>
                <span className="flex flex-wrap items-center gap-1 text-xs text-admin-muted">
                  <span className="rounded bg-admin-surface px-1.5 py-0.5">
                    {t('configPresets.temperatureTag', {
                      value: preset.temperature == null ? '—' : String(preset.temperature),
                    })}
                  </span>
                  <span className="rounded bg-admin-surface px-1.5 py-0.5">
                    {t('configPresets.skillCount', { count: preset.defaultSkillIds.length })}
                  </span>
                  <span className="rounded bg-admin-surface px-1.5 py-0.5">
                    {new Date(preset.updatedAt).toLocaleString()}
                  </span>
                </span>
                <span className="ml-auto flex gap-1">
                  <button
                    type="button"
                    className={adminGhostButtonClass}
                    disabled={!canUpdate}
                    onClick={() => {
                      setSkillInput('')
                      setDraft(draftFromPreset(preset))
                    }}
                  >
                    {t('configPresets.edit')}
                  </button>
                  <button
                    type="button"
                    className={adminDangerGhostButtonSmClass}
                    disabled={!canUpdate}
                    onClick={() => setDeleting(preset)}
                  >
                    {t('configPresets.delete')}
                  </button>
                </span>
              </li>
            ))}
          </ul>
        )}
      </article>

      {draft ? (
        <FormDialog
          title={draft.id == null ? t('configPresets.editorNew') : t('configPresets.editorEdit')}
          closeLabel={t('configPresets.cancel')}
          onClose={() => setDraft(null)}
        >
          <div className="space-y-4">
            <label className="block space-y-1">
              <span className="text-xs font-medium text-admin-muted">{t('configPresets.name')}</span>
              <input
                className={adminFieldClass}
                value={draft.name}
                maxLength={255}
                onChange={(event) => setDraft({ ...draft, name: event.target.value })}
              />
            </label>
            <label className="block space-y-1">
              <span className="text-xs font-medium text-admin-muted">
                {t('configPresets.systemPrompt')}
              </span>
              <textarea
                className={adminFieldClass}
                rows={8}
                maxLength={8000}
                value={draft.systemPrompt}
                placeholder={t('configPresets.systemPromptHint')}
                onChange={(event) => setDraft({ ...draft, systemPrompt: event.target.value })}
              />
              <span className="block text-right text-xs text-admin-muted">
                {draft.systemPrompt.length} / 8000
              </span>
            </label>
            <label className="block space-y-1">
              <span className="text-xs font-medium text-admin-muted">
                {t('configPresets.temperature')}
              </span>
              <input
                className={adminFieldClass}
                type="number"
                min={0}
                max={2}
                step={0.1}
                value={draft.temperature}
                placeholder={t('configPresets.temperatureHint')}
                onChange={(event) => setDraft({ ...draft, temperature: event.target.value })}
              />
            </label>
            <div className="space-y-2">
              <span className="text-xs font-medium text-admin-muted">
                {t('configPresets.defaultSkills')}
              </span>
              {draft.defaultSkillIds.length === 0 ? (
                <p className="text-sm text-admin-muted">{t('configPresets.noSkills')}</p>
              ) : (
                <ul className="flex flex-wrap gap-1.5">
                  {draft.defaultSkillIds.map((skillId) => (
                    <li
                      key={skillId}
                      className="flex items-center gap-1 rounded bg-admin-surface-muted px-2 py-1 font-mono text-xs text-admin"
                    >
                      {skillId}
                      <button
                        type="button"
                        aria-label={t('configPresets.removeSkill')}
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
                    <label key={skill.id} className="flex cursor-pointer items-start gap-2 text-sm text-admin">
                      <input
                        type="checkbox"
                        className="admin-check mt-0.5"
                        checked={draft.defaultSkillIds.includes(skill.id)}
                        onChange={() => toggleSkill(skill.id)}
                      />
                      <span className="min-w-0">
                        <span className="font-mono text-xs">{skill.id}</span>
                        {skill.description ? (
                          <span className="block truncate text-xs text-admin-muted">{skill.description}</span>
                        ) : null}
                      </span>
                    </label>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-admin-muted">{t('configPresets.catalogUnavailable')}</p>
              )}
              <div className="flex gap-2">
                <input
                  className={adminFieldClass}
                  value={skillInput}
                  placeholder={t('configPresets.skillInputHint')}
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
                  {t('configPresets.addSkill')}
                </button>
              </div>
            </div>
            <div className={adminFormActionsClass}>
              <button type="button" className={adminSecondaryButtonClass} onClick={() => setDraft(null)}>
                {t('configPresets.cancel')}
              </button>
              <button
                type="button"
                className={adminPrimaryButtonClass}
                disabled={!canUpdate || draft.name.trim() === '' || saveMutation.isPending}
                onClick={submitDraft}
              >
                {saveMutation.isPending ? t('configPresets.saving') : t('configPresets.save')}
              </button>
            </div>
          </div>
        </FormDialog>
      ) : null}

      {deleting ? (
        <FormDialog
          title={t('configPresets.deleteConfirmTitle')}
          closeLabel={t('configPresets.cancel')}
          onClose={() => setDeleting(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('configPresets.deleteConfirmBody', { name: deleting.name })}
          </p>
          <div className={adminFormActionsClass}>
            <button type="button" className={adminSecondaryButtonClass} onClick={() => setDeleting(null)}>
              {t('configPresets.cancel')}
            </button>
            <button
              type="button"
              className={adminPrimaryButtonClass}
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(deleting.id)}
            >
              {t('configPresets.delete')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </section>
  )
}
