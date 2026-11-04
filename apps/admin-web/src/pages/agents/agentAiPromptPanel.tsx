import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { adminFieldClass, adminPrimaryButtonClass } from '@/components/admin/controls'
import { cn } from '@/lib/cn'
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
      className="flex h-full min-h-0 flex-col gap-4"
      onSubmit={(event) => {
        event.preventDefault()
        if (!canUpdate) return
        saveMutation.mutate()
      }}
    >
      <div className="flex flex-wrap items-end justify-between gap-3">
        <label className="block">
          <span className="text-xs font-medium text-muted-foreground">{t('aiWorkspace.prompt.temperature')}</span>
          <div className="mt-1.5 flex w-56 items-center gap-2">
            <input
              type="range"
              min={0}
              max={2}
              step={0.1}
              value={temperatureValue}
              disabled={!canUpdate}
              onChange={(event) => setTemperature(event.target.value)}
              className="h-1.5 min-w-0 flex-1 accent-[rgb(var(--primary))] disabled:opacity-50"
            />
            <input
              className={cn(adminFieldClass, 'w-14 shrink-0 px-2 text-center')}
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
        <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
          <button type="submit" disabled={saveMutation.isPending} className={adminPrimaryButtonClass}>
            {saveMutation.isPending ? t('aiWorkspace.prompt.saving') : t('aiWorkspace.prompt.save')}
          </button>
        </PermissionGate>
      </div>

      <label className="flex min-h-0 flex-1 flex-col">
        <div className="mb-1.5 flex items-center justify-between gap-2">
          <span className="text-sm font-medium text-foreground">{t('aiWorkspace.prompt.systemPrompt')}</span>
          <span className="font-mono text-xs tabular-nums text-muted-foreground">{promptValue.length}</span>
        </div>
        <textarea
          className={`${adminFieldClass} min-h-[20rem] flex-1 resize-y bg-card-muted/40 font-mono text-[13px] leading-6`}
          value={promptValue}
          disabled={!canUpdate}
          onChange={(event) => setSystemPrompt(event.target.value)}
          spellCheck={false}
        />
      </label>
    </form>
  )
}
