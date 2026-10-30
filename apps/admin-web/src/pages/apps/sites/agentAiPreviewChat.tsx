import {
  AssistantRuntimeProvider,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  useAuiState,
  useLocalRuntime,
} from '@assistant-ui/react'
import { useQuery } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import type {
  AiPreviewAgentEvent,
  AiPreviewTodoItem,
  AiPreviewTodoStatus,
  AiRunUsage,
  AiSearchSummary,
} from '@/types/ai'

import { modelIdFromSpec } from '@/utils/modelSpec'
import { createJavaPreviewAdapter } from './agentAiPreviewAdapter'
import { AiRunUsageLine } from './agentAiRunMeta'

interface ToolCallTrace {
  toolCallId: string
  toolName: string
  state: 'running' | 'ok' | 'failed'
  summary?: AiSearchSummary
}

interface RunTrace {
  skillsApplied: string[]
  skillsMissing: string[]
  thinking: string
  tools: ToolCallTrace[]
  usage: AiRunUsage | null
  model: string | null
}

function PreviewAssistantLabel() {
  const spec = useAuiState((state) => {
    if (state.message.role !== 'assistant') return ''
    const value = state.message.metadata?.custom?.requestedModel
    return typeof value === 'string' ? value : ''
  })
  if (!spec) return null
  return <p className="mb-1 font-mono text-[11px] text-muted-foreground">{spec}</p>
}

const EMPTY_TRACE: RunTrace = {
  skillsApplied: [],
  skillsMissing: [],
  thinking: '',
  tools: [],
  usage: null,
  model: null,
}

type RunError = { errorCode: string; message?: string }

const KNOWN_ERROR_CODES = new Set([
  'EXTERNAL_DEPENDENCY_BLOCKED',
  'MCP_UNAVAILABLE',
  'AGENT_BUDGET_EXCEEDED',
  'REQUEST_FAILED',
  'SYSTEM_ERROR',
  'MODEL_ENTRY_UNAVAILABLE',
])

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

/** adapter 每轮读取的最新选择；闭包持有，避免选择变化时重建 runtime（会丢失预览线程）。 */
function createPreviewSelection() {
  let skills: string[] = []
  let todos: AiPreviewTodoItem[] = []
  let mode: 'CLI' | 'KEY' = 'CLI'
  const entryByMode: Record<'CLI' | 'KEY', number | null> = { CLI: null, KEY: null }
  let requestedSpec = ''
  return {
    getSkills: () => skills,
    setSkills: (next: string[]) => {
      skills = next
    },
    getTodos: () => todos,
    setTodos: (next: AiPreviewTodoItem[]) => {
      todos = next
    },
    getMode: () => mode,
    setMode: (next: 'CLI' | 'KEY') => {
      mode = next
    },
    getModelEntryId: () => entryByMode[mode],
    setModelEntryId: (id: number | null) => {
      entryByMode[mode] = id
    },
    entryFor: (next: 'CLI' | 'KEY') => entryByMode[next],
    setRequestedSpec: (spec: string) => {
      requestedSpec = spec
    },
    getRequestedSpec: () => requestedSpec,
  }
}

function applyAgentEvent(trace: RunTrace, event: AiPreviewAgentEvent): RunTrace {
  switch (event.type) {
    case 'start':
      return {
        skillsApplied: event.skillsApplied,
        skillsMissing: event.skillsMissing,
        thinking: '',
        tools: [],
        usage: null,
        model: null,
      }
    case 'thinking':
      return { ...trace, thinking: trace.thinking + event.delta }
    case 'done':
      return { ...trace, usage: event.usage, model: event.model }
    case 'toolStart':
      return {
        ...trace,
        tools: [...trace.tools, { toolCallId: event.toolCallId, toolName: event.toolName, state: 'running' }],
      }
    case 'toolResult': {
      const state = event.ok ? 'ok' : 'failed'
      const known = trace.tools.some((tool) => tool.toolCallId === event.toolCallId)
      return {
        ...trace,
        tools: known
          ? trace.tools.map((tool) =>
              tool.toolCallId === event.toolCallId
                ? { ...tool, state, summary: event.summary ?? tool.summary }
                : tool,
            )
          : [
              ...trace.tools,
              { toolCallId: event.toolCallId, toolName: event.toolName, state, summary: event.summary },
            ],
      }
    }
    default:
      return trace
  }
}

export function AgentAiPreviewChat({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const [selectedSkills, setSelectedSkills] = useState<string[]>([])
  const [todos, setTodos] = useState<AiPreviewTodoItem[]>([])
  const [trace, setTrace] = useState<RunTrace>(EMPTY_TRACE)
  const [runError, setRunError] = useState<RunError | null>(null)
  const [selection] = useState(createPreviewSelection)
  const [mode, setMode] = useState<'CLI' | 'KEY'>('CLI')
  const [modeChosen, setModeChosen] = useState(false)
  const [modelEntryId, setModelEntryId] = useState<number | null>(null)
  // 首次挂载不带 mode，拿服务端默认判定；用户切换 CLI/KEY 后带 mode 重查，与 previewStream 同一路径回填。
  const modelQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'preview', 'model', modeChosen ? mode : 'default'],
    queryFn: () => aiApi.getPreviewModel(agentId, modeChosen ? mode : undefined),
  })
  const entries = modelQuery.data?.entries ?? []
  const picked = entries.find((item) => item.id === modelEntryId) ?? null
  const namedMissing = modelQuery.data != null && modelEntryId != null && picked == null
  const activeSpec = namedMissing
    ? ''
    : (picked?.modelSpec ?? (modelQuery.data?.available ? modelQuery.data.modelSpec : '') ?? '')
  const activeModel = activeSpec ? (mode === 'CLI' ? modelIdFromSpec(activeSpec) : activeSpec) : ''
  useEffect(() => {
    selection.setRequestedSpec(activeSpec)
  }, [activeSpec, selection])
  const servedModel = trace.model ? modelIdFromSpec(trace.model) : ''

  useEffect(() => {
    if (modeChosen || !modelQuery.data) return
    selection.setMode(modelQuery.data.mode)
    setMode(modelQuery.data.mode)
    setModeChosen(true)
  }, [modeChosen, modelQuery.data, selection])

  const chooseMode = (next: 'CLI' | 'KEY') => {
    selection.setMode(next)
    setMode(next)
    setModeChosen(true)
    setModelEntryId(selection.entryFor(next))
  }

  const chooseEntry = (raw: string) => {
    const next = raw ? Number(raw) : null
    selection.setModelEntryId(next)
    setModelEntryId(next)
  }

  const updateTodos = useCallback(
    (next: AiPreviewTodoItem[]) => {
      selection.setTodos(next)
      setTodos(next)
    },
    [selection],
  )

  const toggleSkill = (id: string) => {
    const next = selectedSkills.includes(id)
      ? selectedSkills.filter((item) => item !== id)
      : [...selectedSkills, id]
    selection.setSkills(next)
    setSelectedSkills(next)
  }

  const adapter = useMemo(
    () =>
      createJavaPreviewAdapter(agentId, {
        getSkills: selection.getSkills,
        getTodos: selection.getTodos,
        getMode: selection.getMode,
        getModelEntryId: selection.getModelEntryId,
        getRequestedSpec: selection.getRequestedSpec,
        onAgentEvent: (event) => {
          if (event.type === 'error') {
            if (event.errorCode === 'MODEL_ENTRY_UNAVAILABLE') {
              selection.setModelEntryId(null)
              setModelEntryId(null)
            }
            setRunError({ errorCode: event.errorCode, message: event.message })
            return
          }
          if (event.type === 'start') setRunError(null)
          if (event.type === 'done') updateTodos(event.todos)
          setTrace((current) => applyAgentEvent(current, event))
        },
      }),
    [agentId, selection, updateTodos],
  )
  const runtime = useLocalRuntime(adapter)

  const skillsQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'preview', 'skills'],
    queryFn: () => aiApi.listPreviewSkills(agentId),
  })

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <div className="mb-3 flex flex-wrap items-center gap-2">
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
            onClick={() => chooseMode(value)}
          >
            {value === 'CLI' ? t('aiWorkspace.preview.modeCli') : t('aiWorkspace.preview.modeKey')}
          </button>
        ))}
        {entries.length > 0 ? (
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            {t('aiWorkspace.preview.nextEntry')}
            <select
              aria-label={t('aiWorkspace.preview.nextEntry')}
              value={modelEntryId ?? ''}
              onChange={(event) => chooseEntry(event.target.value)}
              className="max-w-[16rem] rounded-lg border border-border bg-card px-2 py-1.5 text-xs text-foreground"
            >
              <option value="">{t('aiWorkspace.preview.entryDefault')}</option>
              {entries.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.displayName}
                </option>
              ))}
              {modelEntryId != null && !picked ? (
                <option value={modelEntryId}>{t('aiWorkspace.preview.entryUnavailable')}</option>
              ) : null}
            </select>
          </label>
        ) : null}
      </div>
      <p className="mb-3 text-sm text-muted-foreground">
        {modelQuery.isPending
          ? t('aiWorkspace.loading')
          : namedMissing
            ? t('aiWorkspace.preview.errors.MODEL_ENTRY_UNAVAILABLE')
            : activeModel
              ? t('aiWorkspace.preview.using', { model: activeModel })
              : t('aiWorkspace.preview.noEntry')}
      </p>
      <p className="mb-3 text-sm text-muted-foreground">{t('aiWorkspace.preview.hint')}</p>
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_18rem]">
        <ThreadPrimitive.Root className="flex h-[28rem] flex-col overflow-hidden rounded-xl border border-border bg-card-muted">
          <ThreadPrimitive.Viewport className="flex-1 overflow-y-auto px-3 py-3">
            <ThreadPrimitive.Empty>
              <p className="px-2 text-sm text-muted-foreground">{t('aiWorkspace.preview.empty')}</p>
            </ThreadPrimitive.Empty>
            <ThreadPrimitive.Messages>
              {() => (
                <MessagePrimitive.Root className="mb-3 rounded-lg border border-border bg-card px-3 py-2 text-sm text-foreground">
                  <PreviewAssistantLabel />
                  <MessagePrimitive.Content />
                </MessagePrimitive.Root>
              )}
            </ThreadPrimitive.Messages>
          </ThreadPrimitive.Viewport>
          {runError ? (
            <div role="alert" className="border-t border-danger/35 bg-danger-muted px-3 py-2 text-xs text-danger-emphasis">
              <p>
                {t(
                  `aiWorkspace.preview.errors.${
                    KNOWN_ERROR_CODES.has(runError.errorCode) ? runError.errorCode : 'SYSTEM_ERROR'
                  }`,
                )}
                <span className="ml-1 font-mono text-danger">({runError.errorCode})</span>
              </p>
              {runError.message ? <p className="mt-0.5 break-all text-danger-emphasis">{runError.message}</p> : null}
            </div>
          ) : null}
          <ComposerPrimitive.Root className="flex gap-2 border-t border-border bg-card p-3">
            <ComposerPrimitive.Input
              className="min-h-10 flex-1 resize-none rounded-lg border border-border px-3 py-2 text-sm"
              placeholder={t('aiWorkspace.preview.placeholder')}
            />
            <ComposerPrimitive.Send className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground">
              {t('aiWorkspace.preview.send')}
            </ComposerPrimitive.Send>
          </ComposerPrimitive.Root>
        </ThreadPrimitive.Root>

        <aside className="space-y-4 text-sm">
          <section className="rounded-xl border border-border bg-card p-3">
            <h4 className="mb-2 font-medium text-foreground">{t('aiWorkspace.preview.skills.title')}</h4>
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
                  <li key={skill.id}>
                    <label className="flex cursor-pointer items-start gap-2">
                      <input
                        type="checkbox"
                        className="admin-check mt-1"
                        checked={selectedSkills.includes(skill.id)}
                        onChange={() => toggleSkill(skill.id)}
                      />
                      <span>
                        <span className="font-mono text-xs text-foreground">{skill.id}</span>
                        {skill.description ? (
                          <span className="block text-xs text-muted-foreground">{skill.description}</span>
                        ) : null}
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="rounded-xl border border-border bg-card p-3">
            <h4 className="mb-2 font-medium text-foreground">{t('aiWorkspace.preview.run.title')}</h4>
            {servedModel && activeModel && servedModel !== activeModel ? (
              <p className="mb-2 text-xs text-warning-emphasis">
                {t('aiWorkspace.preview.run.servedModel', { model: servedModel })}
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
                <p className="text-xs text-muted-foreground">{t('aiWorkspace.preview.run.thinking')}</p>
                <div className="mb-2 mt-1 max-h-40 overflow-y-auto whitespace-pre-wrap break-words rounded-lg bg-card-muted px-2 py-1.5 text-xs leading-5 text-muted-foreground">
                  {trace.thinking}
                </div>
              </>
            ) : null}
            <p className="text-xs text-muted-foreground">{t('aiWorkspace.preview.run.tools')}</p>
            {trace.tools.length === 0 ? (
              <p className="text-xs text-muted-foreground">{t('aiWorkspace.preview.run.noTools')}</p>
            ) : (
              <ul className="space-y-1">
                {trace.tools.map((tool) => (
                  <li key={tool.toolCallId || tool.toolName} className="text-xs">
                    <div className="flex justify-between gap-2">
                      <span className="truncate font-mono text-foreground">{tool.toolName}</span>
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
          </section>

          <section className="rounded-xl border border-border bg-card p-3">
            <div className="mb-2 flex items-center justify-between">
              <h4 className="font-medium text-foreground">{t('aiWorkspace.preview.todos.title')}</h4>
              {todos.length ? (
                <button type="button" className="text-xs underline" onClick={() => updateTodos([])}>
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
        </aside>
      </div>
    </AssistantRuntimeProvider>
  )
}
