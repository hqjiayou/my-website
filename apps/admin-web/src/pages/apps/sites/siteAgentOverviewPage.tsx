import { useQuery } from '@tanstack/react-query'
import { ArrowUpRight, CheckCircle2, Circle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router-dom'

import { aiApi, siteApi } from '@/api'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'
import { useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'
import { siteWebUrl } from '@/utils/siteWebUrl'

import type { SiteWorkspaceOutletContext } from './siteWorkspaceLayout'

function StatusCard({
  label,
  children,
  to,
  linkLabel,
}: {
  label: string
  children: React.ReactNode
  to?: string
  linkLabel?: string
}) {
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs text-slate-500">{label}</p>
      <div className="mt-2 text-sm font-medium text-slate-900">{children}</div>
      {to ? (
        <Link
          to={to}
          className="mt-3 inline-flex items-center gap-1 text-xs font-medium text-blue-700 hover:text-blue-800"
        >
          {linkLabel}
          <ArrowUpRight className="h-3.5 w-3.5" />
        </Link>
      ) : null}
    </article>
  )
}

/** Agent 总览：站点工作台落地页。一屏看清这个 agent 的健康（模型链 / Prompt / 知识库 / 对话）与发布清单。 */
export function SiteAgentOverviewPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<SiteWorkspaceOutletContext>()
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const canKnowledge = useHasPermission(PERMISSION_CODES.aiKbList)
  const canConversations = useHasPermission(PERMISSION_CODES.aiConversationList)
  const unavailable = t('agentOverview.unavailable')

  const runtimeQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'runtime'],
    queryFn: () => aiApi.getRuntime(site.id),
    enabled: canPrompt,
  })
  const promptQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'prompt'],
    queryFn: () => aiApi.getPromptProfile(site.id),
    enabled: canPrompt,
  })
  const knowledgeQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'knowledge', 'overview'],
    queryFn: () => aiApi.listKnowledgeDocuments(site.id, { pageNum: 1, pageSize: 100 }),
    enabled: canKnowledge,
  })
  const conversationsQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'conversations', 'overview'],
    queryFn: () => aiApi.listConversations(site.id, { pageNum: 1, pageSize: 5 }),
    enabled: canConversations,
  })
  const checklistQuery = useQuery({
    queryKey: ['sites', site.id, 'runtime-status'],
    queryFn: () => siteApi.getSiteRuntimeStatus(site.id),
  })

  const runtime = runtimeQuery.data
  const runtimeStatus = runtime?.status ?? null
  const knowledgeDocs = knowledgeQuery.data?.records ?? []
  const indexedCount = knowledgeDocs.filter((doc) => doc.indexStatus === 'INDEXED').length
  const failedCount = knowledgeDocs.filter((doc) => doc.indexStatus === 'FAILED').length
  const conversations = conversationsQuery.data?.records ?? []
  const checklist = checklistQuery.data?.publishChecklist
  const checklistItems = [
    { key: 'basicInfo', done: checklist?.basicInfo ?? false },
    { key: 'theme', done: checklist?.theme ?? false },
    { key: 'domain', done: checklist?.domain ?? false },
    { key: 'homeContent', done: checklist?.homeContent ?? false },
    { key: 'aiConfig', done: checklist?.aiConfig ?? false },
  ] as const
  const visitUrl = siteWebUrl(site.siteCode)

  return (
    <section>
      <PageHeader title={t('workspace.nav.agentOverview')} description={t('agentOverview.description')} />

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
        <StatusCard
          label={t('agentOverview.cards.modelChain')}
          to={ROUTES.apps.siteAgentRuntime(site.id)}
          linkLabel={t('agentOverview.actions.openRuntime')}
        >
          {!canPrompt || runtimeQuery.isPending ? (
            <span className="text-slate-500">{unavailable}</span>
          ) : runtime == null || !runtime.reachable || runtimeStatus == null ? (
            <span className="text-rose-700">{t('agentOverview.model.unreachable')}</span>
          ) : (
            <>
              <p className="break-all">{runtimeStatus.provider ?? unavailable}</p>
              <p className="mt-1 text-xs font-normal text-slate-500">
                {t('agentOverview.model.status')}: {runtimeStatus.providerStatus ?? unavailable}
              </p>
              <p className="mt-1 text-xs font-normal text-slate-500">
                {t('agentOverview.model.fallbacks')}:{' '}
                {runtimeStatus.fallbackProviders.length > 0
                  ? runtimeStatus.fallbackProviders.join('、')
                  : t('agentOverview.model.noFallback')}
              </p>
            </>
          )}
        </StatusCard>

        <StatusCard
          label={t('agentOverview.cards.prompt')}
          to={ROUTES.apps.siteAgentPrompt(site.id)}
          linkLabel={t('agentOverview.actions.openPrompt')}
        >
          {!canPrompt || promptQuery.isPending ? (
            <span className="text-slate-500">{unavailable}</span>
          ) : promptQuery.data?.id != null ? (
            <>
              <p>{t('agentOverview.prompt.configured')}</p>
              <p className="mt-1 text-xs font-normal text-slate-500">{promptQuery.data?.updatedAt ?? ''}</p>
            </>
          ) : (
            <span className="text-amber-700">{t('agentOverview.prompt.notConfigured')}</span>
          )}
        </StatusCard>

        <StatusCard
          label={t('agentOverview.cards.knowledge')}
          to={ROUTES.apps.siteAgentKnowledge(site.id)}
          linkLabel={t('agentOverview.actions.openKnowledge')}
        >
          {!canKnowledge || knowledgeQuery.isPending ? (
            <span className="text-slate-500">{unavailable}</span>
          ) : (
            <>
              <p>
                {t('agentOverview.knowledge.documents')}: {knowledgeQuery.data?.total ?? 0}
              </p>
              <p className="mt-1 text-xs font-normal text-slate-500">
                {t('agentOverview.knowledge.indexed')}: {indexedCount}
              </p>
              {failedCount > 0 ? (
                <p className="mt-1 text-xs font-medium text-rose-700">
                  {t('agentOverview.knowledge.failed')}: {failedCount}
                </p>
              ) : null}
            </>
          )}
        </StatusCard>

        <StatusCard
          label={t('agentOverview.cards.conversations')}
          to={ROUTES.apps.siteAgentConversations(site.id)}
          linkLabel={t('agentOverview.actions.openConversations')}
        >
          {!canConversations || conversationsQuery.isPending ? (
            <span className="text-slate-500">{unavailable}</span>
          ) : (
            <>
              <p>
                {t('agentOverview.conversations.total')}: {conversationsQuery.data?.total ?? 0}
              </p>
              <p className="mt-1 text-xs font-normal text-slate-500">
                {t('agentOverview.conversations.latest')}: {conversations[0]?.lastMessageAt ?? '—'}
              </p>
            </>
          )}
        </StatusCard>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between gap-2">
            <h2 className="text-base font-semibold text-slate-900">{t('agentOverview.recent.title')}</h2>
            <div className="flex gap-2">
              <Link
                to={ROUTES.apps.siteAgentPreview(site.id)}
                className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-slate-800"
              >
                {t('agentOverview.actions.openPreview')}
              </Link>
              {visitUrl ? (
                <a
                  href={visitUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
                >
                  {t('agentOverview.actions.visitSite')}
                </a>
              ) : null}
            </div>
          </div>
          {conversations.length === 0 ? (
            <p className="mt-3 text-sm text-slate-500">{t('agentOverview.recent.empty')}</p>
          ) : (
            <ul className="mt-3 space-y-2">
              {conversations.map((item) => (
                <li
                  key={item.id}
                  className="flex items-center justify-between gap-3 rounded-xl border border-slate-100 bg-slate-50 px-3 py-2"
                >
                  <span className="truncate text-sm font-medium text-slate-900">
                    {item.title?.trim() || `#${item.id}`}
                  </span>
                  <span className="shrink-0 text-xs text-slate-500">{item.lastMessageAt ?? ''}</span>
                </li>
              ))}
            </ul>
          )}
        </article>

        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="text-base font-semibold text-slate-900">{t('agentOverview.checklist.title')}</h2>
          <div className="mt-3 space-y-2">
            {checklistItems.map((item) => (
              <div
                key={item.key}
                className="flex items-center gap-2 rounded-xl border border-slate-100 bg-slate-50 px-3 py-2"
              >
                {item.done ? (
                  <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                ) : (
                  <Circle className="h-4 w-4 text-slate-400" />
                )}
                <span className={cn('text-sm', item.done ? 'text-slate-900' : 'text-slate-500')}>
                  {t(`agentOverview.checklist.items.${item.key}`)}
                </span>
              </div>
            ))}
          </div>
        </article>
      </div>
    </section>
  )
}
