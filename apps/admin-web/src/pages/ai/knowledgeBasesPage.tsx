import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { agentApi } from '@/api'
import { aiApi } from '@/api/apps/aiApi'
import { AdminPageHeader } from '@/components/admin'
import { AgentAiKnowledgePanel } from '@/pages/apps/sites/agentAiKnowledgePanel'
import { PermissionGate } from '@/router/permission'
import type { AiKnowledgeReembedConflict, AiKnowledgeReembedResult } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'
import { isApiError } from '@/utils/http/apiError'

import { AiAgentScope } from './aiAgentScope'

export function AiKnowledgeBasesPage() {
  const { t } = useTranslation('aiAdmin')
  const [result, setResult] = useState<AiKnowledgeReembedResult | null>(null)
  const [conflicts, setConflicts] = useState<AiKnowledgeReembedConflict[] | null>(null)

  const reembedMutation = useMutation({
    mutationFn: () => aiApi.reembedKnowledge(),
    onSuccess: (next) => {
      setResult(next)
      setConflicts(null)
      if (next.status === 'PARTIAL') {
        toast.error(t('knowledgeBases.reembed.partial'))
        return
      }
      toast.success(t('knowledgeBases.reembed.done'))
    },
    onError: (error: Error) => {
      setResult(null)
      setConflicts(isApiError(error) && error.conflicts?.length ? error.conflicts : null)
    },
  })

  function startReembed() {
    if (!window.confirm(t('knowledgeBases.reembed.confirm'))) return
    reembedMutation.mutate()
  }

  return (
    <section className="admin-content-data">
      <AdminPageHeader
        title={t('knowledgeBases.title')}
        description={t('knowledgeBases.description')}
        action={
          <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
            <button
              type="button"
              className="rounded-lg bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
              disabled={reembedMutation.isPending}
              onClick={startReembed}
            >
              {reembedMutation.isPending
                ? t('knowledgeBases.reembed.working')
                : t('knowledgeBases.reembed.action')}
            </button>
          </PermissionGate>
        }
      />
      {conflicts ? (
        <div className="mb-4 rounded-lg border border-warning/35 bg-warning-muted px-4 py-3 text-sm text-warning-emphasis">
          <p>{t('knowledgeBases.reembed.conflicts')}</p>
          <ul className="mt-2 list-disc pl-5">
            {conflicts.map((conflict) => (
              <li key={conflict.agentId}>
                {conflict.agentId}: {conflict.dimensions}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {result ? (
        <div className="mb-4 rounded-lg border border-border bg-card px-4 py-3 text-sm text-foreground">
          <p>{t('knowledgeBases.reembed.usage')}</p>
          <p className="mt-2">
            {t('knowledgeBases.reembed.sites')} {result.sites}
            {' · '}
            {t('knowledgeBases.reembed.documents')} {result.documents}
            {' · '}
            {t('knowledgeBases.reembed.dimensions')} {result.dimensions ?? '—'}
            {' · '}
            {result.recreatedSchema
              ? t('knowledgeBases.reembed.recreated')
              : t('knowledgeBases.reembed.notRecreated')}
          </p>
          {result.failed.length > 0 ? (
            <div className="mt-2">
              <p>{t('knowledgeBases.reembed.failed')}</p>
              <ul className="mt-1 list-disc pl-5">
                {result.failed.map((failure) => (
                  <li key={`${failure.agentId}-${failure.sourceId}`}>
                    {failure.agentId} / {failure.sourceId}: {failure.error}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>
      ) : null}
      <KnowledgeOverviewSection />
      <KnowledgeBasesAdminSection />
      <article className="rounded-xl border border-border bg-card p-5">
        <AiAgentScope>{(agentId) => <AgentAiKnowledgePanel agentId={agentId} />}</AiAgentScope>
      </article>
    </section>
  )
}

function KnowledgeBasesAdminSection() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const [linkTargets, setLinkTargets] = useState<Record<number, string>>({})
  const basesQuery = useQuery({
    queryKey: ['ai-knowledge-bases-all'],
    queryFn: () => aiApi.listAllKnowledgeBases(),
  })
  const sitesQuery = useQuery({
    queryKey: ['sites', { pageNum: 1, pageSize: 100 }],
    queryFn: () => agentApi.listSites({ pageNum: 1, pageSize: 100 }),
  })
  const sites = sitesQuery.data?.records ?? []
  const agentName = (agentId: number) =>
    sites.find((site) => site.id === agentId)?.agentName ?? `#${agentId}`

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['ai-knowledge-bases-all'] })
    void queryClient.invalidateQueries({ queryKey: ['ai-knowledge-overview'] })
  }
  const linkMutation = useMutation({
    mutationFn: (input: { baseId: number; targetAgentId: number }) =>
      aiApi.linkKnowledgeBase(input.baseId, input.targetAgentId),
    onSuccess: () => {
      toast.success(t('knowledgeBases.bases.linked'))
      invalidate()
    },
    onError: (error: Error) => toast.error(error.message),
  })
  const unlinkMutation = useMutation({
    mutationFn: (input: { baseId: number; targetAgentId: number }) =>
      aiApi.unlinkKnowledgeBase(input.baseId, input.targetAgentId),
    onSuccess: () => {
      toast.success(t('knowledgeBases.bases.unlinked'))
      invalidate()
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const bases = basesQuery.data ?? []
  if (bases.length === 0) {
    return null
  }
  return (
    <article className="mb-4 rounded-xl border border-border bg-card p-5">
      <h2 className="text-sm font-semibold text-foreground">{t('knowledgeBases.bases.title')}</h2>
      <p className="mt-1 text-xs text-muted-foreground">{t('knowledgeBases.bases.hint')}</p>
      <div className="mt-3 space-y-3">
        {bases.map((base) => {
          const candidates = sites.filter(
            (site) => site.id !== base.agentId && !base.linkedAgentIds.includes(site.id),
          )
          const chosen = linkTargets[base.id] ?? (candidates[0] ? String(candidates[0].id) : '')
          return (
            <div key={base.id} className="rounded-lg border border-border px-3 py-2">
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <span className="font-medium text-foreground">{base.name}</span>
                {base.isDefault ? (
                  <span className="rounded bg-card-muted px-1.5 py-0.5 text-xs text-muted-foreground">
                    {t('knowledgeBases.bases.defaultBadge')}
                  </span>
                ) : null}
                <span className="text-xs text-muted-foreground">
                  {t('knowledgeBases.bases.owner')} {base.ownerAgentName ?? `#${base.agentId}`}
                  {' · '}
                  {t('knowledgeBases.bases.documents')} {base.documentCount}
                </span>
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-2">
                <span className="text-xs text-muted-foreground">{t('knowledgeBases.bases.sharedWith')}</span>
                {base.linkedAgentIds.length === 0 ? (
                  <span className="text-xs text-muted-foreground">{t('knowledgeBases.bases.none')}</span>
                ) : (
                  base.linkedAgentIds.map((linkedAgentId) => (
                    <span
                      key={linkedAgentId}
                      className="flex items-center gap-1 rounded bg-info-muted px-2 py-0.5 text-xs text-primary"
                    >
                      {agentName(linkedAgentId)}
                      <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
                        <button
                          type="button"
                          className="text-primary"
                          title={t('knowledgeBases.bases.unlink')}
                          onClick={() =>
                            unlinkMutation.mutate({ baseId: base.id, targetAgentId: linkedAgentId })
                          }
                        >
                          ×
                        </button>
                      </PermissionGate>
                    </span>
                  ))
                )}
                {candidates.length > 0 ? (
                  <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
                    <span className="flex items-center gap-1">
                      <select
                        className="rounded-lg border border-border px-2 py-1 text-xs"
                        value={chosen}
                        onChange={(event) =>
                          setLinkTargets((prev) => ({ ...prev, [base.id]: event.target.value }))
                        }
                      >
                        {candidates.map((site) => (
                          <option key={site.id} value={site.id}>
                            {site.agentName}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        className="rounded-lg border border-border px-2 py-1 text-xs disabled:opacity-50"
                        disabled={linkMutation.isPending || !chosen}
                        onClick={() =>
                          linkMutation.mutate({ baseId: base.id, targetAgentId: Number(chosen) })
                        }
                      >
                        {t('knowledgeBases.bases.link')}
                      </button>
                    </span>
                  </PermissionGate>
                ) : null}
              </div>
            </div>
          )
        })}
      </div>
    </article>
  )
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function KnowledgeOverviewSection() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const overviewQuery = useQuery({
    queryKey: ['ai-knowledge-overview'],
    queryFn: () => aiApi.getKnowledgeOverview(),
  })
  const retryMutation = useMutation({
    mutationFn: () => aiApi.reindexFailedKnowledge(),
    onSuccess: (res) => {
      toast.success(
        `${t('knowledgeBases.overview.retryDone')} ${res.succeeded}/${res.attempted}`,
      )
      void queryClient.invalidateQueries({ queryKey: ['ai-knowledge-overview'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const overview = overviewQuery.data
  if (!overview) {
    return null
  }
  const stats = [
    { label: t('knowledgeBases.overview.total'), value: String(overview.totalDocuments) },
    { label: t('knowledgeBases.overview.indexed'), value: String(overview.indexedDocuments) },
    { label: t('knowledgeBases.overview.failed'), value: String(overview.failedDocuments) },
    { label: t('knowledgeBases.overview.pending'), value: String(overview.pendingDocuments) },
    { label: t('knowledgeBases.overview.storage'), value: formatBytes(overview.originalBytes) },
  ]
  return (
    <article className="mb-4 rounded-xl border border-border bg-card p-5">
      <h2 className="text-sm font-semibold text-foreground">{t('knowledgeBases.overview.title')}</h2>
      <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-5">
        {stats.map((stat) => (
          <div key={stat.label} className="rounded-lg bg-card-muted px-3 py-2">
            <div className="text-xs text-muted-foreground">{stat.label}</div>
            <div className="mt-0.5 text-lg font-semibold text-foreground">{stat.value}</div>
          </div>
        ))}
      </div>
      {overview.sites.length > 0 ? (
        <table className="mt-4 w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-muted-foreground">
              <th className="py-1 pr-3 font-medium">{t('knowledgeBases.overview.site')}</th>
              <th className="py-1 pr-3 font-medium">{t('knowledgeBases.overview.total')}</th>
              <th className="py-1 pr-3 font-medium">{t('knowledgeBases.overview.indexed')}</th>
              <th className="py-1 pr-3 font-medium">{t('knowledgeBases.overview.failed')}</th>
              <th className="py-1 font-medium">{t('knowledgeBases.overview.storage')}</th>
            </tr>
          </thead>
          <tbody>
            {overview.sites.map((site) => (
              <tr key={site.agentId} className="border-t border-border">
                <td className="py-1.5 pr-3">{site.agentName ?? `#${site.agentId}`}</td>
                <td className="py-1.5 pr-3">{site.totalDocuments}</td>
                <td className="py-1.5 pr-3">{site.indexedDocuments}</td>
                <td className="py-1.5 pr-3">{site.failedDocuments}</td>
                <td className="py-1.5">{formatBytes(site.originalBytes)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
      {overview.recentFailures.length > 0 ? (
        <div className="mt-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-medium text-danger-emphasis">
              {t('knowledgeBases.overview.recentFailures')}
            </h3>
            <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
              <button
                type="button"
                className="rounded-lg border border-border px-3 py-1 text-xs font-medium disabled:opacity-50"
                disabled={retryMutation.isPending}
                onClick={() => {
                  if (window.confirm(t('knowledgeBases.overview.retryConfirm'))) {
                    retryMutation.mutate()
                  }
                }}
              >
                {retryMutation.isPending
                  ? t('knowledgeBases.overview.retrying')
                  : t('knowledgeBases.overview.retryAll')}
              </button>
            </PermissionGate>
          </div>
          <ul className="mt-2 space-y-1 text-sm text-foreground">
            {overview.recentFailures.map((failure) => (
              <li key={failure.documentId}>
                <span className="font-medium">{failure.title}</span>
                <span className="text-muted-foreground">
                  {' · '}
                  {failure.agentName ?? `#${failure.agentId}`}
                </span>
                {failure.indexError ? (
                  <span className="block truncate text-xs text-danger-emphasis" title={failure.indexError}>
                    {failure.indexError}
                  </span>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </article>
  )
}
