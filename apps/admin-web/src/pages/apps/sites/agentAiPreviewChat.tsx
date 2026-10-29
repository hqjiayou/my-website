import {
  AssistantRuntimeProvider,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  useLocalRuntime,
} from '@assistant-ui/react'
import { useQuery } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import type {
  AiModelEntry,
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

const EMPTY_TRACE: RunTrace = {
  skillsApplied: [],
  skillsMissing: [],
  thinking: '',
  tools: [],
  usage: null,
  model: null,
}

function entryForMode(entries: AiModelEntry[], agentId: number, mode: 'CLI' | 'KEY') {
  const enabled = entries.filter(
    (entry) => entry.enabled && (entry.mode ?? 'KEY') === mode && (entry.agentId == null || entry.agentId === agentId),
  )
  return (
    enabled.find((entry) => entry.agentId === agentId && entry.defaultEntry) ??
    enabled.find((entry) => entry.agentId == null && entry.defaultEntry) ??
    null
  )
}

type RunError = { errorCode: string; message?: string }

const KNOWN_ERROR_CODES = new Set([
  'EXTERNAL_DEPENDENCY_BLOCKED',
  'MCP_UNAVAILABLE',
  'AGENT_BUDGET_EXCEEDED',
  'REQUEST_FAILED',
  'SYSTEM_ERROR',
])

const TODO_STATUS_CLASS: Record<AiPreviewTodoStatus, string> = {
  PENDING: 'bg-slate-100 text-slate-600',
  IN_PROGRESS: 'bg-sky-100 text-sky-700',
  COMPLETED: 'bg-emerald-100 text-emerald-700',
  CANCELLED: 'bg-slate-100 text-slate-400 line-through',
}

const TOOL_STATE_CLASS: Record<ToolCallTrace['state'], string> = {
  running: 'text-sky-700',
  ok: 'text-emerald-700',
  failed: 'text-rose-700',
}

/** adapter 每轮读取的最新选择；闭包持有，避免选择变化时重建 runtime（会丢失预览线程）。 */
function createPreviewSelection() {
  let skills: string[] = []
  let todos: AiPreviewTodoItem[] = []
  let mode: 'CLI' | 'KEY' = 'CLI'
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
  const entriesQuery = useQuery({
    queryKey: ['ai', 'model-entries'],
    queryFn: () => aiApi.listModelEntries(),
  })
  const activeEntry = entryForMode(entriesQuery.data ?? [], agentId, mode)
  const activeModel = activeEntry
    ? activeEntry.mode === 'CLI'
      ? modelIdFromSpec(activeEntry.modelSpec)
      : activeEntry.modelSpec
    : ''
  const servedModel = trace.model ? modelIdFromSpec(trace.model) : ''

  useEffect(() => {
    if (modeChosen || !entriesQuery.data) return
    const next = entryForMode(entriesQuery.data, agentId, 'CLI') ? 'CLI' : 'KEY'
    selection.setMode(next)
    setMode(next)
    setModeChosen(true)
  }, [agentId, entriesQuery.data, modeChosen, selection])

  const chooseMode = (next: 'CLI' | 'KEY') => {
    selection.setMode(next)
    setMode(next)
    setModeChosen(true)
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
        onAgentEvent: (event) => {
          if (event.type === 'error') {
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
                ? 'rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-medium text-white'
                : 'rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs font-medium text-slate-700'
            }
            onClick={() => chooseMode(value)}
          >
            {value === 'CLI' ? t('aiWorkspace.preview.modeCli') : t('aiWorkspace.preview.modeKey')}
          </button>
        ))}
      </div>
      <p className="mb-3 text-sm text-slate-600">
        {entriesQuery.isPending
          ? t('aiWorkspace.loading')
          : activeModel
            ? t('aiWorkspace.preview.using', { model: activeModel })
            : t('aiWorkspace.preview.noEntry')}
      </p>
      <p className="mb-3 text-sm text-slate-600">{t('aiWorkspace.preview.hint')}</p>
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_18rem]">
        <ThreadPrimitive.Root className="flex h-[28rem] flex-col overflow-hidden rounded-xl border border-slate-200 bg-slate-50">
          <ThreadPrimitive.Viewport className="flex-1 overflow-y-auto px-3 py-3">
            <ThreadPrimitive.Empty>
              <p className="px-2 text-sm text-slate-500">{t('aiWorkspace.preview.empty')}</p>
            </ThreadPrimitive.Empty>
            <ThreadPrimitive.Messages>
              {() => (
                <MessagePrimitive.Root className="mb-3 rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800">
                  <MessagePrimitive.Content />
                </MessagePrimitive.Root>
              )}
            </ThreadPrimitive.Messages>
          </ThreadPrimitive.Viewport>
          {runError ? (
            <div role="alert" className="border-t border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-800">
              <p>
                {t(
                  `aiWorkspace.preview.errors.${
                    KNOWN_ERROR_CODES.has(runError.errorCode) ? runError.errorCode : 'SYSTEM_ERROR'
                  }`,
                )}
                <span className="ml-1 font-mono text-rose-500">({runError.errorCode})</span>
              </p>
              {runError.message ? <p className="mt-0.5 break-all text-rose-600">{runError.message}</p> : null}
            </div>
          ) : null}
          <ComposerPrimitive.Root className="flex gap-2 border-t border-slate-200 bg-white p-3">
            <ComposerPrimitive.Input
              className="min-h-10 flex-1 resize-none rounded-lg border border-slate-200 px-3 py-2 text-sm"
              placeholder={t('aiWorkspace.preview.placeholder')}
            />
            <ComposerPrimitive.Send className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white">
              {t('aiWorkspace.preview.send')}
            </ComposerPrimitive.Send>
          </ComposerPrimitive.Root>
        </ThreadPrimitive.Root>

        <aside className="space-y-4 text-sm">
          <section className="rounded-xl border border-slate-200 bg-white p-3">
            <h4 className="mb-2 font-medium text-slate-800">{t('aiWorkspace.preview.skills.title')}</h4>
            <p className="mb-2 text-xs text-slate-500">{t('aiWorkspace.preview.skills.hint')}</p>
            {skillsQuery.isPending ? (
              <p className="text-slate-500">{t('aiWorkspace.loading')}</p>
            ) : skillsQuery.isError ? (
              <div className="text-rose-700">
                <p>{t('aiWorkspace.preview.skills.error')}</p>
                <button type="button" className="mt-1 underline" onClick={() => void skillsQuery.refetch()}>
                  {t('aiWorkspace.retry')}
                </button>
              </div>
            ) : skillsQuery.data.length === 0 ? (
              <p className="text-slate-500">{t('aiWorkspace.preview.skills.empty')}</p>
            ) : (
              <ul className="space-y-1.5">
                {skillsQuery.data.map((skill) => (
                  <li key={skill.id}>
                    <label className="flex cursor-pointer items-start gap-2">
                      <input
                        type="checkbox"
                        className="mt-0.5"
                        checked={selectedSkills.includes(skill.id)}
                        onChange={() => toggleSkill(skill.id)}
                      />
                      <span>
                        <span className="font-mono text-xs text-slate-800">{skill.id}</span>
                        {skill.description ? (
                          <span className="block text-xs text-slate-500">{skill.description}</span>
                        ) : null}
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="rounded-xl border border-slate-200 bg-white p-3">
            <h4 className="mb-2 font-medium text-slate-800">{t('aiWorkspace.preview.run.title')}</h4>
            {servedModel && activeModel && servedModel !== activeModel ? (
              <p className="mb-2 text-xs text-amber-700">
                {t('aiWorkspace.preview.run.servedModel', { model: servedModel })}
              </p>
            ) : null}
            <p className="text-xs text-slate-500">{t('aiWorkspace.preview.run.skillsApplied')}</p>
            <p className="mb-2 font-mono text-xs text-slate-800">
              {trace.skillsApplied.length ? trace.skillsApplied.join(', ') : '—'}
            </p>
            {trace.skillsMissing.length ? (
              <p className="mb-2 text-xs text-amber-700">
                {t('aiWorkspace.preview.run.skillsMissing', { ids: trace.skillsMissing.join(', ') })}
              </p>
            ) : null}
            {trace.thinking ? (
              <>
                <p className="text-xs text-slate-500">{t('aiWorkspace.preview.run.thinking')}</p>
                <div className="mb-2 mt-1 max-h-40 overflow-y-auto whitespace-pre-wrap break-words rounded-lg bg-slate-50 px-2 py-1.5 text-xs leading-5 text-slate-600">
                  {trace.thinking}
                </div>
              </>
            ) : null}
            <p className="text-xs text-slate-500">{t('aiWorkspace.preview.run.tools')}</p>
            {trace.tools.length === 0 ? (
              <p className="text-xs text-slate-400">{t('aiWorkspace.preview.run.noTools')}</p>
            ) : (
              <ul className="space-y-1">
                {trace.tools.map((tool) => (
                  <li key={tool.toolCallId || tool.toolName} className="text-xs">
                    <div className="flex justify-between gap-2">
                      <span className="truncate font-mono text-slate-800">{tool.toolName}</span>
                      <span className={TOOL_STATE_CLASS[tool.state]}>
                        {t(`aiWorkspace.preview.run.toolState.${tool.state}`)}
                      </span>
                    </div>
                    {tool.summary ? (
                      <p className="mt-0.5 truncate text-slate-500">
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

          <section className="rounded-xl border border-slate-200 bg-white p-3">
            <div className="mb-2 flex items-center justify-between">
              <h4 className="font-medium text-slate-800">{t('aiWorkspace.preview.todos.title')}</h4>
              {todos.length ? (
                <button type="button" className="text-xs underline" onClick={() => updateTodos([])}>
                  {t('aiWorkspace.preview.todos.clear')}
                </button>
              ) : null}
            </div>
            {todos.length === 0 ? (
              <p className="text-xs text-slate-400">{t('aiWorkspace.preview.todos.empty')}</p>
            ) : (
              <ul className="space-y-1">
                {todos.map((todo) => (
                  <li key={todo.id} className="flex items-start justify-between gap-2 text-xs">
                    <span className="text-slate-800">{todo.title}</span>
                    <span className={`shrink-0 rounded px-1.5 py-0.5 ${TODO_STATUS_CLASS[todo.status]}`}>
                      {t(`aiWorkspace.preview.todos.status.${todo.status}`)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
            <p className="mt-2 text-xs text-slate-400">{t('aiWorkspace.preview.todos.hint')}</p>
          </section>
        </aside>
      </div>
    </AssistantRuntimeProvider>
  )
}
