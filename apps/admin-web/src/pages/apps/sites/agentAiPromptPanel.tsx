import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { adminFieldClass, adminPrimaryButtonClass } from '@/components/admin/controls'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

export function AgentAiPromptPanel({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiPromptUpdate)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', agentId, 'ai', 'prompt'],
    queryFn: () => aiApi.getPromptProfile(agentId),
  })

  const [systemPrompt, setSystemPrompt] = useState<string | null>(null)
  const [temperature, setTemperature] = useState<string | null>(null)

  const promptValue = systemPrompt ?? data?.systemPrompt ?? ''
  const temperatureValue =
    temperature ?? (data?.temperature == null ? '' : String(data.temperature))

  const saveMutation = useMutation({
    mutationFn: () =>
      aiApi.upsertPromptProfile(agentId, {
        systemPrompt: promptValue.trim() || null,
        temperature: temperatureValue.trim() === '' ? null : Number(temperatureValue),
        version: data?.version ?? undefined,
      }),
    onSuccess: () => {
      toast.success(t('aiWorkspace.prompt.saved'))
      setSystemPrompt(null)
      setTemperature(null)
      void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai', 'prompt'] })
    },
  })

  if (isPending) return <p className="text-sm text-muted-foreground">{t('aiWorkspace.loading')}</p>
  if (isError) {
    return (
      <div className="rounded-lg border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis">
        <p>{error instanceof Error ? error.message : t('aiWorkspace.error')}</p>
        <button type="button" className="mt-2 underline" onClick={() => void refetch()}>
          {t('aiWorkspace.retry')}
        </button>
      </div>
    )
  }

  return (
    <form
      className="space-y-5 pb-16"
      onSubmit={(event) => {
        event.preventDefault()
        if (!canUpdate) return
        saveMutation.mutate()
      }}
    >
      <label className="block">
        <div className="flex items-center justify-between">
          <span className="text-sm font-medium text-admin">{t('aiWorkspace.prompt.systemPrompt')}</span>
          <span className="text-xs text-admin-muted">{promptValue.length}</span>
        </div>
        <textarea
          className={`${adminFieldClass} mt-1 bg-card-muted/50 leading-relaxed`}
          rows={12}
          value={promptValue}
          disabled={!canUpdate}
          onChange={(event) => setSystemPrompt(event.target.value)}
        />
      </label>
      <label className="block max-w-sm">
        <span className="text-sm font-medium text-admin">{t('aiWorkspace.prompt.temperature')}</span>
        <div className="mt-1 flex items-center gap-3">
          <input
            type="range"
            min={0}
            max={2}
            step={0.1}
            value={temperatureValue}
            disabled={!canUpdate}
            onChange={(event) => setTemperature(event.target.value)}
            className="h-1.5 flex-1 accent-[rgb(var(--primary))] disabled:opacity-50"
          />
          <input
            className={`${adminFieldClass} w-20`}
            type="number"
            min={0}
            max={2}
            step={0.1}
            value={temperatureValue}
            disabled={!canUpdate}
            onChange={(event) => setTemperature(event.target.value)}
          />
        </div>
      </label>
      <div className="sticky bottom-4 flex items-center justify-end rounded-[14px] border border-admin bg-admin-surface p-3 shadow-admin-pop">
        <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
          <button type="submit" disabled={saveMutation.isPending} className={adminPrimaryButtonClass}>
            {saveMutation.isPending ? t('aiWorkspace.prompt.saving') : t('aiWorkspace.prompt.save')}
          </button>
        </PermissionGate>
      </div>
    </form>
  )
}
