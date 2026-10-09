import {
  AssistantRuntimeProvider,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  useLocalRuntime,
} from '@assistant-ui/react'
import { useQuery } from '@tanstack/react-query'
import { useCallback, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import type { AiPreviewAgentEvent, AiPreviewTodoItem, AiPreviewTodoStatus, AiRunUsage } from '@/types/ai'

import { createJavaPreviewAdapter } from './siteAiPreviewAdapter'
import { AiRunUsageLine } from './siteAiRunMeta'

interface ToolCallTrace {
  toolCallId: string
  toolName: string
  state: 'running' | 'ok' | 'failed'
}

interface RunTrace {
  skillsApplied: string[]
  skillsMissing: string[]
  tools: ToolCallTrace[]
  usage: AiRunUsage | null
}

const EMPTY_TRACE: RunTrace = { skillsApplied: [], skillsMissing: [], tools: [], usage: null }

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
  return {
    getSkills: () => skills,
    setSkills: (next: string[]) => {
      skills = next
    },
    getTodos: () => todos,
    setTodos: (next: AiPreviewTodoItem[]) => {
      todos = next
    },
  }
}

function applyAgentEvent(trace: RunTrace, event: AiPreviewAgentEvent): RunTrace {
  switch (event.type) {
    case 'start':
      return { skillsApplied: event.skillsApplied, skillsMissing: event.skillsMissing, tools: [], usage: null }
    case 'done':
      return { ...trace, usage: event.usage }
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
          ? trace.tools.map((tool) => (tool.toolCallId === event.toolCallId ? { ...tool, state } : tool))
          : [...trace.tools, { toolCallId: event.toolCallId, toolName: event.toolName, state }],
      }
    }
    default:
      return trace
  }
}

export function SiteAiPreviewChat({ siteId }: { siteId: number }) {
  const { t } = useTranslation('sites')
  const [selectedSkills, setSelectedSkills] = useState<string[]>([])
  const [todos, setTodos] = useState<AiPreviewTodoItem[]>([])
  const [trace, setTrace] = useState<RunTrace>(EMPTY_TRACE)
  const [runError, setRunError] = useState<RunError | null>(null)
  const [selection] = useState(createPreviewSelection)

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
      createJavaPreviewAdapter(siteId, {
        getSkills: selection.getSkills,
        getTodos: selection.getTodos,
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
    [siteId, selection, updateTodos],
  )
  const runtime = useLocalRuntime(adapter)

  const skillsQuery = useQuery({
    queryKey: ['sites', siteId, 'ai', 'preview', 'skills'],
    queryFn: () => aiApi.listPreviewSkills(siteId),
  })

  return (
    <AssistantRuntimeProvider runtime={runtime}>
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
            <p className="text-xs text-slate-500">{t('aiWorkspace.preview.run.skillsApplied')}</p>
            <p className="mb-2 font-mono text-xs text-slate-800">
              {trace.skillsApplied.length ? trace.skillsApplied.join(', ') : '—'}
            </p>
            {trace.skillsMissing.length ? (
              <p className="mb-2 text-xs text-amber-700">
                {t('aiWorkspace.preview.run.skillsMissing', { ids: trace.skillsMissing.join(', ') })}
              </p>
            ) : null}
            <p className="text-xs text-slate-500">{t('aiWorkspace.preview.run.tools')}</p>
            {trace.tools.length === 0 ? (
              <p className="text-xs text-slate-400">{t('aiWorkspace.preview.run.noTools')}</p>
            ) : (
              <ul className="space-y-1">
                {trace.tools.map((tool) => (
                  <li key={tool.toolCallId || tool.toolName} className="flex justify-between gap-2 text-xs">
                    <span className="truncate font-mono text-slate-800">{tool.toolName}</span>
                    <span className={TOOL_STATE_CLASS[tool.state]}>
                      {t(`aiWorkspace.preview.run.toolState.${tool.state}`)}
                    </span>
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
