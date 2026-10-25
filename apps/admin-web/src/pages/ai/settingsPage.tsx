import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { siteApi } from '@/api/apps/siteApi'
import { PageHeader } from '@/components/page'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type { AiModelEntry, AiModelEntryWriteInput } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

type Draft = {
  id: number | null
  displayName: string
  modelSpec: string
  apiKey: string
  baseUrl: string
  siteId: string
  embeddingSpec: string
  enabled: boolean
  defaultEntry: boolean
  version: number | null
  apiKeyHint: string | null
}

function emptyDraft(makeDefault: boolean): Draft {
  return {
    id: null,
    displayName: '',
    modelSpec: '',
    apiKey: '',
    baseUrl: '',
    siteId: '',
    embeddingSpec: '',
    enabled: true,
    defaultEntry: makeDefault,
    version: null,
    apiKeyHint: null,
  }
}

function draftFromEntry(entry: AiModelEntry): Draft {
  return {
    id: entry.id,
    displayName: entry.displayName,
    modelSpec: entry.modelSpec,
    apiKey: '',
    baseUrl: entry.baseUrl ?? '',
    siteId: entry.siteId == null ? '' : String(entry.siteId),
    embeddingSpec: entry.embeddingSpec ?? '',
    enabled: entry.enabled,
    defaultEntry: entry.defaultEntry,
    version: entry.version,
    apiKeyHint: entry.apiKeyHint,
  }
}

export function AiSettingsPage() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiModelUpdate)
  const [draft, setDraft] = useState<Draft | null>(null)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
  })
  const sitesQuery = useQuery({
    queryKey: ['sites', 'model-entry-options'],
    queryFn: () => siteApi.listSites({ pageNum: 1, pageSize: 100 }),
  })

  const saveMutation = useMutation({
    mutationFn: (input: { id: number | null; body: AiModelEntryWriteInput }) =>
      input.id == null ? aiApi.createModelEntry(input.body) : aiApi.updateModelEntry(input.id, input.body),
    onSuccess: (entry) => {
      toast.success(t('settings.saved'))
      setDraft(draftFromEntry(entry))
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (saveError: Error) => {
      toast.error(saveError.message)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => aiApi.deleteModelEntry(id),
    onSuccess: () => {
      toast.success(t('settings.deleted'))
      setDraft(null)
      void queryClient.invalidateQueries({ queryKey: ['ai', 'model-entries'] })
    },
    onError: (deleteError: Error) => {
      toast.error(deleteError.message)
    },
  })

  if (isPending) return <p className="text-sm text-slate-500">{t('loading')}</p>
  if (isError) {
    return (
      <section>
        <PageHeader title={t('settings.title')} description={t('settings.description')} />
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          <p>{error instanceof Error ? error.message : t('error')}</p>
          <button type="button" className="mt-2 underline" onClick={() => void refetch()}>
            {t('retry')}
          </button>
        </div>
      </section>
    )
  }

  const entries = data ?? []

  function submitDraft() {
    if (!draft || !canUpdate) return
    const body: AiModelEntryWriteInput = {
      displayName: draft.displayName.trim(),
      modelSpec: draft.modelSpec.trim(),
      baseUrl: draft.baseUrl.trim() || null,
      siteId: draft.siteId.trim() === '' ? null : Number(draft.siteId),
      embeddingSpec: draft.embeddingSpec.trim() || null,
      enabled: draft.enabled,
      defaultEntry: draft.defaultEntry,
      version: draft.version,
    }
    if (draft.apiKey.trim()) body.apiKey = draft.apiKey.trim()
    saveMutation.mutate({ id: draft.id, body })
  }

  return (
    <section>
      <PageHeader title={t('settings.title')} description={t('settings.description')} />
      <div className="space-y-4">
        <p className="text-sm text-slate-600">{t('settings.fallbackHint')}</p>
        <article className="space-y-3 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          {entries.length === 0 ? <p className="text-sm text-slate-600">{t('settings.empty')}</p> : null}
          <ul className="space-y-2">
            {entries.map((entry) => (
              <li key={entry.id}>
                <button
                  type="button"
                  className="flex w-full items-center justify-between rounded-lg border border-slate-200 px-3 py-2 text-left text-sm hover:bg-slate-50"
                  onClick={() => setDraft(draftFromEntry(entry))}
                >
                  <span>
                    <span className="font-medium text-slate-900">{entry.displayName}</span>
                    <span className="ml-2 text-slate-500">{entry.modelSpec}</span>
                  </span>
                  <span className="text-xs text-slate-500">
                    {entry.defaultEntry ? t('settings.defaultBadge') : null}
                    {!entry.enabled ? ` ${t('settings.disabledBadge')}` : null}
                    {entry.apiKeyConfigured ? ` ${entry.apiKeyHint}` : null}
                  </span>
                </button>
              </li>
            ))}
          </ul>
          <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
            <button
              type="button"
              className="rounded-lg border border-slate-300 px-3 py-2 text-sm"
              onClick={() => setDraft(emptyDraft(entries.length === 0))}
            >
              {t('settings.add')}
            </button>
          </PermissionGate>
        </article>

        {draft ? (
          <form
            className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
            onSubmit={(event) => {
              event.preventDefault()
              submitDraft()
            }}
          >
            <label className="block">
              <span className="text-sm font-medium text-slate-800">{t('settings.name')}</span>
              <input
                className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                value={draft.displayName}
                disabled={!canUpdate}
                onChange={(event) => setDraft({ ...draft, displayName: event.target.value })}
              />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-800">{t('settings.spec')}</span>
              <input
                className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                value={draft.modelSpec}
                disabled={!canUpdate}
                placeholder={t('settings.specHint')}
                onChange={(event) => setDraft({ ...draft, modelSpec: event.target.value })}
              />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-800">{t('settings.apiKey')}</span>
              <input
                className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                type="password"
                autoComplete="new-password"
                value={draft.apiKey}
                disabled={!canUpdate}
                placeholder={draft.id == null ? '' : t('settings.apiKeyKeep')}
                onChange={(event) => setDraft({ ...draft, apiKey: event.target.value })}
              />
              {draft.apiKeyHint ? (
                <span className="mt-1 block text-xs text-slate-500">
                  {t('settings.apiKeyHint', { hint: draft.apiKeyHint })}
                </span>
              ) : null}
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-800">{t('settings.site')}</span>
              <select
                className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                value={draft.siteId}
                disabled={!canUpdate}
                onChange={(event) => setDraft({ ...draft, siteId: event.target.value })}
              >
                <option value="">{t('settings.sitePlatform')}</option>
                {(sitesQuery.data?.records ?? []).map((site) => (
                  <option key={site.id} value={site.id}>
                    {site.siteName}
                  </option>
                ))}
              </select>
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-800">{t('settings.embeddingSpec')}</span>
              <input
                className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                value={draft.embeddingSpec}
                disabled={!canUpdate}
                placeholder={t('settings.embeddingSpecHint')}
                onChange={(event) => setDraft({ ...draft, embeddingSpec: event.target.value })}
              />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-slate-800">{t('settings.baseUrl')}</span>
              <input
                className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                value={draft.baseUrl}
                disabled={!canUpdate}
                placeholder={t('settings.baseUrlHint')}
                onChange={(event) => setDraft({ ...draft, baseUrl: event.target.value })}
              />
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-800">
              <input
                type="checkbox"
                checked={draft.enabled}
                disabled={!canUpdate}
                onChange={(event) => setDraft({ ...draft, enabled: event.target.checked })}
              />
              {t('settings.enabled')}
            </label>
            <label className="flex items-center gap-2 text-sm text-slate-800">
              <input
                type="checkbox"
                checked={draft.defaultEntry}
                disabled={!canUpdate}
                onChange={(event) => setDraft({ ...draft, defaultEntry: event.target.checked })}
              />
              {t('settings.defaultEntry')}
            </label>
            <PermissionGate code={PERMISSION_CODES.aiModelUpdate}>
              <div className="flex gap-2">
                <button
                  type="submit"
                  disabled={saveMutation.isPending}
                  className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
                >
                  {saveMutation.isPending ? t('settings.saving') : t('settings.save')}
                </button>
                {draft.id != null ? (
                  <button
                    type="button"
                    className="rounded-lg border border-rose-200 px-3 py-2 text-sm text-rose-700"
                    disabled={deleteMutation.isPending}
                    onClick={() => {
                      if (draft.id == null) return
                      if (!window.confirm(t('settings.confirmDelete'))) return
                      deleteMutation.mutate(draft.id)
                    }}
                  >
                    {t('settings.remove')}
                  </button>
                ) : null}
              </div>
            </PermissionGate>
          </form>
        ) : null}
      </div>
    </section>
  )
}
