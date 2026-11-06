import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { agentApi } from '@/api/apps/agentApi'
import { aiApi } from '@/api/apps/aiApi'
import { FormDialog } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminDangerGhostButtonClass,
  adminFieldClass,
  adminFormActionsClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

/**
 * 供应商 API key 直连条目管理（原「API 管理」页 proxyServicesPage 的 KEY
 * 页签整块，Issue #215 起搬入「模型 API 管理」页作独立分节，逻辑与文案
 * 未改）：左栏 KEY 模式条目名单、右栏详情（spec／key hint／Base URL／
 * 使用归属），新建走弹窗、删除走危险确认弹窗。
 */
export function ApiKeyColumn() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [creating, setCreating] = useState(false)
  const [displayName, setDisplayName] = useState('')
  const [modelSpec, setModelSpec] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [deleting, setDeleting] = useState<{ id: number; displayName: string } | null>(null)

  const entriesQuery = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
  })
  const agentsQuery = useQuery({
    queryKey: ['agents', 'names-for-keys'],
    queryFn: () => agentApi.listSites({ pageNum: 1, pageSize: 100 }),
    retry: false,
  })
  const entries = (entriesQuery.data ?? []).filter((entry) => (entry.mode ?? 'KEY') === 'KEY')
  const selected = entries.find((entry) => entry.id === selectedId) ?? entries[0] ?? null

  function labelFor(agentId: number | null) {
    if (agentId == null) return t('proxyServices.keyUnbound')
    const name = agentsQuery.data?.records.find((agent) => agent.id === agentId)?.agentName
    return name ? t('proxyServices.keyBound', { name }) : t('proxyServices.keyBoundUnknown')
  }

  function resetDraft() {
    setDisplayName('')
    setModelSpec('')
    setBaseUrl('')
    setApiKey('')
  }

  const createMutation = useMutation({
    mutationFn: () =>
      aiApi.createModelEntry({
        displayName: displayName.trim() || modelSpec.trim(),
        modelSpec: modelSpec.trim(),
        apiKey: apiKey.trim(),
        baseUrl: baseUrl.trim() || null,
        embeddingSpec: null,
        enabled: true,
        defaultEntry: false,
        mode: 'KEY',
        alias: null,
      }),
    onSuccess: (created) => {
      toast.success(t('proxyServices.keySaved'))
      setCreating(false)
      resetDraft()
      setSelectedId(created.id)
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => aiApi.deleteModelEntry(id),
    onSuccess: () => {
      toast.success(t('proxyServices.keyDeleted'))
      setDeleting(null)
      setSelectedId(null)
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  return (
    <section className="overflow-hidden rounded-xl border border-admin bg-admin-surface">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-admin px-4 py-3">
        <span className="min-w-0 text-sm text-admin-muted">{t('proxyServices.keyHint')}</span>
        <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
          <button type="button" className={adminPrimaryButtonClass} onClick={() => setCreating(true)}>
            <Plus className="h-4 w-4" />
            {t('proxyServices.keyCreate')}
          </button>
        </PermissionGate>
      </div>
      {entriesQuery.isLoading ? <p className="px-4 py-6 text-sm text-admin-muted">{t('loading')}</p> : null}
      {entriesQuery.isError ? (
        <p className="px-4 py-6 text-sm text-danger-emphasis">
          {t('error')}{' '}
          <button type="button" className="underline" onClick={() => entriesQuery.refetch()}>
            {t('retry')}
          </button>
        </p>
      ) : null}
      {!entriesQuery.isLoading && !entriesQuery.isError && entries.length === 0 ? (
        <p className="px-4 py-10 text-sm text-admin-muted">{t('proxyServices.keyEmpty')}</p>
      ) : null}
      {entries.length > 0 ? (
        <div className="grid lg:grid-cols-[15rem_minmax(0,1fr)]">
          <div className="flex flex-col gap-0.5 border-b border-admin p-2 lg:border-b-0 lg:border-r lg:border-admin">
            {entries.map((entry) => {
              const active = selected?.id === entry.id
              return (
                <button
                  key={entry.id}
                  type="button"
                  aria-pressed={active}
                  onClick={() => setSelectedId(entry.id)}
                  className={cn(
                    'flex w-full flex-col items-start gap-0.5 rounded-[10px] border border-transparent px-2.5 py-2 text-left transition hover:bg-admin-surface-muted admin-focus-ring',
                    active && 'admin-active-inset border-admin bg-admin-surface shadow-sm',
                  )}
                >
                  <span className="w-full truncate text-sm font-semibold text-admin">{entry.displayName}</span>
                  <span className="w-full truncate font-mono text-xs text-admin-muted">{entry.modelSpec}</span>
                </button>
              )
            })}
          </div>
          {selected ? (
            <div className="px-5 py-4">
              <h2 className="text-sm font-medium text-admin">{selected.displayName}</h2>
              <dl className="mt-4 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-4 gap-y-3 text-sm">
                <dt className="text-admin-muted">{t('proxyServices.keySpec')}</dt>
                <dd className="break-all font-mono font-medium text-admin">{selected.modelSpec}</dd>
                <dt className="text-admin-muted">{t('proxyServices.keySecret')}</dt>
                <dd className="font-mono font-medium text-admin">{selected.apiKeyHint || '—'}</dd>
                <dt className="text-admin-muted">{t('proxyServices.keyBaseUrl')}</dt>
                <dd className="break-all font-medium text-admin">{selected.baseUrl || '—'}</dd>
                <dt className="text-admin-muted">{t('proxyServices.keyUse')}</dt>
                <dd className="font-medium text-admin">
                  {selected.sourceAgentId != null ? labelFor(selected.sourceAgentId) : t('proxyServices.keyPlatform')}
                </dd>
              </dl>
              {canUpdate ? (
                <div className="mt-6 flex justify-end">
                  <button
                    type="button"
                    className={adminDangerGhostButtonClass}
                    onClick={() => setDeleting({ id: selected.id, displayName: selected.displayName })}
                  >
                    {t('proxyServices.delete')}
                  </button>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
      ) : null}
      {creating ? (
        <FormDialog
          title={t('proxyServices.keyDialogTitle')}
          description={t('proxyServices.keyDialogDescription')}
          closeLabel={t('proxyServices.close')}
          onClose={() => {
            if (createMutation.isPending) return
            setCreating(false)
            resetDraft()
          }}
        >
          <form
            className="space-y-4"
            onSubmit={(event) => {
              event.preventDefault()
              if (!modelSpec.trim() || !apiKey.trim()) return
              createMutation.mutate()
            }}
          >
            <label className="block text-sm text-admin">
              {t('proxyServices.keyName')}
              <input className={`${adminFieldClass} mt-1`} value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
            </label>
            <label className="block text-sm text-admin">
              {t('proxyServices.keySpec')}
              <input
                className={`${adminFieldClass} mt-1`}
                value={modelSpec}
                placeholder={t('proxyServices.keySpecHint')}
                onChange={(event) => setModelSpec(event.target.value)}
                required
              />
            </label>
            <label className="block text-sm text-admin">
              {t('proxyServices.keyBaseUrl')}
              <input
                className={`${adminFieldClass} mt-1`}
                value={baseUrl}
                placeholder={t('proxyServices.keyBaseUrlHint')}
                onChange={(event) => setBaseUrl(event.target.value)}
              />
            </label>
            <label className="block text-sm text-admin">
              {t('proxyServices.keySecret')}
              <input
                className={`${adminFieldClass} mt-1`}
                type="password"
                autoComplete="new-password"
                value={apiKey}
                onChange={(event) => setApiKey(event.target.value)}
                required
              />
            </label>
            <div className={adminFormActionsClass}>
              <button
                type="button"
                className={adminSecondaryButtonClass}
                onClick={() => {
                  setCreating(false)
                  resetDraft()
                }}
              >
                {t('proxyServices.dialog.cancel')}
              </button>
              <button type="submit" className={adminPrimaryButtonClass} disabled={createMutation.isPending}>
                {t('proxyServices.dialog.save')}
              </button>
            </div>
          </form>
        </FormDialog>
      ) : null}
      {deleting ? (
        <FormDialog
          title={t('proxyServices.keyDeleteConfirmTitle')}
          closeLabel={t('proxyServices.dialog.cancel')}
          onClose={() => setDeleting(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('proxyServices.keyDeleteConfirmBody', { name: deleting.displayName })}
          </p>
          <div className={adminFormActionsClass}>
            <button type="button" className={adminSecondaryButtonClass} onClick={() => setDeleting(null)}>
              {t('proxyServices.dialog.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(deleting.id)}
            >
              {t('proxyServices.delete')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </section>
  )
}
