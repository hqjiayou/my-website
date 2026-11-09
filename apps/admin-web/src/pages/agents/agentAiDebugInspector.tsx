import { useAuiState } from '@assistant-ui/react'
import { useQuery } from '@tanstack/react-query'
import { ChevronDown, X } from 'lucide-react'
import { useState, type KeyboardEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { aiApi } from '@/api/apps/aiApi'
import { ROUTES } from '@/router/config/routePaths'
import type {
  AiPreviewModelEntry,
  AiPreviewTodoItem,
  AiPreviewTodoStatus,
  AiRunUsage,
  AiSearchSummary,
} from '@/types/ai'
import { isSameModel, modelIdFromSpec } from '@/utils/modelSpec'
import { AiRunUsageLine } from './agentAiRunMeta'
import { skillCatalogDescription } from './agentAiSkillCatalogText'

export interface ToolCallTrace {
  toolCallId: string
  toolName: string
  state: 'running' | 'ok' | 'failed'
  summary?: AiSearchSummary
}

export interface RunTrace {
  skillsApplied: string[]
  skillsMissing: string[]
  thinking: string
  tools: ToolCallTrace[]
  usage: AiRunUsage | null
  /** 执行层回报的实际作答模型。 */
  model: string | null
  /** 本轮开始时请求的 spec（取自发送时的选择，不随之后的下拉变化）。 */
  requestedModel: string | null
}

/** off=未开启；requesting=等待浏览器授权；on=已授权并缓存；failed=被拒绝/超时/不可用。 */
export type PreviewLocationStatus = 'off' | 'requesting' | 'on' | 'failed'

const TODO_STATUS_CLASS: Record<AiPreviewTodoStatus, string> = {
  PENDING: 'bg-card-muted text-muted-foreground',
  IN_PROGRESS: 'bg-info-muted text-info-emphasis',
  COMPLETED: 'bg-success-muted text-success-emphasis',
  CANCELLED: 'bg-card-muted text-muted-foreground line-through',
}

const TOOL_STATE_CLASS: Record<ToolCallTrace['state'], string> = {
  running: 'text-info-emphasis',
  ok: 'text-success-emphasis',
  failed: 'text-danger-emphasis',
}

function RunLivePulse() {
  const isRunning = useAuiState((state) => state.thread.isRunning)
  if (!isRunning) return null
  return <span className="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-info" aria-hidden />
}

function RunThinkingPlaceholder() {
  const { t } = useTranslation('sites')
  const isRunning = useAuiState((state) => state.thread.isRunning)
  if (!isRunning) return null
  return (
    <p className="mb-2 flex items-center gap-1.5 text-xs text-muted-foreground">
      <span className="animate-pulse">{t('aiWorkspace.preview.thinking')}</span>
      <span className="inline-block h-1.5 w-1.5 animate-pulse rounded-full bg-info" aria-hidden />
    </p>
  )
}

/** 预览勾选行：checkbox + 共用 catalog 说明折叠。 */
function PreviewSkillRow({
  id,
  description,
  checked,
  onToggle,
}: {
  id: string
  description: string | null | undefined
  checked: boolean
  onToggle: () => void
}) {
  const { t } = useTranslation('sites')
  const [expanded, setExpanded] = useState(false)
  const text = skillCatalogDescription(id, description, t)

  return (
    <li>
      <div className="flex items-start gap-2">
        <input
          type="checkbox"
          className="admin-check mt-1"
          checked={checked}
          onChange={onToggle}
          aria-label={id}
        />
        <div className="min-w-0 flex-1">
          {text ? (
            <button
              type="button"
              className="flex w-full items-center gap-1 text-left"
              aria-expanded={expanded}
              onClick={() => setExpanded((value) => !value)}
            >
              <span className="min-w-0 flex-1 truncate font-mono text-xs text-foreground">{id}</span>
              <ChevronDown
                className={`h-3.5 w-3.5 shrink-0 text-muted-foreground transition-transform duration-150 ${
                  expanded ? 'rotate-180' : ''
                }`}
                aria-hidden
              />
            </button>
          ) : (
            <span className="block truncate font-mono text-xs text-foreground">{id}</span>
          )}
          {text && expanded ? (
            <p className="mt-1 text-xs leading-5 text-muted-foreground">{text}</p>
          ) : null}
        </div>
      </div>
    </li>
  )
}

function PreviewMcpSection({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const [open, setOpen] = useState(false)
  const runtimeQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'runtime'],
    queryFn: () => aiApi.getRuntime(agentId),
  })
  const servers = runtimeQuery.data?.status?.mcpServers ?? []

  return (
    <section className="rounded-xl border border-border bg-card p-3">
      <div className="mb-0 flex items-center gap-2">
        <button
          type="button"
          aria-expanded={open}
          className="flex min-w-0 flex-1 items-center justify-between gap-2 text-left"
          onClick={() => setOpen((value) => !value)}
        >
          <h4 className="font-medium text-foreground">{t('aiWorkspace.preview.mcp.title')}</h4>
          <ChevronDown
            className={`h-4 w-4 shrink-0 text-muted-foreground transition-transform duration-150 ${
              open ? 'rotate-180' : ''
            }`}
            aria-hidden
          />
        </button>
        <Link
          to={ROUTES.apps.agentRuntime(agentId)}
          className="shrink-0 text-[11px] text-primary underline-offset-2 hover:underline"
        >
          {t('aiWorkspace.preview.mcp.openRuntime')}
        </Link>
      </div>
      {open ? (
        <>
          <p className="mt-2 text-xs text-muted-foreground">{t('aiWorkspace.preview.mcp.hint')}</p>
          {runtimeQuery.isPending ? (
            <p className="mt-2 text-xs text-muted-foreground">{t('aiWorkspace.loading')}</p>
          ) : runtimeQuery.isError || runtimeQuery.data?.reachable === false ? (
            <p className="mt-2 text-xs text-danger-emphasis">{t('aiWorkspace.preview.mcp.error')}</p>
          ) : servers.length === 0 ? (
            <p className="mt-2 text-xs text-muted-foreground">{t('aiWorkspace.preview.mcp.empty')}</p>
          ) : (
            <ul className="mt-2 space-y-1">
              {servers.map((server) => (
                <li key={server.name} className="flex items-center justify-between gap-2 text-xs">
                  <span className="truncate font-mono text-foreground">{server.name}</span>
                  <span className="shrink-0 text-muted-foreground">
                    {t(server.enabled ? 'aiWorkspace.preview.mcp.enabled' : 'aiWorkspace.preview.mcp.disabled')}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </>
      ) : null}
    </section>
  )
}

export interface AgentAiDebugInspectorProps {
  agentId: number
  open: boolean
  onClose: () => void
  mode: 'CLI' | 'KEY'
  onChooseMode: (mode: 'CLI' | 'KEY') => void
  entries: AiPreviewModelEntry[]
  modelEntryId: number | null
  entryPicked: boolean
  onChooseEntry: (raw: string) => void
  modelPending: boolean
  namedMissing: boolean
  activeModel: string
  trace: RunTrace
  selectedSkills: string[]
  onToggleSkill: (id: string) => void
  todos: AiPreviewTodoItem[]
  onClearTodos: () => void
  locationStatus: PreviewLocationStatus
  onToggleLocation: (enable: boolean) => void
}

/**
 * 预览调试 Inspector：只放「本轮」调试参数（入口 / 定位 / skills / 运行轨迹 / todos），
 * 不写 Agent 持久化的 Prompt、Skills、MCP 默认值。
 * 始终挂载、关闭时隐藏，保持各区块折叠态与查询缓存；xl 以上贴右侧停靠，以下为右侧抽屉。
 */
export function AgentAiDebugInspector({
  agentId,
  open,
  onClose,
  mode,
  onChooseMode,
  entries,
  modelEntryId,
  entryPicked,
  onChooseEntry,
  modelPending,
  namedMissing,
  activeModel,
  trace,
  selectedSkills,
  onToggleSkill,
  todos,
  onClearTodos,
  locationStatus,
  onToggleLocation,
}: AgentAiDebugInspectorProps) {
  const { t } = useTranslation('sites')
  const [traceOpen, setTraceOpen] = useState(true)
  const [skillsOpen, setSkillsOpen] = useState(true)

  const skillsQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'preview', 'skills'],
    queryFn: () => aiApi.listPreviewSkills(agentId),
  })

  const requestedLabel = trace.requestedModel ? modelIdFromSpec(trace.requestedModel) : ''
  const servedLabel = trace.model ? modelIdFromSpec(trace.model) : ''
  const fellBack =
    trace.requestedModel != null && trace.model != null && !isSameModel(trace.requestedModel, trace.model)

  const handleKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === 'Escape') onClose()
  }

  return (
    <>
      {open ? (
        <button
          type="button"
          tabIndex={-1}
          aria-label={t('aiWorkspace.preview.inspector.close')}
          className="admin-overlay fixed inset-0 z-40 xl:hidden"
          onClick={onClose}
        />
      ) : null}
      <aside
        aria-label={t('aiWorkspace.preview.inspector.title')}
        onKeyDown={handleKeyDown}
        className={
          open
            ? 'fixed inset-y-0 right-0 z-50 flex w-[min(22rem,100vw)] flex-col border-l border-border bg-card shadow-admin-overlay xl:static xl:z-auto xl:h-full xl:w-80 xl:shrink-0 xl:rounded-xl xl:border xl:shadow-none'
            : 'hidden'
        }
      >
        <header className="flex items-start justify-between gap-2 border-b border-border px-3 py-3">
          <div className="min-w-0">
            <h3 className="text-sm font-medium text-foreground">{t('aiWorkspace.preview.inspector.title')}</h3>
            <p className="mt-1 text-xs leading-5 text-muted-foreground">
              {t('aiWorkspace.preview.inspector.scopeHint')}
            </p>
          </div>
          <button
            type="button"
            aria-label={t('aiWorkspace.preview.inspector.close')}
            title={t('aiWorkspace.preview.inspector.close')}
            className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md text-muted-foreground transition hover:bg-card-muted hover:text-foreground"
            onClick={onClose}
          >
            <X className="h-4 w-4" aria-hidden />
          </button>
        </header>
        <div className="min-h-0 flex-1 space-y-4 overflow-y-auto p-3 text-sm">
          <section className="rounded-xl border border-border bg-card p-3">
            <div className="flex flex-wrap items-center gap-2">
              {(['CLI', 'KEY'] as const).map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={mode === value}
                  className={
                    mode === value
                      ? 'rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground'
                      : 'rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground'
                  }
                  onClick={() => onChooseMode(value)}
                >
                  {value === 'CLI' ? t('aiWorkspace.preview.modeCli') : t('aiWorkspace.preview.modeKey')}
                </button>
              ))}
            </div>
            {entries.length > 0 ? (
              <label className="mt-3 flex flex-col gap-1.5 text-xs text-muted-foreground">
                {t('aiWorkspace.preview.nextEntry')}
                <select
                  aria-label={t('aiWorkspace.preview.nextEntry')}
                  value={modelEntryId ?? ''}
                  onChange={(event) => onChooseEntry(event.target.value)}
                  className="w-full rounded-lg border border-border bg-card px-2 py-1.5 text-xs text-foreground"
                >
                  <option value="">{t('aiWorkspace.preview.entryDefault')}</option>
                  {entries.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.displayName}
                    </option>
                  ))}
                  {modelEntryId != null && !entryPicked ? (
                    <option value={modelEntryId}>{t('aiWorkspace.preview.entryUnavailable')}</option>
                  ) : null}
                </select>
              </label>
            ) : null}
            <p className="mt-3 text-sm text-muted-foreground">
              {modelPending
                ? t('aiWorkspace.loading')
                : namedMissing
                  ? t('aiWorkspace.preview.errors.MODEL_ENTRY_UNAVAILABLE')
                  : activeModel
                    ? t('aiWorkspace.preview.using', { model: activeModel })
                    : t('aiWorkspace.preview.noEntry')}
            </p>
            <p className="mt-2 text-xs leading-5 text-muted-foreground">{t('aiWorkspace.preview.hint')}</p>
          </section>

          <section className="rounded-xl border border-border bg-card p-3">
            <label className="flex items-start justify-between gap-3">
              <span className="min-w-0">
                <span className="block font-medium text-foreground">
                  {t('aiWorkspace.preview.inspector.location.title')}
                </span>
                <span className="mt-1 block text-xs leading-5 text-muted-foreground">
                  {t('aiWorkspace.preview.inspector.location.hint')}
                </span>
              </span>
              <input
                type="checkbox"
                role="switch"
                className="admin-switch mt-0.5"
                checked={locationStatus === 'on' || locationStatus === 'requesting'}
                onChange={(event) => onToggleLocation(event.target.checked)}
                aria-label={t('aiWorkspace.preview.inspector.location.title')}
              />
            </label>
            {locationStatus !== 'off' ? (
              <p
                className={`mt-2 text-xs ${
                  locationStatus === 'failed' ? 'text-warning-emphasis' : 'text-muted-foreground'
                }`}
                role={locationStatus === 'failed' ? 'alert' : 'status'}
              >
                {t(`aiWorkspace.preview.inspector.location.status.${locationStatus}`)}
              </p>
            ) : null}
          </section>

          <section className="rounded-xl border border-border bg-card p-3">
            <button
              type="button"
              aria-expanded={traceOpen}
              className="mb-2 flex w-full items-center justify-between gap-2 text-left"
              onClick={() => setTraceOpen((value) => !value)}
            >
              <h4 className="font-medium text-foreground">{t('aiWorkspace.preview.run.title')}</h4>
              <ChevronDown
                className={`h-4 w-4 shrink-0 text-muted-foreground transition-transform duration-150 ${
                  traceOpen ? 'rotate-180' : ''
                }`}
                aria-hidden
              />
            </button>
            {traceOpen ? (
              <>
                {requestedLabel ? (
                  <p className="text-xs text-muted-foreground">
                    {t('aiWorkspace.preview.run.requestedModel')}
                    <span className="ml-1 break-all font-mono text-foreground">{requestedLabel}</span>
                  </p>
                ) : null}
                {servedLabel ? (
                  <p className="mb-2 text-xs text-muted-foreground">
                    {t('aiWorkspace.preview.run.actualModel')}
                    <span className="ml-1 break-all font-mono text-foreground">{servedLabel}</span>
                  </p>
                ) : null}
                {fellBack ? (
                  <p className="mb-2 text-xs text-warning-emphasis">
                    {t('aiWorkspace.preview.run.servedModel', { model: servedLabel })}
                  </p>
                ) : null}
                <p className="text-xs text-muted-foreground">{t('aiWorkspace.preview.run.skillsApplied')}</p>
                <p className="mb-2 font-mono text-xs text-foreground">
                  {trace.skillsApplied.length ? trace.skillsApplied.join(', ') : '—'}
                </p>
                {trace.skillsMissing.length ? (
                  <p className="mb-2 text-xs text-warning-emphasis">
                    {t('aiWorkspace.preview.run.skillsMissing', { ids: trace.skillsMissing.join(', ') })}
                  </p>
                ) : null}
                {trace.thinking ? (
                  <>
                    <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                      {t('aiWorkspace.preview.run.thinking')}
                      <RunLivePulse />
                    </p>
                    <div className="mb-2 mt-1 max-h-40 overflow-y-auto whitespace-pre-wrap break-words rounded-lg bg-card-muted px-2 py-1.5 text-xs leading-5 text-muted-foreground">
                      {trace.thinking}
                    </div>
                  </>
                ) : (
                  <RunThinkingPlaceholder />
                )}
                <p className="text-xs text-muted-foreground">{t('aiWorkspace.preview.run.tools')}</p>
                {trace.tools.length === 0 ? (
                  <p className="text-xs text-muted-foreground">{t('aiWorkspace.preview.run.noTools')}</p>
                ) : (
                  <ul className="space-y-1">
                    {trace.tools.map((tool) => (
                      <li key={tool.toolCallId || tool.toolName} className="text-xs">
                        <div className="flex justify-between gap-2">
                          <span className="flex min-w-0 items-center gap-1.5 truncate font-mono text-foreground">
                            {tool.state === 'running' ? (
                              <span className="inline-block h-1.5 w-1.5 shrink-0 animate-pulse rounded-full bg-info" aria-hidden />
                            ) : null}
                            <span className="truncate">{tool.toolName}</span>
                          </span>
                          <span className={TOOL_STATE_CLASS[tool.state]}>
                            {t(`aiWorkspace.preview.run.toolState.${tool.state}`)}
                          </span>
                        </div>
                        {tool.summary ? (
                          <p className="mt-0.5 truncate text-muted-foreground">
                            {t('aiWorkspace.preview.run.searchDetail', {
                              query: tool.summary.query,
                              count: tool.summary.hitCount,
                            })}
                          </p>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                )}
                {trace.usage ? (
                  <div className="mt-2">
                    <AiRunUsageLine usage={trace.usage} />
                  </div>
                ) : null}
              </>
            ) : null}
          </section>

          <PreviewMcpSection agentId={agentId} />

          <section className="rounded-xl border border-border bg-card p-3">
            <button
              type="button"
              aria-expanded={skillsOpen}
              className="mb-2 flex w-full items-center justify-between gap-2 text-left"
              onClick={() => setSkillsOpen((value) => !value)}
            >
              <h4 className="font-medium text-foreground">{t('aiWorkspace.preview.skills.title')}</h4>
              <ChevronDown
                className={`h-4 w-4 shrink-0 text-muted-foreground transition-transform duration-150 ${
                  skillsOpen ? 'rotate-180' : ''
                }`}
                aria-hidden
              />
            </button>
            {skillsOpen ? (
              <>
                <p className="mb-2 text-xs text-muted-foreground">{t('aiWorkspace.preview.skills.hint')}</p>
                {skillsQuery.isPending ? (
                  <p className="text-muted-foreground">{t('aiWorkspace.loading')}</p>
                ) : skillsQuery.isError ? (
                  <div className="text-danger-emphasis">
                    <p>{t('aiWorkspace.preview.skills.error')}</p>
                    <button type="button" className="mt-1 underline" onClick={() => void skillsQuery.refetch()}>
                      {t('aiWorkspace.retry')}
                    </button>
                  </div>
                ) : skillsQuery.data.length === 0 ? (
                  <p className="text-muted-foreground">{t('aiWorkspace.preview.skills.empty')}</p>
                ) : (
                  <ul className="space-y-1.5">
                    {skillsQuery.data.map((skill) => (
                      <PreviewSkillRow
                        key={skill.id}
                        id={skill.id}
                        description={skill.description}
                        checked={selectedSkills.includes(skill.id)}
                        onToggle={() => onToggleSkill(skill.id)}
                      />
                    ))}
                  </ul>
                )}
              </>
            ) : null}
          </section>

          <section className="rounded-xl border border-border bg-card p-3">
            <div className="mb-2 flex items-center justify-between">
              <h4 className="font-medium text-foreground">{t('aiWorkspace.preview.todos.title')}</h4>
              {todos.length ? (
                <button type="button" className="text-xs underline" onClick={onClearTodos}>
                  {t('aiWorkspace.preview.todos.clear')}
                </button>
              ) : null}
            </div>
            {todos.length === 0 ? (
              <p className="text-xs text-muted-foreground">{t('aiWorkspace.preview.todos.empty')}</p>
            ) : (
              <ul className="space-y-1">
                {todos.map((todo) => (
                  <li key={todo.id} className="flex items-start justify-between gap-2 text-xs">
                    <span className="text-foreground">{todo.title}</span>
                    <span className={`shrink-0 rounded px-1.5 py-0.5 ${TODO_STATUS_CLASS[todo.status]}`}>
                      {t(`aiWorkspace.preview.todos.status.${todo.status}`)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            <p className="mt-2 text-xs text-muted-foreground">{t('aiWorkspace.preview.todos.hint')}</p>
          </section>
        </div>
      </aside>
    </>
  )
}
