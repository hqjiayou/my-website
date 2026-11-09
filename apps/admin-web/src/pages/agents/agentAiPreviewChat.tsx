import {
  ActionBarPrimitive,
  AssistantRuntimeProvider,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  useAui,
  useAuiState,
  useLocalRuntime,
  type TextMessagePartProps,
} from '@assistant-ui/react'
import { useQuery } from '@tanstack/react-query'
import { ArrowUp, Check, Copy, MessageSquare, Quote, SlidersHorizontal, TriangleAlert, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import type {
  AiCitation,
  AiMessage,
  AiPreviewAgentEvent,
  AiPreviewHistoryMessage,
  AiPreviewTodoItem,
} from '@/types/ai'

import { isSameModel, modelIdFromSpec } from '@/utils/modelSpec'
import {
  AgentAiDebugInspector,
  type PreviewLocationStatus,
  type RunTrace,
} from './agentAiDebugInspector'
import {
  createJavaPreviewAdapter,
  requestPreviewLocation,
  type PreviewLocation,
} from './agentAiPreviewAdapter'
import { PreviewVisualizationPart } from './agentAiPreviewCards'

function PreviewAssistantLabel() {
  const spec = useAuiState((state) => {
    if (state.message.role !== 'assistant') return ''
    const value = state.message.metadata?.custom?.requestedModel
    return typeof value === 'string' ? value : ''
  })
  if (!spec) return null
  return (
    <p className="mb-2 inline-flex max-w-full truncate rounded-md bg-card-muted px-1.5 py-0.5 font-mono text-[11px] text-muted-foreground">
      {spec}
    </p>
  )
}

function PreviewMessageActions({ align }: { align: 'start' | 'end' }) {
  const { t } = useTranslation('sites')
  const aui = useAui()
  const messageId = useAuiState((state) => state.message.id)
  const isCopied = useAuiState((state) => state.message.isCopied)

  return (
    <ActionBarPrimitive.Root
      hideWhenRunning
      className={`mt-1 flex items-center gap-0.5 opacity-80 transition-opacity focus-within:opacity-100 md:opacity-0 md:group-hover:opacity-100 ${
        align === 'end' ? 'justify-end' : 'justify-start'
      }`}
    >
      <ActionBarPrimitive.Copy
        aria-label={t('aiWorkspace.preview.copy')}
        title={isCopied ? t('aiWorkspace.preview.copied') : t('aiWorkspace.preview.copy')}
        className="flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground transition hover:bg-card hover:text-foreground"
      >
        {isCopied ? <Check className="h-3.5 w-3.5" aria-hidden /> : <Copy className="h-3.5 w-3.5" aria-hidden />}
      </ActionBarPrimitive.Copy>
      <button
        type="button"
        aria-label={t('aiWorkspace.preview.quote')}
        title={t('aiWorkspace.preview.quote')}
        className="flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground transition hover:bg-card hover:text-foreground"
        onClick={() => {
          const text = aui.message.getCopyText().trim()
          if (!text) return
          aui.composer.setQuote({ text, messageId })
        }}
      >
        <Quote className="h-3.5 w-3.5" aria-hidden />
      </button>
    </ActionBarPrimitive.Root>
  )
}

const EMPTY_CITATIONS: AiCitation[] = []

/** 角标号与会话记录列表（agentAiRunMeta）同口径：优先执行层分配的 cite，缺省用数组序。 */
function citationNumber(citation: AiCitation, index: number): number {
  return citation.cite ?? index + 1
}

/** 检索分数是 0–1 的相似度，收成百分比并夹到 0–100（与 Portal 同口径）。 */
function similarityPercent(score: number): number {
  if (!Number.isFinite(score)) return 0
  return Math.min(100, Math.max(0, Math.round(score * 100)))
}

function CitationSourceCard({
  citation,
  n,
  onClose,
}: {
  citation: AiCitation
  n: number
  onClose: () => void
}) {
  const { t } = useTranslation('sites')
  const location = citation.location
  const locationParts: string[] = []
  if (location?.page != null && location.page > 0) {
    locationParts.push(t('aiWorkspace.preview.citations.page', { page: location.page }))
  }
  if (location?.sheet) locationParts.push(location.sheet)
  if (location?.rowStart != null) {
    locationParts.push(
      t('aiWorkspace.preview.citations.rows', { start: location.rowStart, end: location.rowEnd ?? location.rowStart }),
    )
  }
  return (
    <div className="mt-2 rounded-lg border border-border bg-card px-3 py-2 text-xs">
      <div className="flex items-start justify-between gap-2">
        <p className="font-medium text-foreground">{t('aiWorkspace.preview.citations.source', { n })}</p>
        <button
          type="button"
          aria-label={t('aiWorkspace.preview.citations.close')}
          className="rounded p-0.5 text-muted-foreground transition hover:text-foreground"
          onClick={onClose}
        >
          <X className="h-3.5 w-3.5" aria-hidden />
        </button>
      </div>
      <p className="mt-1 break-words text-foreground">{citation.title || citation.sourceId}</p>
      {locationParts.length ? <p className="mt-0.5 text-muted-foreground">{locationParts.join(' · ')}</p> : null}
      <p className="mt-0.5 text-muted-foreground">
        {t('aiWorkspace.preview.citations.score', { percent: similarityPercent(citation.score) })}
      </p>
    </div>
  )
}

/**
 * 文本呈现：与默认 Text 同为 pre-line 纯文本，只把正文里的 [cN] 标记换成角标。
 * citations 由 adapter 在 DONE 后挂到 message metadata.custom——流式期间没有
 * 角标（标记按原文显示），DONE 后一次性补挂，编号由执行层分配、不会重排；
 * 对不上任何引用的标记保持原文，不吞信息。
 */
function PreviewTextPart({ text, status }: TextMessagePartProps) {
  const { t } = useTranslation('sites')
  const citations = useAuiState((state) => {
    if (state.message.role !== 'assistant') return EMPTY_CITATIONS
    const value = state.message.metadata?.custom?.citations
    return Array.isArray(value) ? (value as AiCitation[]) : EMPTY_CITATIONS
  })
  const [openCite, setOpenCite] = useState<number | null>(null)
  const byNumber = new Map(citations.map((citation, index) => [citationNumber(citation, index), citation]))
  const open = openCite != null ? byNumber.get(openCite) : undefined
  const running = status.type === 'running'
  if (running && !text) {
    return <p className="animate-pulse text-sm text-muted-foreground">{t('aiWorkspace.preview.thinking')}</p>
  }
  return (
    <>
      <p style={{ whiteSpace: 'pre-line' }}>
        {text.split(/(\[c\d+\])/g).map((segment, index) => {
          const matched = /^\[c(\d+)\]$/.exec(segment)
          const n = matched ? Number(matched[1]) : null
          const citation = n != null ? byNumber.get(n) : undefined
          if (n == null || !citation) return <span key={index}>{segment}</span>
          return (
            <button
              key={index}
              type="button"
              aria-expanded={openCite === n}
              title={citation.title || citation.sourceId}
              className="mx-0.5 rounded bg-info-muted px-1 align-baseline font-mono text-[10px] font-medium leading-4 text-info-emphasis transition hover:underline"
              onClick={() => setOpenCite(openCite === n ? null : n)}
            >
              [{n}]
            </button>
          )
        })}
        {running ? (
          <span className="ml-1 inline-block h-2 w-2 animate-pulse rounded-full bg-foreground align-middle" aria-hidden />
        ) : null}
      </p>
      {open && openCite != null ? (
        <CitationSourceCard citation={open} n={openCite} onClose={() => setOpenCite(null)} />
      ) : null}
    </>
  )
}

/**
 * 回复内卡片（#178 预留接入点，#179 落地）：VISUALIZATION 事件由 adapter 以
 * data part（name="visualization"）产出后从这里进入消息流，按 part 在内容中
 * 的顺序与文本同级排布。真组件在 agentAiPreviewCards（kind 分发天气卡/图表卡）。
 */

const PREVIEW_PART_COMPONENTS = {
  Text: PreviewTextPart,
  data: { by_name: { visualization: PreviewVisualizationPart } },
}

/** 用户主色气泡右偏中；助手卡片气泡左偏中；操作栏提供复制与引用。 */
function PreviewMessage() {
  const role = useAuiState((state) => state.message.role)
  if (role === 'user') {
    return (
      <MessagePrimitive.Root className="group mb-3 ml-auto flex w-fit max-w-[min(36rem,78%)] flex-col items-end">
        <div className="w-fit max-w-full rounded-2xl bg-primary px-3.5 py-2 text-sm text-primary-foreground">
          <MessagePrimitive.Quote>
            {({ text }) => (
              <div className="mb-1.5 border-l-2 border-primary-foreground/45 pl-2 text-xs leading-5 text-primary-foreground/80">
                {text}
              </div>
            )}
          </MessagePrimitive.Quote>
          <MessagePrimitive.Content components={PREVIEW_PART_COMPONENTS} />
        </div>
        <PreviewMessageActions align="end" />
      </MessagePrimitive.Root>
    )
  }
  return (
    <MessagePrimitive.Root className="group mb-3 mr-auto flex w-fit max-w-[min(40rem,86%)] flex-col items-start">
      <div className="w-fit max-w-full rounded-2xl border border-border bg-card px-3.5 py-2.5 text-sm leading-6 text-foreground">
        <PreviewAssistantLabel />
        <MessagePrimitive.Content components={PREVIEW_PART_COMPONENTS} />
      </div>
      <PreviewMessageActions align="start" />
    </MessagePrimitive.Root>
  )
}

const EMPTY_TRACE: RunTrace = {
  skillsApplied: [],
  skillsMissing: [],
  thinking: '',
  tools: [],
  usage: null,
  model: null,
  requestedModel: null,
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

/** adapter 每轮读取的最新选择；闭包持有，避免选择变化时重建 runtime（会丢失预览线程）。 */
function previewConversationStorageKey(agentId: number) {
  return `admin-preview-conversation:${agentId}`
}

function readStoredConversationId(agentId: number): number | null {
  try {
    const raw = sessionStorage.getItem(previewConversationStorageKey(agentId))
    const id = raw ? Number(raw) : NaN
    return Number.isFinite(id) && id > 0 ? id : null
  } catch {
    return null
  }
}

function writeStoredConversationId(agentId: number, conversationId: number | null) {
  try {
    const key = previewConversationStorageKey(agentId)
    if (conversationId == null) sessionStorage.removeItem(key)
    else sessionStorage.setItem(key, String(conversationId))
  } catch {
    // sessionStorage 不可用时仍用内存态续写本页。
  }
}

function toPreviewHistory(messages: AiMessage[]): AiPreviewHistoryMessage[] {
  return messages
    .filter((item) => item.role === 'USER' || item.role === 'ASSISTANT')
    .map((item) => ({ role: item.role, content: item.content.trim().slice(0, 8000) }))
    .filter((item) => item.content)
}

function createPreviewSelection(initialConversationId: number | null) {
  let skills: string[] = []
  let todos: AiPreviewTodoItem[] = []
  let mode: 'CLI' | 'KEY' = 'CLI'
  const entryByMode: Record<'CLI' | 'KEY', number | null> = { CLI: null, KEY: null }
  let requestedSpec = ''
  let conversationId: number | null = initialConversationId
  let persistedHistory: AiPreviewHistoryMessage[] = []
  // 定位默认关闭；只有 Inspector 授权成功后才缓存坐标并启用，关闭即清除。
  let locationEnabled = false
  let cachedLocation: PreviewLocation | undefined
  let locationEpoch = 0
  return {
    getLocationEnabled: () => locationEnabled,
    getCachedLocation: () => cachedLocation,
    setLocation: (next: PreviewLocation) => {
      cachedLocation = next
      locationEnabled = true
    },
    clearLocation: () => {
      cachedLocation = undefined
      locationEnabled = false
    },
    // 授权请求是异步的：每次开/关递增，过期的回调据此丢弃，避免关闭后又被迟到的授权写回。
    nextLocationEpoch: () => ++locationEpoch,
    isLocationEpoch: (epoch: number) => epoch === locationEpoch,
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
    getConversationId: () => conversationId,
    setConversationId: (id: number | null) => {
      conversationId = id
    },
    getPersistedHistory: () => persistedHistory,
    setPersistedHistory: (next: AiPreviewHistoryMessage[]) => {
      persistedHistory = next
    },
  }
}

function applyAgentEvent(trace: RunTrace, event: AiPreviewAgentEvent, requestedSpec: string): RunTrace {
  switch (event.type) {
    case 'start':
      return {
        skillsApplied: event.skillsApplied,
        skillsMissing: event.skillsMissing,
        thinking: '',
        tools: [],
        usage: null,
        model: null,
        requestedModel: requestedSpec || null,
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
  const [selection] = useState(() => createPreviewSelection(readStoredConversationId(agentId)))
  const [conversationId, setConversationId] = useState<number | null>(() => readStoredConversationId(agentId))
  const [restoredMessages, setRestoredMessages] = useState<AiMessage[]>([])
  const [mode, setMode] = useState<'CLI' | 'KEY'>('CLI')
  const [modeChosen, setModeChosen] = useState(false)
  // 调试 Inspector 默认收起：聊天区占满，需要时再右侧停靠（xl+）或抽屉打开（xl 以下）。
  const [inspectorOpen, setInspectorOpen] = useState(false)
  const [locationStatus, setLocationStatus] = useState<PreviewLocationStatus>('off')
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

  // 渲染期调整（与下方服务端 mode 采纳同法）：agentId 变化（含首次挂载）时把本地
  // 续写标记同步落进 state，不在 effect 里同步 setState。
  const [restoredAgentId, setRestoredAgentId] = useState<number | null>(null)
  if (restoredAgentId !== agentId) {
    setRestoredAgentId(agentId)
    const storedId = readStoredConversationId(agentId)
    selection.setConversationId(storedId)
    setConversationId(storedId)
    if (storedId == null) {
      selection.setPersistedHistory([])
      setRestoredMessages([])
    }
  }

  useEffect(() => {
    const storedId = readStoredConversationId(agentId)
    if (storedId == null) {
      return
    }
    let cancelled = false
    void aiApi
      .listConversationMessages(agentId, storedId)
      .then((messages) => {
        if (cancelled) return
        setRestoredMessages(messages)
        selection.setPersistedHistory(toPreviewHistory(messages))
      })
      .catch(() => {
        if (cancelled) return
        // 会话已删或不属于预览：清掉本地续写标记，避免后续请求 404。
        writeStoredConversationId(agentId, null)
        selection.setConversationId(null)
        selection.setPersistedHistory([])
        setConversationId(null)
        setRestoredMessages([])
      })
    return () => {
      cancelled = true
    }
  }, [agentId, selection])

  const fallbackServed =
    trace.requestedModel != null && trace.model != null && !isSameModel(trace.requestedModel, trace.model)
      ? modelIdFromSpec(trace.model)
      : ''

  // 服务端默认模式只采纳一次（用户未手动选时）：按 React 官方推荐的渲染期调整，
  // 不在 effect 里同步 setState。采纳后 modeChosen=true 使查询键切换为带 mode 重查，
  // 回来的 mode 与已采纳值相同、不会重复触发。
  const [adoptedServerMode, setAdoptedServerMode] = useState<'CLI' | 'KEY' | null>(null)
  if (!modeChosen && modelQuery.data && adoptedServerMode !== modelQuery.data.mode) {
    setAdoptedServerMode(modelQuery.data.mode)
    selection.setMode(modelQuery.data.mode)
    setMode(modelQuery.data.mode)
    setModeChosen(true)
  }

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

  // 定位只在用户显式打开开关（用户手势）时请求；失败/拒绝只改状态，不影响发送。
  const toggleLocation = (enable: boolean) => {
    const epoch = selection.nextLocationEpoch()
    selection.clearLocation()
    if (!enable) {
      setLocationStatus('off')
      return
    }
    setLocationStatus('requesting')
    void requestPreviewLocation().then((coords) => {
      if (!selection.isLocationEpoch(epoch)) return
      if (!coords) {
        setLocationStatus('failed')
        return
      }
      selection.setLocation(coords)
      setLocationStatus('on')
    })
  }

  const beginNewConversation = () => {
    writeStoredConversationId(agentId, null)
    selection.setConversationId(null)
    selection.setPersistedHistory([])
    setConversationId(null)
    setRestoredMessages([])
  }

  const adapter = useMemo(
    () =>
      createJavaPreviewAdapter(agentId, {
        getSkills: selection.getSkills,
        getTodos: selection.getTodos,
        getMode: selection.getMode,
        getModelEntryId: selection.getModelEntryId,
        getRequestedSpec: selection.getRequestedSpec,
        getConversationId: selection.getConversationId,
        getPersistedHistory: selection.getPersistedHistory,
        getLocationEnabled: selection.getLocationEnabled,
        getCachedLocation: selection.getCachedLocation,
        onAgentEvent: (event) => {
          if (event.type === 'error') {
            if (event.errorCode === 'MODEL_ENTRY_UNAVAILABLE') {
              selection.setModelEntryId(null)
              setModelEntryId(null)
            }
            setRunError({ errorCode: event.errorCode, message: event.message })
            return
          }
          if (event.type === 'conversation') {
            selection.setConversationId(event.conversationId)
            writeStoredConversationId(agentId, event.conversationId)
            setConversationId(event.conversationId)
            return
          }
          if (event.type === 'start') setRunError(null)
          if (event.type === 'done') updateTodos(event.todos)
          const requestedSpec = selection.getRequestedSpec()
          setTrace((current) => applyAgentEvent(current, event, requestedSpec))
        },
      }),
    [agentId, selection, updateTodos],
  )
  const runtime = useLocalRuntime(adapter)

  return (
    <AssistantRuntimeProvider runtime={runtime}>
      <div className="flex h-full min-h-0 gap-4">
        <ThreadPrimitive.Root className="flex min-h-[20rem] min-w-0 flex-1 flex-col overflow-hidden rounded-xl border border-border bg-card-muted">
          <div className="flex items-center justify-between gap-2 border-b border-border px-3 py-2 sm:px-6">
            <p className="min-w-0 flex-1 truncate text-xs text-muted-foreground">
              <span className="mr-1.5 rounded-md bg-card px-1.5 py-0.5 font-medium text-foreground">
                {mode === 'CLI' ? t('aiWorkspace.preview.modeCli') : t('aiWorkspace.preview.modeKey')}
              </span>
              {modelQuery.isPending ? (
                t('aiWorkspace.loading')
              ) : namedMissing ? (
                t('aiWorkspace.preview.errors.MODEL_ENTRY_UNAVAILABLE')
              ) : activeModel ? (
                <span className="font-mono">{activeModel}</span>
              ) : (
                t('aiWorkspace.preview.noEntry')
              )}
            </p>
            <button
              type="button"
              aria-expanded={inspectorOpen}
              aria-label={inspectorOpen ? t('aiWorkspace.preview.inspector.close') : t('aiWorkspace.preview.inspector.open')}
              title={inspectorOpen ? t('aiWorkspace.preview.inspector.close') : t('aiWorkspace.preview.inspector.open')}
              className="flex h-8 shrink-0 items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 text-xs font-medium text-foreground transition hover:bg-card-muted"
              onClick={() => setInspectorOpen((value) => !value)}
            >
              <SlidersHorizontal className="h-4 w-4" aria-hidden />
              <span className="hidden sm:inline">{t('aiWorkspace.preview.inspector.toggle')}</span>
            </button>
          </div>
          {fallbackServed ? (
            <p
              role="status"
              className="flex items-start gap-1.5 border-b border-border bg-warning-muted px-3 py-1.5 text-xs text-warning-emphasis sm:px-6"
            >
              <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
              {t('aiWorkspace.preview.run.servedModel', { model: fallbackServed })}
            </p>
          ) : null}
          {conversationId != null ? (
            <div className="flex items-center justify-between gap-2 border-b border-border px-3 py-2 text-xs text-muted-foreground sm:px-6">
              <span>{t('aiWorkspace.preview.conversation.resume', { id: conversationId })}</span>
              <button type="button" className="shrink-0 underline" onClick={beginNewConversation}>
                {t('aiWorkspace.preview.conversation.new')}
              </button>
            </div>
          ) : null}
          <ThreadPrimitive.Viewport className="flex-1 overflow-y-auto px-3 py-4 sm:px-10">
            {restoredMessages.length > 0 ? (
              <ul className="mb-4 space-y-3 border-b border-border pb-4">
                {restoredMessages.map((item) => {
                  const isUser = item.role === 'USER'
                  return (
                    <li
                      key={item.id}
                      className={`flex max-w-[min(40rem,86%)] flex-col ${isUser ? 'ml-auto items-end' : 'mr-auto items-start'}`}
                    >
                      <div
                        className={
                          isUser
                            ? 'w-fit max-w-full rounded-2xl bg-primary px-3.5 py-2.5 text-sm leading-6 text-primary-foreground'
                            : 'w-fit max-w-full rounded-2xl border border-border bg-card px-3.5 py-2.5 text-sm leading-6 text-foreground'
                        }
                      >
                        <p className="whitespace-pre-wrap break-words">{item.content}</p>
                      </div>
                    </li>
                  )
                })}
              </ul>
            ) : null}
            <ThreadPrimitive.Empty>
              {restoredMessages.length === 0 ? (
                <div className="flex min-h-full flex-col items-center justify-center gap-2 px-4 py-16 text-center">
                  <span className="flex h-11 w-11 items-center justify-center rounded-full border border-border bg-card text-muted-foreground">
                    <MessageSquare className="h-5 w-5" aria-hidden />
                  </span>
                  <p className="text-sm font-medium text-foreground">{t('aiWorkspace.preview.empty')}</p>
                  <p className="max-w-md text-xs leading-5 text-muted-foreground">
                    {t('aiWorkspace.preview.emptyHint')}
                  </p>
                </div>
              ) : null}
            </ThreadPrimitive.Empty>
            <ThreadPrimitive.Messages>{() => <PreviewMessage />}</ThreadPrimitive.Messages>
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
          <ComposerPrimitive.Root className="px-3 pb-3 pt-1 sm:px-6">
            <ComposerPrimitive.Quote className="mb-2 flex items-start gap-2 rounded-xl border border-border bg-card px-2.5 py-2">
              <ComposerPrimitive.QuoteText className="min-w-0 flex-1 whitespace-pre-wrap break-words text-xs leading-5 text-muted-foreground line-clamp-3" />
              <ComposerPrimitive.QuoteDismiss
                aria-label={t('aiWorkspace.preview.quoteDismiss')}
                title={t('aiWorkspace.preview.quoteDismiss')}
                className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-muted-foreground transition hover:bg-card-muted hover:text-foreground"
              >
                <X className="h-3.5 w-3.5" aria-hidden />
              </ComposerPrimitive.QuoteDismiss>
            </ComposerPrimitive.Quote>
            <div className="flex items-end gap-2 rounded-2xl border border-border bg-card p-1.5">
              <ComposerPrimitive.Input
                className="min-h-9 flex-1 resize-none bg-transparent px-2 py-1.5 text-sm text-foreground outline-none"
                placeholder={t('aiWorkspace.preview.placeholder')}
              />
              <ComposerPrimitive.Send
                aria-label={t('aiWorkspace.preview.send')}
                title={t('aiWorkspace.preview.send')}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground transition hover:opacity-90 disabled:opacity-40"
              >
                <ArrowUp className="h-4 w-4" aria-hidden />
              </ComposerPrimitive.Send>
            </div>
          </ComposerPrimitive.Root>
        </ThreadPrimitive.Root>
        <AgentAiDebugInspector
          agentId={agentId}
          open={inspectorOpen}
          onClose={() => setInspectorOpen(false)}
          mode={mode}
          onChooseMode={chooseMode}
          entries={entries}
          modelEntryId={modelEntryId}
          entryPicked={picked != null}
          onChooseEntry={chooseEntry}
          modelPending={modelQuery.isPending}
          namedMissing={namedMissing}
          activeModel={activeModel}
          trace={trace}
          selectedSkills={selectedSkills}
          onToggleSkill={toggleSkill}
          todos={todos}
          onClearTodos={() => updateTodos([])}
          locationStatus={locationStatus}
          onToggleLocation={toggleLocation}
        />
      </div>
    </AssistantRuntimeProvider>
  )
}
