import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { PageHeader } from '@/components/page'
import { SiteAiKnowledgePanel } from '@/pages/apps/sites/siteAiKnowledgePanel'
import { PermissionGate } from '@/router/permission'
import type { AiKnowledgeReembedConflict, AiKnowledgeReembedResult } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'
import { isApiError } from '@/utils/http/apiError'

import { AiSiteScope } from './aiSiteScope'

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
    <section>
      <PageHeader
        title={t('knowledgeBases.title')}
        description={t('knowledgeBases.description')}
        action={
          <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
            <button
              type="button"
              className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
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
        <div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
          <p>{t('knowledgeBases.reembed.conflicts')}</p>
          <ul className="mt-2 list-disc pl-5">
            {conflicts.map((conflict) => (
              <li key={conflict.siteId}>
                {conflict.siteId}: {conflict.dimensions}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {result ? (
        <div className="mb-4 rounded-lg border border-slate-200 bg-white px-4 py-3 text-sm text-slate-700">
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
                  <li key={`${failure.siteId}-${failure.sourceId}`}>
                    {failure.siteId} / {failure.sourceId}: {failure.error}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>
      ) : null}
      <KnowledgeOverviewSection />
      <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <AiSiteScope>{(siteId) => <SiteAiKnowledgePanel siteId={siteId} />}</AiSiteScope>
      </article>
    </section>
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
    <article className="mb-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h2 className="text-sm font-semibold text-slate-900">{t('knowledgeBases.overview.title')}</h2>
      <div className="mt-3 grid grid-cols-2 gap-3 sm:grid-cols-5">
        {stats.map((stat) => (
          <div key={stat.label} className="rounded-lg bg-slate-50 px-3 py-2">
            <div className="text-xs text-slate-500">{stat.label}</div>
            <div className="mt-0.5 text-lg font-semibold text-slate-900">{stat.value}</div>
          </div>
        ))}
      </div>
      {overview.sites.length > 0 ? (
        <table className="mt-4 w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-slate-500">
              <th className="py-1 pr-3 font-medium">{t('knowledgeBases.overview.site')}</th>
              <th className="py-1 pr-3 font-medium">{t('knowledgeBases.overview.total')}</th>
              <th className="py-1 pr-3 font-medium">{t('knowledgeBases.overview.indexed')}</th>
              <th className="py-1 pr-3 font-medium">{t('knowledgeBases.overview.failed')}</th>
              <th className="py-1 font-medium">{t('knowledgeBases.overview.storage')}</th>
            </tr>
          </thead>
          <tbody>
            {overview.sites.map((site) => (
              <tr key={site.siteId} className="border-t border-slate-100">
                <td className="py-1.5 pr-3">{site.siteName ?? `#${site.siteId}`}</td>
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
            <h3 className="text-sm font-medium text-red-700">
              {t('knowledgeBases.overview.recentFailures')}
            </h3>
            <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
              <button
                type="button"
                className="rounded-lg border border-slate-300 px-3 py-1 text-xs font-medium disabled:opacity-50"
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
          <ul className="mt-2 space-y-1 text-sm text-slate-700">
            {overview.recentFailures.map((failure) => (
              <li key={failure.documentId}>
                <span className="font-medium">{failure.title}</span>
                <span className="text-slate-500">
                  {' · '}
                  {failure.siteName ?? `#${failure.siteId}`}
                </span>
                {failure.indexError ? (
                  <span className="block truncate text-xs text-red-600" title={failure.indexError}>
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
