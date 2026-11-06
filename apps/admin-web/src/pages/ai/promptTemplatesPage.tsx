import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

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
import type { AiPromptTemplate } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

type Draft = {
  id: number | null
  name: string
  systemPrompt: string
  temperature: string
  version: number | null
}

function draftFromTemplate(template: AiPromptTemplate): Draft {
  return {
    id: template.id,
    name: template.name,
    systemPrompt: template.systemPrompt ?? '',
    temperature: template.temperature == null ? '' : String(template.temperature),
    version: template.version,
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
 * Prompt 模板管理页（Issue #214 UoW-4）：命名提示词资产＝名称＋systemPrompt＋
 * temperature，归工具组独立管理。模板与 agent 只有拷贝关系：智能体在提示词
 * 面板套用即复制为本地副本，之后改/删模板都不影响已套用的智能体。模板不带
 * skill 集——默认技能是 agent 自有数据，在智能体侧维护（自配置预设拆分）。
 */
export function AiPromptTemplatesPage() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiPromptUpdate)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [deleting, setDeleting] = useState<AiPromptTemplate | null>(null)

  const templatesQuery = useQuery({
    queryKey: ['ai', 'prompt-templates'],
    queryFn: () => aiApi.listPromptTemplates(),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['ai', 'prompt-templates'] })
  }

  const saveMutation = useMutation({
    mutationFn: (value: Draft & { temperatureValue: number | null }) =>
      value.id == null
        ? aiApi.createPromptTemplate({
            name: value.name.trim(),
            systemPrompt: value.systemPrompt.trim() === '' ? null : value.systemPrompt,
            temperature: value.temperatureValue,
          })
        : aiApi.updatePromptTemplate(value.id, {
            name: value.name.trim(),
            systemPrompt: value.systemPrompt.trim() === '' ? null : value.systemPrompt,
            temperature: value.temperatureValue,
            version: value.version ?? 0,
          }),
    onSuccess: () => {
      setDraft(null)
      invalidate()
      toast.success(t('promptTemplates.saved'))
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : t('error')),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => aiApi.deletePromptTemplate(id),
    onSuccess: () => {
      setDeleting(null)
      invalidate()
      toast.success(t('promptTemplates.deleted'))
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : t('error')),
  })

  function submitDraft() {
    if (!draft) return
    const temperatureValue = parseTemperature(draft.temperature)
    if (temperatureValue === undefined) {
      toast.error(t('promptTemplates.temperatureInvalid'))
      return
    }
    saveMutation.mutate({ ...draft, temperatureValue })
  }

  const templates = templatesQuery.data ?? []

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('promptTemplates.title')} description={t('promptTemplates.description')} />
      <article className="space-y-3 rounded-xl border border-admin bg-admin-surface p-5">
        <div className="flex items-start justify-between gap-3">
          <p className="text-sm text-admin-muted">{t('promptTemplates.hint')}</p>
          <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
            <button
              type="button"
              className={adminPrimaryButtonClass}
              onClick={() => setDraft({ id: null, name: '', systemPrompt: '', temperature: '', version: null })}
            >
              {t('promptTemplates.add')}
            </button>
          </PermissionGate>
        </div>

        {templatesQuery.isPending ? (
          <p className="text-sm text-admin-muted">{t('loading')}</p>
        ) : templatesQuery.isError ? (
          <p className="text-sm text-danger-emphasis">{t('promptTemplates.loadFailed')}</p>
        ) : templates.length === 0 ? (
          <p className="text-sm text-admin-muted">{t('promptTemplates.empty')}</p>
        ) : (
          <ul className="space-y-2">
            {templates.map((template) => (
              <li
                key={template.id}
                className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-admin bg-admin-surface-muted px-3 py-2"
              >
                <span className="text-sm font-semibold text-admin">{template.name}</span>
                <span className="min-w-0 max-w-xl flex-1 truncate text-xs text-admin-muted">
                  {template.systemPrompt?.trim()
                    ? template.systemPrompt.trim()
                    : t('promptTemplates.noPrompt')}
                </span>
                <span className="flex flex-wrap items-center gap-1 text-xs text-admin-muted">
                  <span className="rounded bg-admin-surface px-1.5 py-0.5">
                    {t('promptTemplates.temperatureTag', {
                      value: template.temperature == null ? '—' : String(template.temperature),
                    })}
                  </span>
                  <span className="rounded bg-admin-surface px-1.5 py-0.5">
                    {new Date(template.updatedAt).toLocaleString()}
                  </span>
                </span>
                <span className="ml-auto flex gap-1">
                  <button
                    type="button"
                    className={adminGhostButtonClass}
                    disabled={!canUpdate}
                    onClick={() => setDraft(draftFromTemplate(template))}
                  >
                    {t('promptTemplates.edit')}
                  </button>
                  <button
                    type="button"
                    className={adminDangerGhostButtonSmClass}
                    disabled={!canUpdate}
                    onClick={() => setDeleting(template)}
                  >
                    {t('promptTemplates.delete')}
                  </button>
                </span>
              </li>
            ))}
          </ul>
        )}
      </article>

      {draft ? (
        <FormDialog
          title={draft.id == null ? t('promptTemplates.editorNew') : t('promptTemplates.editorEdit')}
          closeLabel={t('promptTemplates.cancel')}
          onClose={() => setDraft(null)}
        >
          <div className="space-y-4">
            <label className="block space-y-1">
              <span className="text-xs font-medium text-admin-muted">{t('promptTemplates.name')}</span>
              <input
                className={adminFieldClass}
                value={draft.name}
                maxLength={255}
                onChange={(event) => setDraft({ ...draft, name: event.target.value })}
              />
            </label>
            <label className="block space-y-1">
              <span className="text-xs font-medium text-admin-muted">
                {t('promptTemplates.systemPrompt')}
              </span>
              <textarea
                className={adminFieldClass}
                rows={8}
                maxLength={8000}
                value={draft.systemPrompt}
                placeholder={t('promptTemplates.systemPromptHint')}
                onChange={(event) => setDraft({ ...draft, systemPrompt: event.target.value })}
              />
              <span className="block text-right text-xs text-admin-muted">
                {draft.systemPrompt.length} / 8000
              </span>
            </label>
            <label className="block space-y-1">
              <span className="text-xs font-medium text-admin-muted">
                {t('promptTemplates.temperature')}
              </span>
              <input
                className={adminFieldClass}
                type="number"
                min={0}
                max={2}
                step={0.1}
                value={draft.temperature}
                placeholder={t('promptTemplates.temperatureHint')}
                onChange={(event) => setDraft({ ...draft, temperature: event.target.value })}
              />
            </label>
            <div className={adminFormActionsClass}>
              <button type="button" className={adminSecondaryButtonClass} onClick={() => setDraft(null)}>
                {t('promptTemplates.cancel')}
              </button>
              <button
                type="button"
                className={adminPrimaryButtonClass}
                disabled={!canUpdate || draft.name.trim() === '' || saveMutation.isPending}
                onClick={submitDraft}
              >
                {saveMutation.isPending ? t('promptTemplates.saving') : t('promptTemplates.save')}
              </button>
            </div>
          </div>
        </FormDialog>
      ) : null}

      {deleting ? (
        <FormDialog
          title={t('promptTemplates.deleteConfirmTitle')}
          closeLabel={t('promptTemplates.cancel')}
          onClose={() => setDeleting(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('promptTemplates.deleteConfirmBody', { name: deleting.name })}
          </p>
          <div className={adminFormActionsClass}>
            <button type="button" className={adminSecondaryButtonClass} onClick={() => setDeleting(null)}>
              {t('promptTemplates.cancel')}
            </button>
            <button
              type="button"
              className={adminPrimaryButtonClass}
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(deleting.id)}
            >
              {t('promptTemplates.delete')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </section>
  )
}
