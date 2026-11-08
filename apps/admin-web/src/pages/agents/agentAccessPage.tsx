import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router-dom'

import { aiApi } from '@/api'
import { ROUTES } from '@/router/config/routePaths'
import { useHasPermission } from '@/router/permission'
import type { AiAgentChain, AiChainMode, AiModelEntry } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

/**
 * 该模式当前生效的平台入口（与 Java 平台链同规则，Issue #210 UoW-6）：入口已全平台
 * 注册、与查看的 agent 无关；带来源标注的入口（原专属）优先层在前，层内默认在前、
 * id 升序，第一个默认即主用。本页只读呈现（Issue #216 撤除本页的平台写路径），
 * 入口维护在模型 API 管理页。
 */
function entryFor(entries: AiModelEntry[], mode: 'CLI' | 'KEY') {
  const enabled = entries.filter((entry) => entry.enabled && (entry.mode ?? 'KEY') === mode)
  const tier = (provenanceMarked: boolean) =>
    enabled
      .filter((entry) => (provenanceMarked ? entry.sourceAgentId != null : entry.sourceAgentId == null))
      .sort((a, b) => Number(b.defaultEntry) - Number(a.defaultEntry) || a.id - b.id)
  return [...tier(true), ...tier(false)].find((entry) => entry.defaultEntry) ?? null
}

function ScopeNote({ entry, agentId }: { entry: AiModelEntry | null; agentId: number }) {
  const { t } = useTranslation('sites')
  if (entry == null) return <p className="text-xs text-admin-muted">{t('access.none')}</p>
  if (entry.sourceAgentId === agentId) return <p className="text-xs text-admin-muted">{t('access.usingAgent')}</p>
  return <p className="text-xs text-admin-muted">{t('access.usingGlobal')}</p>
}

function PlatformEntryCard({
  mode,
  entry,
  agentId,
}: {
  mode: 'CLI' | 'KEY'
  entry: AiModelEntry | null
  agentId: number
}) {
  const { t } = useTranslation('sites')
  const isCli = mode === 'CLI'
  return (
    <article className="flex h-full flex-col rounded-xl border border-border bg-card p-5">
      <div className="mb-3 flex items-start justify-between gap-3 border-b border-border pb-3">
        <div>
          <h3 className="text-sm font-semibold text-foreground">
            {t(isCli ? 'access.cliTitle' : 'access.keyTitle')}
          </h3>
          <p className="mt-1 text-xs leading-5 text-muted-foreground">
            {t(isCli ? 'access.cliDescription' : 'access.keyDescription')}
          </p>
        </div>
        <span
          className={
            isCli
              ? 'shrink-0 rounded-md bg-info-muted px-2 py-0.5 text-[11px] font-medium text-info-emphasis'
              : 'shrink-0 rounded-md bg-primary/10 px-2 py-0.5 text-[11px] font-medium text-primary'
          }
        >
          {mode}
        </span>
      </div>
      <div className="mb-3">
        <ScopeNote entry={entry} agentId={agentId} />
      </div>
      {entry ? (
        <div className="space-y-2">
          <p className="text-sm font-medium text-foreground">{entry.displayName}</p>
          <p className="font-mono text-xs text-admin-muted">{entry.modelSpec}</p>
          <div className="flex flex-wrap gap-1.5">
            <span className="rounded-md bg-success-muted px-2 py-0.5 text-[11px] font-medium text-success-emphasis">
              {t('aiWorkspace.runtime.enabled')}
            </span>
            <span className="rounded-md bg-primary/10 px-2 py-0.5 text-[11px] font-medium text-primary">
              {t('aiWorkspace.chainPreset.defaultMark')}
            </span>
          </div>
        </div>
      ) : null}
      <p className="mt-4 text-sm">
        <Link to={ROUTES.ai.modelEntries} className="font-medium text-admin underline-offset-2 hover:underline">
          {t('access.openModelEntries')}
        </Link>
      </p>
    </article>
  )
}

const CHAIN_ROWS: AiChainMode[] = ['CLI', 'KEY']

function AgentChainSummaryCard({
  agentId,
  chain,
  entryById,
}: {
  agentId: number
  chain: AiAgentChain | undefined
  entryById: Map<number, AiModelEntry>
}) {
  const { t } = useTranslation('sites')
  return (
    <article className="rounded-xl border border-border bg-card p-5">
      <div className="mb-3 border-b border-border pb-3">
        <h3 className="text-sm font-semibold text-foreground">{t('aiWorkspace.chainPreset.title')}</h3>
        <p className="mt-1 text-xs leading-5 text-muted-foreground">{t('access.chainHint')}</p>
      </div>
      <ul className="space-y-2">
        {CHAIN_ROWS.map((mode) => {
          const ids = chain ? (mode === 'KEY' ? chain.keyEntryIds : chain.cliEntryIds) : []
          // 主用口径与链编辑器/运行时解析一致：名单序内第一个默认成员，无默认取首位。
          const primaryId = ids.find((id) => entryById.get(id)?.defaultEntry) ?? ids[0]
          const primary = primaryId != null ? entryById.get(primaryId) : undefined
          return (
            <li key={mode} className="flex flex-wrap items-center gap-2 text-sm">
              <span className="w-10 shrink-0 text-xs font-semibold uppercase text-admin-muted">{mode}</span>
              {ids.length === 0 ? (
                <span className="text-admin-muted">{t('aiWorkspace.chainPreset.noMembers')}</span>
              ) : (
                <>
                  <span className="text-admin-muted">{t('access.chainMembers', { count: ids.length })}</span>
                  <span className="text-foreground">
                    {t('aiWorkspace.chainPreset.primaryMark')}：
                    {primary ? primary.displayName || primary.modelSpec : `#${primaryId}`}
                  </span>
                </>
              )}
            </li>
          )
        })}
      </ul>
      <p className="mt-4 text-sm">
        <Link to={ROUTES.apps.agentRuntime(agentId)} className="font-medium text-admin underline-offset-2 hover:underline">
          {t('access.openRuntime')}
        </Link>
      </p>
    </article>
  )
}

export function AgentAccessPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const canRead = useHasPermission(PERMISSION_CODES.aiModelRead)

  const entriesQuery = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
    enabled: canRead,
  })
  const chainQuery = useQuery({
    queryKey: ['sites', site.id, 'ai', 'chain'],
    queryFn: () => aiApi.getAgentChain(site.id),
    enabled: canRead,
  })

  if (!canRead) {
    return <p className="text-sm text-admin-muted">{t('access.noPermission')}</p>
  }
  if (entriesQuery.isPending || chainQuery.isPending) {
    return <p className="text-sm text-admin-muted">{t('access.loading')}</p>
  }
  if (entriesQuery.isError || chainQuery.isError) {
    return <p className="text-sm text-danger-emphasis">{t('access.loadError')}</p>
  }

  const entries = entriesQuery.data ?? []
  const entryById = new Map(entries.map((entry) => [entry.id, entry]))

  return (
    <section className="w-full space-y-3">
      <div className="grid gap-3 xl:grid-cols-2">
        <PlatformEntryCard mode="CLI" entry={entryFor(entries, 'CLI')} agentId={site.id} />
        <PlatformEntryCard mode="KEY" entry={entryFor(entries, 'KEY')} agentId={site.id} />
      </div>
      <AgentChainSummaryCard agentId={site.id} chain={chainQuery.data} entryById={entryById} />
    </section>
  )
}
