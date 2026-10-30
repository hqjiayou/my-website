import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, FileText, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { agentApi } from '@/api'
import { aiApi } from '@/api/apps/aiApi'
import { AdminPageHeader } from '@/components/admin'
import { adminPrimaryButtonClass, adminSecondaryButtonClass } from '@/components/admin/controls'
import { cn } from '@/lib/cn'
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
              className={adminPrimaryButtonClass}
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
    <article className="mb-4 rounded-xl border border-admin bg-admin-surface p-5">
      <h2 className="text-sm font-semibold text-admin">{t('knowledgeBases.bases.title')}</h2>
      <p className="mt-1 text-xs text-admin-muted">{t('knowledgeBases.bases.hint')}</p>
      <div className="mt-3 space-y-3">
        {bases.map((base) => {
          const candidates = sites.filter(
            (site) => site.id !== base.agentId && !base.linkedAgentIds.includes(site.id),
          )
          const chosen = linkTargets[base.id] ?? (candidates[0] ? String(candidates[0].id) : '')
          return (
            <div key={base.id} className="flex items-start gap-3 rounded-lg border border-admin px-3 py-2.5">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-info-muted text-info-emphasis">
                <BookOpen className="h-4 w-4" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  <span className="font-semibold text-admin">{base.name}</span>
                  {base.isDefault ? (
                    <span className="rounded-full bg-admin-surface-muted px-1.5 py-0.5 text-xs text-admin-muted">
                      {t('knowledgeBases.bases.defaultBadge')}
                    </span>
                  ) : null}
                  <span className="text-xs text-admin-muted">
                    {t('knowledgeBases.bases.owner')} {base.ownerAgentName ?? `#${base.agentId}`}
                    {' · '}
                    {t('knowledgeBases.bases.documents')} {base.documentCount}
                  </span>
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <span className="text-xs text-admin-muted">{t('knowledgeBases.bases.sharedWith')}</span>
                  {base.linkedAgentIds.length === 0 ? (
                    <span className="text-xs text-admin-muted">{t('knowledgeBases.bases.none')}</span>
                  ) : (
                    base.linkedAgentIds.map((linkedAgentId) => (
                      <span
                        key={linkedAgentId}
                        className="flex items-center gap-1 rounded-full bg-info-muted py-0.5 pl-2 pr-1 text-xs text-info-emphasis"
                      >
                        {agentName(linkedAgentId)}
                        <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
                          <button
                            type="button"
                            className="rounded-full p-0.5 text-danger-emphasis transition hover:bg-danger-muted"
                            title={t('knowledgeBases.bases.unlink')}
                            onClick={() =>
                              unlinkMutation.mutate({ baseId: base.id, targetAgentId: linkedAgentId })
                            }
                          >
                            <X className="h-3 w-3" />
                          </button>
                        </PermissionGate>
                      </span>
                    ))
                  )}
                  {candidates.length > 0 ? (
                    <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
                      <span className="flex items-center gap-1">
                        <select
                          className="rounded-lg border border-admin bg-admin-surface px-2 py-1 text-xs text-admin"
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
                          className="rounded-lg border border-admin bg-admin-surface px-2 py-1 text-xs text-admin disabled:opacity-50"
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
  // 设计稿的「知识库数（按智能体）」格缺对应 i18n 文案，按红线不编造文案，省略该格（见报告）。
  const stats: { key: string; label: string; value: number; valueClassName: string }[] = [
    {
      key: 'total',
      label: t('knowledgeBases.overview.total'),
      value: overview.totalDocuments,
      valueClassName: 'text-admin',
    },
    {
      key: 'indexed',
      label: t('knowledgeBases.overview.indexed'),
      value: overview.indexedDocuments,
      valueClassName: 'text-success-emphasis',
    },
    {
      key: 'failed',
      label: t('knowledgeBases.overview.failed'),
      value: overview.failedDocuments,
      valueClassName: overview.failedDocuments > 0 ? 'text-danger-emphasis' : 'text-admin',
    },
    {
      key: 'stale',
      label: t('knowledgeBases.overview.stale'),
      value: overview.staleDocuments,
      valueClassName: overview.staleDocuments > 0 ? 'text-warning-emphasis' : 'text-admin',
    },
  ]
  return (
    <>
      <div className="mb-3 grid grid-cols-2 gap-3 sm:grid-cols-4">
        {stats.map((stat) => (
          <div key={stat.key} className="rounded-xl border border-admin bg-admin-surface p-4">
            <div className={cn('text-[23px] font-bold leading-none tabular-nums', stat.valueClassName)}>
              {stat.value}
            </div>
            <div className="mt-1 text-xs text-admin-muted">{stat.label}</div>
          </div>
        ))}
      </div>
      <article className="mb-3 overflow-hidden rounded-xl border border-admin bg-admin-surface">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-admin px-4 py-3">
          <h2 className="text-sm font-semibold text-admin">{t('knowledgeBases.overview.title')}</h2>
          <span className="text-xs text-admin-muted">
            {t('knowledgeBases.overview.storage')} {formatBytes(overview.originalBytes)}
          </span>
        </div>
        {overview.sites.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="bg-admin-surface-muted text-[11.5px] font-medium text-admin-muted">
                  <th className="px-4 py-3">{t('knowledgeBases.overview.site')}</th>
                  <th className="px-4 py-3">{t('knowledgeBases.overview.total')}</th>
                  <th className="px-4 py-3">{t('knowledgeBases.overview.indexed')}</th>
                  <th className="px-4 py-3">{t('knowledgeBases.overview.failed')}</th>
                  <th className="px-4 py-3">{t('knowledgeBases.overview.storage')}</th>
                </tr>
              </thead>
              <tbody>
                {overview.sites.map((site) => (
                  <tr key={site.agentId} className="border-t border-admin hover:bg-admin-surface-muted">
                    <td className="px-4 py-3 font-semibold text-admin">{site.agentName ?? `#${site.agentId}`}</td>
                    <td className="px-4 py-3 tabular-nums text-admin">{site.totalDocuments}</td>
                    <td className="px-4 py-3 tabular-nums font-semibold text-success-emphasis">
                      {site.indexedDocuments}
                    </td>
                    <td
                      className={cn(
                        'px-4 py-3 tabular-nums font-semibold',
                        site.failedDocuments > 0 ? 'text-danger-emphasis' : 'text-admin',
                      )}
                    >
                      {site.failedDocuments}
                    </td>
                    <td className="px-4 py-3 tabular-nums text-admin">{formatBytes(site.originalBytes)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </article>
      {overview.recentFailures.length > 0 ? (
        <article className="overflow-hidden rounded-xl border border-admin bg-admin-surface">
          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-admin px-4 py-3">
            <h3 className="flex items-center gap-2 text-sm font-semibold text-danger-emphasis">
              <span className="h-2 w-2 rounded-full bg-danger" />
              {t('knowledgeBases.overview.recentFailures')}
            </h3>
            <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
              <button
                type="button"
                className={adminSecondaryButtonClass}
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
          <div>
            {overview.recentFailures.map((failure) => (
              <div
                key={failure.documentId}
                className="flex items-center gap-3 border-b border-admin px-4 py-3 last:border-b-0"
              >
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-danger-muted text-danger-emphasis">
                  <FileText className="h-4 w-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-semibold text-admin">{failure.title}</div>
                  {failure.indexError ? (
                    <div className="truncate text-xs text-danger-emphasis" title={failure.indexError}>
                      {failure.indexError}
                    </div>
                  ) : null}
                </div>
                <span className="shrink-0 rounded-full bg-admin-surface-muted px-2 py-0.5 text-xs text-admin-muted">
                  {failure.agentName ?? `#${failure.agentId}`}
                </span>
              </div>
            ))}
          </div>
        </article>
      ) : null}
    </>
  )
}
