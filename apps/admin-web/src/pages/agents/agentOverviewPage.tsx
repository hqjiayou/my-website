import { useQuery } from '@tanstack/react-query'
import { ArrowRight, BookOpen, Code, Zap } from 'lucide-react'
import { Fragment, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router-dom'

import { aiApi, SITE_STATUS } from '@/api'
import { StatusTag } from '@/components/admin'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'
import { useHasPermission } from '@/router/permission'
import type { AiRuntimeLimits, AiRuntimeStatus } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

import { SkillCatalogItem } from './agentAiSkillCatalog'
import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

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

/** 模型链节点：主用节点描边高亮，备援节点为普通底色，均带表示在线的小圆点。 */
function ChainNode({ main, children }: { main?: boolean; children: ReactNode }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-lg border px-2 py-1 text-xs font-semibold',
        main
          ? 'border-[rgb(var(--primary)/.4)] bg-[rgb(var(--primary)/.08)] text-[rgb(var(--primary))]'
          : 'border-admin bg-admin-surface-muted text-admin',
      )}
    >
      <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-admin-success" />
      {children}
    </span>
  )
}

function Fact({
  icon: Icon,
  label,
  badge,
  value,
  sub,
  footerLabel,
  to,
}: {
  icon: typeof Zap
  label: string
  badge?: ReactNode
  value: ReactNode
  sub?: ReactNode
  footerLabel: string
  to: string
}) {
  return (
    <Link
      to={to}
      className={cn(
        'flex flex-col gap-2 rounded-[14px] border border-admin bg-admin-surface px-4 py-3.5 transition admin-focus-ring',
        'hover:border-[rgb(var(--muted-foreground)/.35)] hover:shadow-admin-pop',
      )}
    >
      <p className="flex items-center gap-1.5 text-xs font-semibold text-admin-muted">
        <Icon className="h-3.5 w-3.5 shrink-0 text-admin-muted" />
        {label}
        {badge ? <span className="ml-auto">{badge}</span> : null}
      </p>
      {value}
      {sub ? <p className="text-xs leading-5 text-admin-muted">{sub}</p> : null}
      <p className="mt-auto flex items-center gap-1 border-t border-dashed border-admin pt-2 text-xs font-semibold text-[rgb(var(--primary))]">
        {footerLabel}
        <ArrowRight className="h-3 w-3" />
      </p>
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

/** 概览：上面是模型、Prompt、知识库，下面是 Harness。 */
export function AgentOverviewPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const canKnowledge = useHasPermission(PERMISSION_CODES.aiKbList)
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
  const promptConfigQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'prompt'],
    queryFn: () => aiApi.getAgentPromptConfig(site.id),
    enabled: canPrompt,
  })
  const knowledgeQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'knowledge', 'overview'],
    queryFn: () => aiApi.listKnowledgeDocuments(site.id, { pageNum: 1, pageSize: 100 }),
    enabled: canKnowledge,
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
        runtimeStatus.fallbackProviders.length > 0
          ? runtimeStatus.fallbackProviders.map((spec) => modelFace(spec).name).join('、')
          : null,
      ]
        .filter(Boolean)
        .join(' · ')
    : undefined
  const modelChainReady =
    canPrompt && !runtimeQuery.isPending && runtime != null && runtime.reachable && runtimeStatus != null && configuredModel != null
  const modelValueNode = modelChainReady ? (
    <div className="flex flex-wrap items-center gap-1.5">
      <ChainNode main>{modelFace(configuredModel as string).name}</ChainNode>
      {runtimeStatus!.fallbackProviders.map((spec) => (
        <Fragment key={spec}>
          <ArrowRight className="h-3.5 w-3.5 shrink-0 text-admin-muted" />
          <ChainNode>{modelFace(spec).name}</ChainNode>
        </Fragment>
      ))}
    </div>
  ) : (
    <p className="text-sm font-semibold leading-5 text-admin">{modelValue}</p>
  )

  // #214 UoW-4：Prompt 卡改读 agent 本地副本（配置预设引用已退役）；标题行展示
  // 来源模板留痕名，摘要为本地副本的 systemPrompt 本体。
  const promptConfig = promptConfigQuery.data
  const promptConfigured =
    promptConfig != null &&
    (promptConfig.promptTemplateId != null ||
      Boolean(promptConfig.systemPrompt?.trim()) ||
      promptConfig.temperature != null)
  const promptValue = !canPrompt || promptConfigQuery.isPending
    ? unavailable
    : promptConfigured
      ? t('agentOverview.prompt.configured')
      : t('agentOverview.prompt.notConfigured')
  const promptBadge =
    canPrompt && !promptConfigQuery.isPending ? (
      <StatusTag status={promptConfigured ? SITE_STATUS.ACTIVE : SITE_STATUS.DISABLED} label={promptValue} />
    ) : undefined
  const promptSummary = promptConfig?.systemPrompt?.trim() || null
  const promptTitle =
    promptConfig?.promptTemplateName ??
    (promptConfig?.promptTemplateId != null ? `#${promptConfig.promptTemplateId}` : promptValue)
  const promptValueNode = promptConfigured ? (
    <div className="space-y-1.5">
      <p className="text-sm font-semibold leading-5 text-admin">{promptTitle}</p>
      {promptSummary ? (
        <blockquote className="line-clamp-2 border-l-[3px] border-[rgb(var(--primary)/.35)] pl-2.5 text-xs leading-relaxed text-admin-muted">
          {promptSummary}
        </blockquote>
      ) : null}
    </div>
  ) : (
    <p className="text-sm font-semibold leading-5 text-admin">{promptValue}</p>
  )

  const knowledgeReady = canKnowledge && !knowledgeQuery.isPending
  const knowledgeTotal = knowledgeQuery.data?.total ?? 0
  const knowledgeValue = knowledgeReady ? String(knowledgeTotal) : unavailable
  const knowledgeValueNode = knowledgeReady ? (
    <div className="space-y-1.5">
      <p className="flex items-baseline gap-1.5">
        <span className="text-2xl font-bold leading-none text-admin">{knowledgeValue}</span>
        <span className="text-xs text-admin-muted">{t('agentOverview.knowledge.documents')}</span>
      </p>
      {knowledgeTotal > 0 ? (
        <div className="h-1.5 overflow-hidden rounded-full bg-admin-surface-muted">
          <div
            className="h-full rounded-full bg-[rgb(var(--chart-2))]"
            style={{ width: `${Math.min(100, Math.round((indexedCount / knowledgeTotal) * 100))}%` }}
          />
        </div>
      ) : null}
    </div>
  ) : (
    <p className="text-sm font-semibold leading-5 text-admin">{knowledgeValue}</p>
  )
  const knowledgeSub = knowledgeReady ? (
    <>
      {t('agentOverview.knowledge.indexed')} {indexedCount}
      {failedCount > 0 ? (
        <>
          {' · '}
          <span className="font-semibold text-admin-danger">
            {t('agentOverview.knowledge.failed')} {failedCount}
          </span>
        </>
      ) : null}
    </>
  ) : undefined

  return (
    <section className="w-full">
      <div className="space-y-3">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          <Fact
            icon={Zap}
            label={t('agentOverview.cards.modelChain')}
            value={modelValueNode}
            sub={modelDetail}
            footerLabel={t('agentOverview.actions.openRuntime')}
            to={ROUTES.apps.agentRuntime(site.id)}
          />
          <Fact
            icon={Code}
            label={t('agentOverview.cards.prompt')}
            badge={promptBadge}
            value={promptValueNode}
            footerLabel={t('agentOverview.actions.openPrompt')}
            to={ROUTES.apps.agentPrompt(site.id)}
          />
          <Fact
            icon={BookOpen}
            label={t('agentOverview.cards.knowledge')}
            value={knowledgeValueNode}
            sub={knowledgeSub}
            footerLabel={t('agentOverview.actions.openKnowledge')}
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
        <p className="px-4 py-4 text-sm text-danger-emphasis">{t('agentOverview.model.unreachable')}</p>
      ) : (
        <>
          <div className="grid sm:grid-cols-3">
            {HARNESS_FILES.map((key) => {
              const on = files?.[key] === true
              return (
                <div
                  key={key}
                  className="flex items-center gap-2.5 border-b border-admin px-4 py-3 last:border-b-0 sm:border-b-0 sm:border-r sm:last:border-r-0"
                >
                  <StatusTag
                    status={on ? SITE_STATUS.ACTIVE : SITE_STATUS.DISABLED}
                    tone={on ? 'active' : 'warning'}
                    label={t(on ? 'agentOverview.harness.present' : 'agentOverview.harness.missing')}
                  />
                  <div className="min-w-0">
                    <p className="truncate font-mono text-[12.5px] font-semibold text-admin">{key}</p>
                    <p className="truncate text-[11.5px] text-admin-muted">{t(`agentOverview.harness.${key}`)}</p>
                  </div>
                </div>
              )
            })}
          </div>
          <div className="grid border-t border-admin lg:grid-cols-2">
            <section className="border-b border-admin px-4 py-3 lg:border-b-0 lg:border-r">
              <h3 className="text-xs font-medium uppercase tracking-wide text-admin-muted">{t('agentOverview.harness.skills')}</h3>
              {skillsPending ? (
                <p className="mt-2 text-sm text-admin-muted">…</p>
              ) : skillsError ? (
                <p className="mt-2 text-sm text-danger-emphasis">{t('aiWorkspace.preview.skills.error')}</p>
              ) : !skills || skills.length === 0 ? (
                <p className="mt-2 text-sm text-admin-muted">{t('agentOverview.harness.noSkills')}</p>
              ) : (
                <ul className="mt-2">
                  {skills.map((skill) => (
                    <li key={skill.id} className="border-b border-dashed border-admin py-2 first:pt-0 last:border-b-0 last:pb-0">
                      <SkillCatalogItem
                        id={skill.id}
                        version={skill.version}
                        description={skill.description}
                        titleClassName="text-sm font-medium text-admin"
                        descriptionClassName="mt-1 text-xs leading-5 text-admin-muted"
                        versionClassName="ml-1.5 text-xs font-normal text-admin-muted"
                      />
                    </li>
                  ))}
                </ul>
              )}
            </section>
            <section className="space-y-4 px-4 py-3">
              <div>
                <h3 className="text-xs font-medium uppercase tracking-wide text-admin-muted">{t('aiWorkspace.runtime.limits.title')}</h3>
                <dl className="mt-2 space-y-1.5">
                  {LIMIT_KEYS.map((key) => (
                    <div key={key} className="flex items-baseline justify-between gap-3 text-sm">
                      <dt className="text-admin-muted">{t(`aiWorkspace.runtime.limits.${key}`)}</dt>
                      <dd className="font-semibold tabular-nums text-admin">
                        {status.limits[key] ?? t('aiWorkspace.runtime.limits.unlimited')}
                      </dd>
                    </div>
                  ))}
                </dl>
              </div>
              <div>
                <h3 className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-admin-muted">
                  {t('agentOverview.harness.mcp')}
                  {status.mcpStatus ? <span className="font-normal normal-case">{status.mcpStatus}</span> : null}
                </h3>
                {status.mcpServers.length === 0 ? (
                  <p className="mt-2 text-sm text-admin-muted">{t('agentOverview.harness.noMcp')}</p>
                ) : (
                  <ul className="mt-2 space-y-1">
                    {status.mcpServers.map((server) => (
                      <li key={server.name} className="flex items-center justify-between gap-3 text-sm">
                        <span className="flex min-w-0 items-center gap-1.5">
                          <span
                            className={cn('h-1.5 w-1.5 shrink-0 rounded-full', server.enabled ? 'bg-admin-success' : 'bg-admin-muted-foreground')}
                          />
                          <span className="truncate font-mono text-admin">{server.name}</span>
                        </span>
                        <span className={server.enabled ? 'text-success-emphasis' : 'text-admin-muted'}>
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

