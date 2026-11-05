import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Settings2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { agentApi } from '@/api/apps/agentApi'
import { aiApi } from '@/api/apps/aiApi'
import { adminFieldClass, adminPrimaryButtonClass } from '@/components/admin/controls'
import { ROUTES } from '@/router/config/routePaths'
import { PermissionGate } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

/**
 * AI 配置预设引用（Issue #211 预设本体、#213 UoW-D 引用面）：下拉选一份平台
 * 配置预设保存即切换引用；选「不引用」保存即清除。预设本体（systemPrompt /
 * temperature / 默认 skill 集）的新建与编辑在平台区「配置预设」页做，本面板
 * 只管引用——旧的 per-site Prompt 配置（prompt-profile）端点与编辑面已退役。
 */
export function AgentAiConfigPresetPanel({ agentId }: { agentId: number }) {
  const { t } = useTranslation('aiAdmin')
  const { t: tSites } = useTranslation('sites')
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<string | null>(null)
  const [syncedKey, setSyncedKey] = useState<string | null>(null)

  const siteQuery = useQuery({
    queryKey: ['sites', agentId],
    queryFn: () => agentApi.getSite(agentId),
  })
  const presetsQuery = useQuery({
    queryKey: ['ai', 'config-presets'],
    queryFn: () => aiApi.listConfigPresets(),
  })

  const savedId = siteQuery.data?.configPresetId
  if (siteQuery.data) {
    const key = savedId == null ? '' : String(savedId)
    if (key !== syncedKey) {
      setSelected(key)
      setSyncedKey(key)
    }
  }

  const presets = presetsQuery.data ?? []
  const currentPreset = savedId == null ? undefined : presets.find((preset) => preset.id === savedId)
  const referenceBroken = savedId != null && presetsQuery.data != null && currentPreset == null
  const selectedValue = selected ?? ''

  const saveMutation = useMutation({
    mutationFn: () =>
      agentApi.updateAgentConfigPreset(agentId, {
        configPresetId: selectedValue === '' ? null : Number(selectedValue),
      }),
    onSuccess: () => {
      toast.success(t('prompts.reference.saved'))
      setSyncedKey(null)
      void queryClient.invalidateQueries({ queryKey: ['sites'] })
      void queryClient.invalidateQueries({ queryKey: ['ai', 'config-presets'] })
    },
    onError: (saveError: Error) => toast.error(saveError.message),
  })

  if (siteQuery.isPending || presetsQuery.isPending) {
    return <p className="text-sm text-muted-foreground">{tSites('aiWorkspace.loading')}</p>
  }
  if (siteQuery.isError || presetsQuery.isError) {
    const loadError = siteQuery.error ?? presetsQuery.error
    return (
      <div className="rounded-lg border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis">
        <p>{loadError instanceof Error ? loadError.message : tSites('aiWorkspace.error')}</p>
        <button
          type="button"
          className="mt-2 underline"
          onClick={() => {
            void siteQuery.refetch()
            void presetsQuery.refetch()
          }}
        >
          {tSites('aiWorkspace.retry')}
        </button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <p className="text-xs font-medium text-muted-foreground">{t('prompts.reference.current')}</p>
        {savedId == null ? (
          <p className="mt-1.5 text-sm text-foreground">{t('prompts.reference.none')}</p>
        ) : referenceBroken ? (
          <p className="mt-1.5 text-sm text-warning-emphasis">{t('prompts.reference.notFound')}</p>
        ) : (
          <div className="mt-1.5 flex flex-col gap-2">
            <p className="flex flex-wrap items-center gap-2 text-sm font-semibold text-foreground">
              {currentPreset?.name}
              {currentPreset?.temperature != null ? (
                <span className="rounded-full bg-card-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                  {t('configPresets.temperatureTag', { value: currentPreset.temperature })}
                </span>
              ) : null}
              {currentPreset ? (
                <span className="rounded-full bg-card-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                  {t('configPresets.skillCount', { count: currentPreset.defaultSkillIds.length })}
                </span>
              ) : null}
            </p>
            <div>
              <p className="mb-1.5 text-xs font-medium text-muted-foreground">
                {t('prompts.reference.systemPrompt')}
              </p>
              {currentPreset?.systemPrompt?.trim() ? (
                <pre className="max-h-48 overflow-auto whitespace-pre-wrap rounded-lg border border-border bg-card-muted/40 p-3 font-mono text-[13px] leading-6 text-foreground">
                  {currentPreset.systemPrompt}
                </pre>
              ) : (
                <p className="text-sm text-muted-foreground">{t('prompts.reference.noPrompt')}</p>
              )}
            </div>
          </div>
        )}
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <label className="flex items-center gap-2">
          <span className="text-xs font-medium text-muted-foreground">{t('prompts.reference.pick')}</span>
          <select
            className={adminFieldClass}
            style={{ maxWidth: 320 }}
            value={selectedValue}
            disabled={saveMutation.isPending}
            onChange={(event) => setSelected(event.target.value)}
            aria-label={t('prompts.reference.pick')}
          >
            <option value="">{t('prompts.reference.noPreset')}</option>
            {presets.map((preset) => (
              <option key={preset.id} value={preset.id}>
                {preset.name}
              </option>
            ))}
          </select>
        </label>
        <PermissionGate code={PERMISSION_CODES.agentUpdate}>
          <button
            type="button"
            className={adminPrimaryButtonClass}
            disabled={saveMutation.isPending || selected === null}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t('prompts.reference.saving') : t('prompts.reference.save')}
          </button>
        </PermissionGate>
        <Link
          to={ROUTES.ai.configPresets}
          className="inline-flex items-center gap-1.5 text-sm font-medium text-primary underline-offset-2 hover:underline"
        >
          <Settings2 className="h-4 w-4" aria-hidden />
          {t('prompts.reference.manage')}
        </Link>
      </div>
    </div>
  )
}
