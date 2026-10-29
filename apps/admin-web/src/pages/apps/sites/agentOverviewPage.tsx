import { useQuery } from '@tanstack/react-query'
import { type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router-dom'

import { aiApi } from '@/api'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'
import { useHasPermission } from '@/router/permission'
import type { AiConversationListItem, AiMessage, AiRuntimeLimits, AiRuntimeStatus } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

interface ConversationStats {
  messageCount: number
  contextTokens: number
  inputTokens: number
  outputTokens: number
  model: string | null
}

function summarizeMessages(messages: AiMessage[]): ConversationStats {
  let inputTokens = 0
  let outputTokens = 0
  let contextTokens = 0
  let model: string | null = null
  for (const message of messages) {
    const usage = message.agentMeta?.usage
    if (message.role === 'ASSISTANT' && usage) {
      inputTokens += usage.inputTokens
      outputTokens += usage.outputTokens
      contextTokens = usage.inputTokens
    }
    if (message.role === 'ASSISTANT' && message.agentMeta?.model) {
      model = message.agentMeta.model
    }
  }
  return {
    messageCount: messages.length,
    contextTokens,
    inputTokens,
    outputTokens,
    model,
  }
}

function familyOf(value: string) {
  if (value.includes('gpt') || value.includes('codex') || value === 'openai') return 'GPT'
  if (value.includes('gemini') || value === 'google' || value.startsWith('google')) return 'Gemini'
  if (value.includes('claude') || value === 'anthropic') return 'Claude'
  if (value.includes('muse') || value === 'meta') return 'Muse'
  return null
}

/** 模型名优先于通道前缀：openai:muse-wonder 是 Muse，不是 GPT。 */
function modelFace(spec: string) {
  const colon = spec.indexOf(':')
  const name = colon >= 0 && colon + 1 < spec.length ? spec.slice(colon + 1) : spec
  const prefix = colon >= 0 ? spec.slice(0, colon).toLowerCase() : ''
  const family = familyOf(name.toLowerCase()) ?? familyOf(prefix) ?? name
  return { family, name }
}

function formatWhen(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(undefined, { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function Fact({
  label,
  value,
  detail,
  to,
  className,
}: {
  label: string
  value: ReactNode
  detail?: ReactNode
  to: string
  className?: string
}) {
  return (
    <Link
      to={to}
      className={cn(
        'block rounded-xl border border-admin bg-admin-surface px-4 py-3 transition hover:bg-admin-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[rgb(var(--primary)/0.35)]',
        className,
      )}
    >
      <p className="text-xs font-medium text-admin-muted">{label}</p>
      <p className="mt-1 break-all text-sm font-semibold leading-5 text-admin">{value}</p>
      {detail ? <p className="mt-1 text-xs leading-5 text-admin-muted">{detail}</p> : null}
    </Link>
  )
}

const HARNESS_FILES = ['background', 'guardrails', 'toolGuidance'] as const

const LIMIT_KEYS: Array<keyof AiRuntimeLimits> = [
  'maxRequests',
  'maxToolCalls',
  'maxTotalTokens',
  'toolTimeoutSeconds',
  'historyMaxChars',
]

/** 概览：上面是模型、Prompt、知识库，中间是 Harness，右边是对话列表。 */
export function AgentOverviewPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const canKnowledge = useHasPermission(PERMISSION_CODES.aiKbList)
  const canConversations = useHasPermission(PERMISSION_CODES.aiConversationList)
  const unavailable = t('agentOverview.unavailable')

  const runtimeQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'runtime'],
    queryFn: () => aiApi.getRuntime(site.id),
    enabled: canPrompt,
  })
  const skillsQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'preview', 'skills'],
    queryFn: () => aiApi.listPreviewSkills(site.id),
    enabled: canPrompt && runtimeQuery.data?.reachable === true,
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
    queryFn: () => aiApi.listConversations(site.id, { pageNum: 1, pageSize: 20 }),
    enabled: canConversations,
  })

  const conversations = conversationsQuery.data?.records ?? []
  const conversationIds = conversations.map((item) => item.id)
  const statsQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'conversations', 'overview-stats', conversationIds],
    queryFn: async () => {
      const pages = await Promise.all(
        conversationIds.map(async (id) => [id, summarizeMessages(await aiApi.listConversationMessages(site.id, id))] as const),
      )
      return Object.fromEntries(pages) as Record<number, ConversationStats>
    },
    enabled: canConversations && conversationIds.length > 0,
  })

  const runtime = runtimeQuery.data
  const runtimeStatus = runtime?.status ?? null
  const configuredModel = runtimeStatus?.provider ?? null
  const knowledgeDocs = knowledgeQuery.data?.records ?? []
  const indexedCount = knowledgeDocs.filter((doc) => doc.indexStatus === 'INDEXED').length
  const failedCount = knowledgeDocs.filter((doc) => doc.indexStatus === 'FAILED').length

  const modelValue = !canPrompt || runtimeQuery.isPending
    ? unavailable
    : runtime == null || !runtime.reachable || runtimeStatus == null
      ? t('agentOverview.model.unreachable')
      : configuredModel
        ? modelFace(configuredModel).name
        : unavailable
  const modelDetail = runtimeStatus
    ? [
        runtimeStatus.providerStatus,
        runtimeStatus.fallbackProviders.length > 0
          ? runtimeStatus.fallbackProviders.map((spec) => modelFace(spec).name).join('、')
          : null,
      ]
        .filter(Boolean)
        .join(' · ')
    : undefined

  const promptValue = !canPrompt || promptQuery.isPending
    ? unavailable
    : promptQuery.data?.id != null
      ? t('agentOverview.prompt.configured')
      : t('agentOverview.prompt.notConfigured')

  const knowledgeValue = !canKnowledge || knowledgeQuery.isPending
    ? unavailable
    : String(knowledgeQuery.data?.total ?? 0)
  const knowledgeDetail = canKnowledge && !knowledgeQuery.isPending
    ? `${t('agentOverview.knowledge.indexed')} ${indexedCount}${failedCount > 0 ? ` · ${t('agentOverview.knowledge.failed')} ${failedCount}` : ''}`
    : undefined

  return (
    <section className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_26rem]">
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <Fact
            label={t('agentOverview.cards.modelChain')}
            value={modelValue}
            detail={modelDetail}
            to={ROUTES.apps.agentRuntime(site.id)}
          />
          <Fact
            label={t('agentOverview.cards.prompt')}
            value={promptValue}
            to={ROUTES.apps.agentPrompt(site.id)}
          />
          <Fact
            label={t('agentOverview.cards.knowledge')}
            value={knowledgeValue}
            detail={knowledgeDetail}
            to={ROUTES.apps.agentKnowledge(site.id)}
          />
        </div>
        <HarnessPanel
          pending={!canPrompt || runtimeQuery.isPending}
          unreachable={runtime == null || !runtime.reachable || runtimeStatus == null}
          status={runtimeStatus}
          skills={skillsQuery.data}
          skillsPending={skillsQuery.isPending}
          skillsError={skillsQuery.isError}
        />
      </div>

      <aside className="flex max-h-[calc(100vh-11rem)] flex-col overflow-hidden rounded-xl border border-admin bg-admin-surface lg:sticky lg:top-4">
        <div className="flex items-center justify-between gap-3 border-b border-admin px-4 py-3">
          <h2 className="text-sm font-semibold text-admin">{t('agentOverview.recent.title')}</h2>
          <Link
            to={ROUTES.apps.agentConversations(site.id)}
            className="text-xs font-medium text-admin-muted underline-offset-2 hover:text-admin hover:underline"
          >
            {t('agentOverview.actions.openConversations')}
            {conversationsQuery.data ? ` ${conversationsQuery.data.total}` : ''}
          </Link>
        </div>
        {!canConversations || conversationsQuery.isPending ? (
          <p className="px-4 py-4 text-sm text-admin-muted">{unavailable}</p>
        ) : conversations.length === 0 ? (
          <p className="px-4 py-4 text-sm text-admin-muted">{t('agentOverview.recent.empty')}</p>
        ) : (
          <ul className="min-h-0 flex-1 overflow-auto">
            {conversations.map((item) => (
              <ConversationRow
                key={item.id}
                item={item}
                stats={statsQuery.data?.[item.id]}
                pending={statsQuery.isPending}
                fallbackModel={configuredModel}
              />
            ))}
          </ul>
        )}
      </aside>
    </section>
  )
}

function HarnessPanel({
  pending,
  unreachable,
  status,
  skills,
  skillsPending,
  skillsError,
}: {
  pending: boolean
  unreachable: boolean
  status: AiRuntimeStatus | null
  skills: Array<{ id: string; description: string | null; version: string | null }> | undefined
  skillsPending: boolean
  skillsError: boolean
}) {
  const { t } = useTranslation('sites')
  const files = status?.harness

  return (
    <article className="overflow-hidden rounded-xl border border-admin bg-admin-surface">
      <div className="flex items-center justify-between gap-3 border-b border-admin px-4 py-3">
        <h2 className="text-sm font-semibold text-admin">{t('agentOverview.harness.title')}</h2>
        <p className="text-xs text-admin-muted">{t('agentOverview.harness.hint')}</p>
      </div>
      {pending ? (
        <p className="px-4 py-4 text-sm text-admin-muted">{t('agentOverview.unavailable')}</p>
      ) : unreachable || !status ? (
        <p className="px-4 py-4 text-sm text-rose-700">{t('agentOverview.model.unreachable')}</p>
      ) : (
        <>
          <div className="grid sm:grid-cols-3">
            {HARNESS_FILES.map((key) => {
              const on = files?.[key] === true
              return (
                <div key={key} className="flex items-center justify-between gap-3 border-b border-admin px-4 py-3 last:border-b-0 sm:border-b-0 sm:border-r sm:last:border-r-0">
                  <span className="text-sm text-admin">{t(`agentOverview.harness.${key}`)}</span>
                  <span className={cn('text-xs font-medium', on ? 'text-emerald-700' : 'text-amber-700')}>
                    {t(on ? 'agentOverview.harness.present' : 'agentOverview.harness.missing')}
                  </span>
                </div>
              )
            })}
          </div>
          <div className="grid border-t border-admin lg:grid-cols-2">
            <section className="border-b border-admin px-4 py-3 lg:border-b-0 lg:border-r">
              <h3 className="text-xs font-medium text-admin-muted">{t('agentOverview.harness.skills')}</h3>
              {skillsPending ? (
                <p className="mt-2 text-sm text-admin-muted">…</p>
              ) : skillsError ? (
                <p className="mt-2 text-sm text-rose-700">{t('aiWorkspace.preview.skills.error')}</p>
              ) : !skills || skills.length === 0 ? (
                <p className="mt-2 text-sm text-admin-muted">{t('agentOverview.harness.noSkills')}</p>
              ) : (
                <ul className="mt-2 space-y-2">
                  {skills.map((skill) => (
                    <li key={skill.id}>
                      <p className="text-sm font-medium text-admin">
                        {skill.id}
                        {skill.version ? <span className="ml-1.5 text-xs font-normal text-admin-muted">v{skill.version}</span> : null}
                      </p>
                      {skill.description ? <p className="text-xs leading-5 text-admin-muted">{skill.description}</p> : null}
                    </li>
                  ))}
                </ul>
              )}
            </section>
            <section className="space-y-4 px-4 py-3">
              <div>
                <h3 className="text-xs font-medium text-admin-muted">{t('aiWorkspace.runtime.limits.title')}</h3>
                <dl className="mt-2 space-y-1.5">
                  {LIMIT_KEYS.map((key) => (
                    <div key={key} className="flex items-baseline justify-between gap-3 text-sm">
                      <dt className="text-admin-muted">{t(`aiWorkspace.runtime.limits.${key}`)}</dt>
                      <dd className="tabular-nums text-admin">
                        {status.limits[key] ?? t('aiWorkspace.runtime.limits.unlimited')}
                      </dd>
                    </div>
                  ))}
                </dl>
              </div>
              <div>
                <h3 className="flex items-center gap-2 text-xs font-medium text-admin-muted">
                  {t('agentOverview.harness.mcp')}
                  {status.mcpStatus ? <span className="font-normal">{status.mcpStatus}</span> : null}
                </h3>
                {status.mcpServers.length === 0 ? (
                  <p className="mt-2 text-sm text-admin-muted">{t('agentOverview.harness.noMcp')}</p>
                ) : (
                  <ul className="mt-2 space-y-1">
                    {status.mcpServers.map((server) => (
                      <li key={server.name} className="flex items-center justify-between gap-3 text-sm">
                        <span className="truncate text-admin">{server.name}</span>
                        <span className={server.enabled ? 'text-emerald-700' : 'text-admin-muted'}>
                          {t(server.enabled ? 'aiWorkspace.runtime.enabled' : 'aiWorkspace.runtime.disabled')}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </section>
          </div>
        </>
      )}
    </article>
  )
}

function ConversationRow({
  item,
  stats,
  pending,
  fallbackModel,
}: {
  item: AiConversationListItem
  stats: ConversationStats | undefined
  pending: boolean
  fallbackModel: string | null
}) {
  const { t } = useTranslation('sites')
  const spec = stats?.model || fallbackModel
  const face = spec ? modelFace(spec) : null
  const count = (value: number) => value.toLocaleString()

  return (
    <li className="border-b border-admin px-4 py-3 last:border-b-0">
      <div className="flex items-start justify-between gap-3">
        <p className="min-w-0 truncate text-sm font-medium text-admin">{item.title?.trim() || `#${item.id}`}</p>
        <time className="shrink-0 text-xs tabular-nums text-admin-muted">{formatWhen(item.lastMessageAt)}</time>
      </div>
      <p className="mt-1.5 flex min-w-0 items-center gap-2 text-xs">
        {face ? (
          <>
            <span className="shrink-0 font-medium text-admin">{face.family}</span>
            {face.name !== face.family ? <span className="truncate text-admin-muted">{face.name}</span> : null}
          </>
        ) : (
          <span className="text-admin-muted">{t('agentOverview.recent.modelUnknown')}</span>
        )}
      </p>
      {stats ? (
        <p className="mt-1.5 grid grid-cols-4 gap-2 text-xs tabular-nums text-admin-muted">
          <span>{t('agentOverview.recent.length', { value: stats.messageCount })}</span>
          <span>{t('agentOverview.recent.context', { value: count(stats.contextTokens) })}</span>
          <span>{t('agentOverview.recent.input', { value: count(stats.inputTokens) })}</span>
          <span>{t('agentOverview.recent.output', { value: count(stats.outputTokens) })}</span>
        </p>
      ) : pending ? (
        <p className="mt-1.5 text-xs text-admin-muted">…</p>
      ) : null}
    </li>
  )
}
