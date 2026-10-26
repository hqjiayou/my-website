import { useMutation } from '@tanstack/react-query'
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
      <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
        <AiSiteScope>{(siteId) => <SiteAiKnowledgePanel siteId={siteId} />}</AiSiteScope>
      </article>
    </section>
  )
}
