import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link2 } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { adminFieldClass, adminPrimaryButtonClass } from '@/components/admin/controls'
import { PermissionGate } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

/**
 * 对话链预设引用（Issue #210 UoW-7）：下拉选一条平台链预设保存即换链；
 * 选「未引用」保存即解除引用，对话链为空、对话会诚实 BLOCKED。
 * 预设本身的新建/编辑/删除在 AI 设置页的链预设卡里做。
 */
export function AgentAiChainPresetSection({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<string>('')
  const [syncedKey, setSyncedKey] = useState<string | null>(null)

  const referenceQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'chain-preset'],
    queryFn: () => aiApi.getAgentChainPreset(agentId),
  })
  const presetsQuery = useQuery({
    queryKey: ['ai', 'chain-presets'],
    queryFn: () => aiApi.listChainPresets(),
  })
  const entriesQuery = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
  })

  const saved = referenceQuery.data
  if (saved) {
    const key = saved.chainPresetId == null ? '' : String(saved.chainPresetId)
    if (key !== syncedKey) {
      setSelected(key)
      setSyncedKey(key)
    }
  }

  const presets = presetsQuery.data ?? []
  const entryById = new Map((entriesQuery.data ?? []).map((entry) => [entry.id, entry]))
  const selectedPreset = presets.find((preset) => String(preset.id) === selected)

  const saveMutation = useMutation({
    mutationFn: () => aiApi.updateAgentChainPreset(agentId, selected === '' ? null : Number(selected)),
    onSuccess: () => {
      toast.success(t('aiWorkspace.chainPreset.saved'))
      void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai'] })
    },
    onError: (saveError: Error) => toast.error(saveError.message),
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
      {referenceQuery.isError || presetsQuery.isError ? (
        <p className="text-sm text-danger-emphasis">{t('aiWorkspace.chainPreset.loadError')}</p>
      ) : (
        <div className="flex flex-wrap items-center gap-2">
          <select
            className={adminFieldClass}
            style={{ maxWidth: 320 }}
            value={selected}
            disabled={referenceQuery.isPending || presetsQuery.isPending}
            onChange={(event) => setSelected(event.target.value)}
            aria-label={t('aiWorkspace.chainPreset.title')}
          >
            <option value="">{t('aiWorkspace.chainPreset.none')}</option>
            {presets.map((preset) => (
              <option key={preset.id} value={preset.id}>
                {preset.name}
              </option>
            ))}
          </select>
          <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
            <button
              type="button"
              className={adminPrimaryButtonClass}
              disabled={saveMutation.isPending || referenceQuery.isPending}
              onClick={() => saveMutation.mutate()}
            >
              {t('aiWorkspace.chainPreset.save')}
            </button>
          </PermissionGate>
        </div>
      )}
      {selectedPreset ? (
        <p className="mt-2 text-xs text-muted-foreground">
          {t('aiWorkspace.chainPreset.members')}：
          {selectedPreset.entryIds.length === 0
            ? t('aiWorkspace.chainPreset.noMembers')
            : selectedPreset.entryIds
                .map((entryId) => {
                  const entry = entryById.get(entryId)
                  return entry ? `${entry.displayName || entry.modelSpec}` : `#${entryId}`
                })
                .join(' → ')}
        </p>
      ) : null}
    </section>
  )
}
