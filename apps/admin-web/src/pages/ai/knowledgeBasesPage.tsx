import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, ChevronDown, ChevronRight, FileText, X } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { agentApi } from '@/api'
import { aiApi } from '@/api/apps/aiApi'
import { AdminPageHeader, FormDialog } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminFormActionsClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { AgentAiKnowledgePanel } from '@/pages/agents/agentAiKnowledgePanel'
import { PermissionGate } from '@/router/permission'
import type { AiKnowledgeReembedConflict, AiKnowledgeReembedResult } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'
import { isApiError } from '@/utils/http/apiError'

import { AiAgentScope } from './aiAgentScope'
import { EmbeddingRegistrationCard } from './embeddingRegistrationCard'

export function AiKnowledgeBasesPage() {
  const { t } = useTranslation('aiAdmin')
  const [result, setResult] = useState<AiKnowledgeReembedResult | null>(null)
  const [conflicts, setConflicts] = useState<AiKnowledgeReembedConflict[] | null>(null)
  const [confirmingReembed, setConfirmingReembed] = useState(false)

  const reembedMutation = useMutation({
    mutationFn: () => aiApi.reembedKnowledge(),
    onSuccess: (next) => {
      setConfirmingReembed(false)
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
              onClick={() => setConfirmingReembed(true)}
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
      {confirmingReembed ? (
        <FormDialog
          title={t('knowledgeBases.reembed.confirmTitle')}
          closeLabel={t('knowledgeBases.cancel')}
          onClose={() => setConfirmingReembed(false)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">{t('knowledgeBases.reembed.confirm')}</p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setConfirmingReembed(false)}
            >
              {t('knowledgeBases.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={reembedMutation.isPending}
              onClick={() => reembedMutation.mutate()}
            >
              {t('knowledgeBases.reembed.action')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </section>
  )
}

function KnowledgeBasesAdminSection() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const [linkTargets, setLinkTargets] = useState<Record<number, string>>({})
  const [unlinkTarget, setUnlinkTarget] = useState<{
    baseId: number
    baseName: string
    targetAgentId: number
    targetName: string
  } | null>(null)
  const [embeddingBaseId, setEmbeddingBaseId] = useState<number | null>(null)
  const [expandedBaseId, setExpandedBaseId] = useState<number | null>(null)
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
      setUnlinkTarget(null)
      toast.success(t('knowledgeBases.bases.unlinked'))
      invalidate()
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const bases = basesQuery.data ?? []
  if (bases.length === 0) {
    return null
  }
  const selectedBase = bases.find((base) => base.id === embeddingBaseId) ?? bases[0]
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
                    {' · '}
                    {t('knowledgeBases.bases.sharedWith')} {base.linkedAgentIds.length}
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
                            className="rounded-full p-0.5 text-danger-emphasis transition hover:bg-danger-muted disabled:opacity-50"
                            title={t('knowledgeBases.bases.unlink')}
                            disabled={unlinkMutation.isPending}
                            onClick={() =>
                              setUnlinkTarget({
                                baseId: base.id,
                                baseName: base.name,
                                targetAgentId: linkedAgentId,
                                targetName: agentName(linkedAgentId),
                              })
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
                <div className="mt-2">
                  <button
                    type="button"
                    className="flex items-center gap-1 text-xs text-admin-muted transition hover:text-admin"
                    onClick={() => setExpandedBaseId(expandedBaseId === base.id ? null : base.id)}
                  >
                    {expandedBaseId === base.id ? (
                      <ChevronDown className="h-3.5 w-3.5" />
                    ) : (
                      <ChevronRight className="h-3.5 w-3.5" />
                    )}
                    {t('knowledgeBases.bases.documents')} {base.documentCount}
                  </button>
                  {expandedBaseId === base.id ? (
                    <BaseDocumentsList baseId={base.id} ownerAgentId={base.agentId} />
                  ) : null}
                </div>
              </div>
            </div>
          )
        })}
      </div>
      <div className="mt-5 border-t border-admin pt-4">
        <h3 className="text-sm font-semibold text-admin">{t('knowledgeBases.bases.embeddingTitle')}</h3>
        <p className="mt-1 text-xs text-admin-muted">{t('knowledgeBases.bases.embeddingHint')}</p>
        <select
          className="mt-3 w-72 max-w-full rounded-lg border border-admin bg-admin-surface px-2 py-1.5 text-sm text-admin"
          value={selectedBase.id}
          onChange={(event) => setEmbeddingBaseId(Number(event.target.value))}
          aria-label={t('knowledgeBases.bases.embeddingPick')}
        >
          {bases.map((base) => (
            <option key={base.id} value={base.id}>
              {base.name} · {t('knowledgeBases.bases.owner')} {base.ownerAgentName ?? `#${base.agentId}`}
            </option>
          ))}
        </select>
        <div className="mt-3">
          <EmbeddingRegistrationCard key={selectedBase.id} baseId={selectedBase.id} />
        </div>
      </div>
      {unlinkTarget ? (
        <FormDialog
          title={t('knowledgeBases.bases.unlinkConfirmTitle')}
          closeLabel={t('knowledgeBases.cancel')}
          onClose={() => setUnlinkTarget(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('knowledgeBases.bases.unlinkConfirm', {
              name: unlinkTarget.targetName,
              base: unlinkTarget.baseName,
            })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setUnlinkTarget(null)}
            >
              {t('knowledgeBases.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={unlinkMutation.isPending}
              onClick={() =>
                unlinkMutation.mutate({
                  baseId: unlinkTarget.baseId,
                  targetAgentId: unlinkTarget.targetAgentId,
                })
              }
            >
              {t('knowledgeBases.bases.unlink')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </article>
  )
}

/**
 * 按空间只读文档列表（Issue #209 UoW-5 管理面空间视角）：以空间创建者为 viewer
 * 复用既有文档列表接口，只展示标题与索引状态，不提供任何写操作——文档写入仍限
 * 创建者在自己的工作区维护，订阅者只读消费，此处与之同一口径。
 */
function BaseDocumentsList({ baseId, ownerAgentId }: { baseId: number; ownerAgentId: number }) {
  const { t } = useTranslation('aiAdmin')
  const documentsQuery = useQuery({
    queryKey: ['ai-knowledge-base-documents', baseId],
    queryFn: () => aiApi.listKnowledgeDocuments(ownerAgentId, { baseId, pageNum: 1, pageSize: 20 }),
  })
  const items = documentsQuery.data?.records ?? []
  if (documentsQuery.isLoading) {
    return null
  }
  if (items.length === 0) {
    return <p className="mt-2 text-xs text-admin-muted">{t('knowledgeBases.bases.none')}</p>
  }
  return (
    <ul className="mt-2 space-y-1">
      {items.map((item) => (
        <li
          key={item.id}
          className="flex items-center gap-2 rounded-lg border border-admin px-3 py-2"
        >
          <FileText className="h-3.5 w-3.5 shrink-0 text-admin-muted" />
          <span className="min-w-0 flex-1 truncate text-xs text-admin">{item.title}</span>
          <span
            className={cn(
              'shrink-0 rounded-full px-2 py-0.5 text-xs',
              item.indexStatus === 'INDEXED'
                ? 'bg-success-muted text-success-emphasis'
                : item.indexStatus === 'FAILED'
                  ? 'bg-danger-muted text-danger-emphasis'
                  : 'bg-admin-surface-muted text-admin-muted',
            )}
            title={item.indexStatus === 'FAILED' ? (item.indexError ?? undefined) : undefined}
          >
            {item.indexStatus === 'INDEXED'
              ? t('knowledgeBases.overview.indexed')
              : item.indexStatus === 'FAILED'
                ? t('knowledgeBases.overview.failed')
                : t('knowledgeBases.overview.pending')}
          </span>
        </li>
      ))}
    </ul>
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
  const [confirmingRetry, setConfirmingRetry] = useState(false)
  const overviewQuery = useQuery({
    queryKey: ['ai-knowledge-overview'],
    queryFn: () => aiApi.getKnowledgeOverview(),
  })
  const retryMutation = useMutation({
    mutationFn: () => aiApi.reindexFailedKnowledge(),
    onSuccess: (res) => {
      setConfirmingRetry(false)
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
                onClick={() => setConfirmingRetry(true)}
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
      {confirmingRetry ? (
        <FormDialog
          title={t('knowledgeBases.overview.retryConfirmTitle')}
          closeLabel={t('knowledgeBases.cancel')}
          onClose={() => setConfirmingRetry(false)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">{t('knowledgeBases.overview.retryConfirm')}</p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setConfirmingRetry(false)}
            >
              {t('knowledgeBases.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={retryMutation.isPending}
              onClick={() => retryMutation.mutate()}
            >
              {t('knowledgeBases.overview.retryAll')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </>
  )
}
