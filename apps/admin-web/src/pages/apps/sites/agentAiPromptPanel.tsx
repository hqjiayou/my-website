import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
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
      className="space-y-4"
      onSubmit={(event) => {
        event.preventDefault()
        if (!canUpdate) return
        saveMutation.mutate()
      }}
    >
      <label className="block">
        <span className="text-sm font-medium text-foreground">{t('aiWorkspace.prompt.systemPrompt')}</span>
        <textarea
          className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
          rows={8}
          value={promptValue}
          disabled={!canUpdate}
          onChange={(event) => setSystemPrompt(event.target.value)}
        />
      </label>
      <label className="block max-w-xs">
        <span className="text-sm font-medium text-foreground">{t('aiWorkspace.prompt.temperature')}</span>
        <input
          className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm"
          type="number"
          min={0}
          max={2}
          step={0.1}
          value={temperatureValue}
          disabled={!canUpdate}
          onChange={(event) => setTemperature(event.target.value)}
        />
      </label>
      <PermissionGate code={PERMISSION_CODES.aiPromptUpdate}>
        <button
          type="submit"
          disabled={saveMutation.isPending}
          className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
        >
          {saveMutation.isPending ? t('aiWorkspace.prompt.saving') : t('aiWorkspace.prompt.save')}
        </button>
      </PermissionGate>
    </form>
  )
}
