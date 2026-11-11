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
import type { AiChainMode, AiModelEntry, AiRuntimeLimits, AiRuntimeStatus } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'
import { isApiError } from '@/utils/http/apiError'

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

function isForbiddenError(error: unknown) {
  return isApiError(error) && (error.status === 403 || error.code === 1003)
}

/**
 * 组链节点卡（DESIGN_SYSTEM §10 组链可视化）：名称＋模型两行呈现；每行首个节点
 * 为主用（主色边＋5% 底＋主用标签），已停用节点虚线边＋55% 透明。
 */
function ChainNode({
  name,
  model,
  main,
  disabled,
  missing,
  mainLabel,
}: {
  name: string
  model: string
  main?: boolean
  disabled?: boolean
  missing?: boolean
  mainLabel: string
}) {
  return (
    <span
      className={cn(
        'min-w-0 rounded-[10px] border px-3 py-2',
        main && !missing ? 'border-primary/50 bg-primary/5' : 'border-admin bg-admin-surface',
        (disabled || missing) && 'border-dashed opacity-55',
      )}
    >
      <span className="flex items-center gap-1.5">
        <span className="truncate text-[13px] font-semibold text-admin">{name}</span>
        {main && !missing ? (
          <span className="shrink-0 rounded-full bg-primary/10 px-1.5 py-0.5 text-xs font-medium text-primary">
            {mainLabel}
          </span>
        ) : null}
      </span>
      <span className={cn('block truncate text-xs', missing ? 'text-warning-emphasis' : 'text-admin-muted')}>
        {model}
      </span>
    </span>
  )
}

/** 组链节点间连接件：横线＋线下触发条件小字（§10 定案形态，不以图标代替）。 */
function ChainConnector({ label }: { label: string }) {
  return (
    <span className="flex flex-col items-center gap-1 self-center px-1">
      <span className="w-8 border-t border-admin" />
      <span className="whitespace-nowrap text-xs text-admin-muted">{label}</span>
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

type ChainSlot = { id: number; entry: AiModelEntry | null }

/** 概览：上面是模型、Prompt、知识库，下面是 Harness。 */
export function AgentOverviewPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const canKnowledge = useHasPermission(PERMISSION_CODES.aiKbList)

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
  // 组链可视化（§10）的按模式数据：agent 自有链的 key/cli 两份有序 entry id 名单，
  // join 平台模型条目取名称/模型/启停（与对话链编辑器同源同 queryKey）。
  const chainQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'chain'],
    queryFn: () => aiApi.getAgentChain(site.id),
    enabled: canPrompt,
  })
  const modelEntriesQuery = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
    enabled: canPrompt,
  })
  const knowledgeQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'knowledge', 'overview'],
    // 仅取分页元数据 total；pageSize 尽量小，避免用样本冒充全量索引统计。
    queryFn: () => aiApi.listKnowledgeDocuments(site.id, { pageNum: 1, pageSize: 1 }),
    enabled: canKnowledge,
  })

  const runtime = runtimeQuery.data
  const runtimeStatus = runtime?.status ?? null
  const configuredModel = runtimeStatus?.provider ?? null
  const modelsConfigTo = ROUTES.apps.agentConfigModels(site.id)

  const chainEntryById = new Map((modelEntriesQuery.data ?? []).map((entry) => [entry.id, entry]))
  const chainRows = (['KEY', 'CLI'] as AiChainMode[])
    .map((mode) => {
      const ids = chainQuery.data
        ? mode === 'KEY'
          ? chainQuery.data.keyEntryIds
          : chainQuery.data.cliEntryIds
        : []
      const slots: ChainSlot[] = ids.map((id) => ({ id, entry: chainEntryById.get(id) ?? null }))
      return { mode, slots }
    })
    .filter((row) => row.slots.length > 0)

  const modelForbidden =
    !canPrompt ||
    isForbiddenError(runtimeQuery.error) ||
    isForbiddenError(chainQuery.error) ||
    isForbiddenError(modelEntriesQuery.error)
  const modelLoading = canPrompt && (runtimeQuery.isPending || chainQuery.isPending || modelEntriesQuery.isPending)
  const modelError =
    canPrompt &&
    !modelLoading &&
    (runtimeQuery.isError || chainQuery.isError || modelEntriesQuery.isError) &&
    !modelForbidden
  const modelEmpty = canPrompt && !modelLoading && !modelError && chainRows.length === 0
  const modelChainReady = canPrompt && !modelLoading && !modelError && !modelForbidden && chainRows.length > 0

  let modelValueNode: ReactNode
  if (!canPrompt || modelForbidden) {
    modelValueNode = <p className="text-sm font-semibold leading-5 text-admin">{t('agentOverview.noPermission')}</p>
  } else if (modelLoading) {
    modelValueNode = <p className="text-sm font-semibold leading-5 text-admin-muted">{t('agentOverview.loading')}</p>
  } else if (modelError) {
    modelValueNode = <p className="text-sm font-semibold leading-5 text-danger-emphasis">{t('agentOverview.loadError')}</p>
  } else if (modelEmpty) {
    const unreachable = runtime == null || !runtime.reachable || runtimeStatus == null
    modelValueNode = (
      <p className="text-sm font-semibold leading-5 text-admin">
        {unreachable
          ? t('agentOverview.model.unreachable')
          : configuredModel
            ? modelFace(configuredModel).name
            : t('agentOverview.model.notConfigured')}
      </p>
    )
  } else {
    modelValueNode = (
      <div className="space-y-2">
        {chainRows.map((row) => (
          <div key={row.mode} className="flex items-center gap-2">
            <span className="w-16 shrink-0 text-xs font-semibold uppercase text-admin-muted">{row.mode}</span>
            <div className="flex flex-wrap items-center gap-2">
              {row.slots.map((slot, index) => (
                <Fragment key={slot.id}>
                  {index > 0 ? <ChainConnector label={t('agentOverview.model.fallbackTrigger')} /> : null}
                  {slot.entry ? (
                    <ChainNode
                      name={slot.entry.displayName}
                      model={modelFace(slot.entry.modelSpec).name}
                      main={index === 0}
                      disabled={!slot.entry.enabled}
                      mainLabel={t('aiWorkspace.chainPreset.primaryMark')}
                    />
                  ) : (
                    <ChainNode
                      name={`#${slot.id}`}
                      model={t('agentOverview.model.missingEntry')}
                      main={index === 0}
                      missing
                      mainLabel={t('aiWorkspace.chainPreset.primaryMark')}
                    />
                  )}
                </Fragment>
              ))}
            </div>
          </div>
        ))}
      </div>
    )
  }

  const modelSub =
    modelChainReady && chainRows.some((row) => row.slots.some((slot) => slot.entry == null)) ? (
      <span className="text-warning-emphasis">{t('agentOverview.model.missingHint')}</span>
    ) : undefined

  // #214 UoW-4：Prompt 卡改读 agent 本地副本（配置预设引用已退役）；标题行展示
  // 来源模板留痕名，摘要为本地副本的 systemPrompt 本体。
  const promptConfig = promptConfigQuery.data
  const promptConfigured =
    promptConfig != null &&
    (promptConfig.promptTemplateId != null ||
      Boolean(promptConfig.systemPrompt?.trim()) ||
      promptConfig.temperature != null)
  const promptForbidden = !canPrompt || isForbiddenError(promptConfigQuery.error)
  const promptLoading = canPrompt && promptConfigQuery.isPending
  const promptError = canPrompt && promptConfigQuery.isError && !promptForbidden

  let promptValueNode: ReactNode
  let promptBadge: ReactNode
  if (!canPrompt || promptForbidden) {
    promptValueNode = <p className="text-sm font-semibold leading-5 text-admin">{t('agentOverview.noPermission')}</p>
  } else if (promptLoading) {
    promptValueNode = <p className="text-sm font-semibold leading-5 text-admin-muted">{t('agentOverview.loading')}</p>
  } else if (promptError) {
    promptValueNode = <p className="text-sm font-semibold leading-5 text-danger-emphasis">{t('agentOverview.loadError')}</p>
  } else if (promptConfigured) {
    const promptSummary = promptConfig?.systemPrompt?.trim() || null
    const promptTitle =
      promptConfig?.promptTemplateName ??
      (promptConfig?.promptTemplateId != null
        ? `#${promptConfig.promptTemplateId}`
        : t('agentOverview.prompt.configured'))
    promptBadge = (
      <StatusTag status={SITE_STATUS.ACTIVE} label={t('agentOverview.prompt.configured')} />
    )
    promptValueNode = (
      <div className="space-y-1.5">
        <p className="text-sm font-semibold leading-5 text-admin">{promptTitle}</p>
        {promptSummary ? (
          <blockquote className="line-clamp-2 border-l-[3px] border-[rgb(var(--primary)/.35)] pl-2.5 text-xs leading-relaxed text-admin-muted">
            {promptSummary}
          </blockquote>
        ) : null}
      </div>
    )
  } else {
    promptBadge = (
      <StatusTag status={SITE_STATUS.DISABLED} label={t('agentOverview.prompt.notConfigured')} />
    )
    promptValueNode = (
      <p className="text-sm font-semibold leading-5 text-admin">{t('agentOverview.prompt.notConfigured')}</p>
    )
  }

  // 知识 total 来自服务端分页元数据，可信；indexed/failed 若按 pageSize:100 样本推算则不得冒充全量。
  const knowledgeForbidden = !canKnowledge || isForbiddenError(knowledgeQuery.error)
  const knowledgeLoading = canKnowledge && knowledgeQuery.isPending
  const knowledgeError = canKnowledge && knowledgeQuery.isError && !knowledgeForbidden
  const knowledgeTotal = knowledgeQuery.data?.total

  let knowledgeValueNode: ReactNode
  if (!canKnowledge || knowledgeForbidden) {
    knowledgeValueNode = <p className="text-sm font-semibold leading-5 text-admin">{t('agentOverview.noPermission')}</p>
  } else if (knowledgeLoading) {
    knowledgeValueNode = <p className="text-sm font-semibold leading-5 text-admin-muted">{t('agentOverview.loading')}</p>
  } else if (knowledgeError) {
    knowledgeValueNode = (
      <p className="text-sm font-semibold leading-5 text-danger-emphasis">{t('agentOverview.loadError')}</p>
    )
  } else {
    knowledgeValueNode = (
      <p className="flex items-baseline gap-1.5">
        <span className="text-2xl font-bold leading-none text-admin">{knowledgeTotal ?? 0}</span>
        <span className="text-xs text-admin-muted">{t('agentOverview.knowledge.documents')}</span>
      </p>
    )
  }

  return (
    <section className="w-full">
      <div className="space-y-3">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          <Fact
            icon={Zap}
            label={t('agentOverview.cards.modelChain')}
            value={modelValueNode}
            sub={modelSub}
            footerLabel={
              modelChainReady && chainRows.some((row) => row.slots.some((slot) => slot.entry == null))
                ? t('agentOverview.actions.fixModels')
                : t('agentOverview.actions.openRuntime')
            }
            to={modelsConfigTo}
          />
          <Fact
            icon={Code}
            label={t('agentOverview.cards.prompt')}
            badge={promptBadge}
            value={promptValueNode}
            footerLabel={t('agentOverview.actions.openPrompt')}
            to={ROUTES.apps.agentConfigPrompt(site.id)}
          />
          <Fact
            icon={BookOpen}
            label={t('agentOverview.cards.knowledge')}
            value={knowledgeValueNode}
            footerLabel={t('agentOverview.actions.openKnowledge')}
            to={ROUTES.apps.agentConfigKnowledge(site.id)}
          />
        </div>
        {canPrompt ? (
          <HarnessPanel
            pending={runtimeQuery.isPending}
            forbidden={isForbiddenError(runtimeQuery.error)}
            error={runtimeQuery.isError && !isForbiddenError(runtimeQuery.error)}
            unreachable={runtime == null || !runtime.reachable || runtimeStatus == null}
            status={runtimeStatus}
            skills={skillsQuery.data}
            skillsPending={skillsQuery.isPending}
            skillsError={skillsQuery.isError}
            skillsForbidden={isForbiddenError(skillsQuery.error)}
          />
        ) : (
          <article className="rounded-xl border border-admin bg-admin-surface px-4 py-4">
            <p className="text-sm text-admin-muted">{t('agentOverview.noPermission')}</p>
          </article>
        )}
      </div>
    </section>
  )
}

function HarnessPanel({
  pending,
  forbidden,
  error,
  unreachable,
  status,
  skills,
  skillsPending,
  skillsError,
  skillsForbidden,
}: {
  pending: boolean
  forbidden: boolean
  error: boolean
  unreachable: boolean
  status: AiRuntimeStatus | null
  skills: Array<{ id: string; description: string | null; version: string | null }> | undefined
  skillsPending: boolean
  skillsError: boolean
  skillsForbidden: boolean
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
        <p className="px-4 py-4 text-sm text-admin-muted">{t('agentOverview.loading')}</p>
      ) : forbidden ? (
        <p className="px-4 py-4 text-sm text-admin-muted">{t('agentOverview.noPermission')}</p>
      ) : error ? (
        <p className="px-4 py-4 text-sm text-danger-emphasis">{t('agentOverview.loadError')}</p>
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
                    <p className="truncate font-mono text-xs font-semibold text-admin">{key}</p>
                    <p className="truncate text-xs text-admin-muted">{t(`agentOverview.harness.${key}`)}</p>
                  </div>
                </div>
              )
            })}
          </div>
          <div className="grid border-t border-admin lg:grid-cols-2">
            <section className="border-b border-admin px-4 py-3 lg:border-b-0 lg:border-r">
              <h3 className="text-xs font-medium uppercase tracking-wide text-admin-muted">{t('agentOverview.harness.skills')}</h3>
              {skillsPending ? (
                <p className="mt-2 text-sm text-admin-muted">{t('agentOverview.loading')}</p>
              ) : skillsForbidden ? (
                <p className="mt-2 text-sm text-admin-muted">{t('agentOverview.noPermission')}</p>
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
