import type { ChatModelAdapter } from '@assistant-ui/react'

import { streamPreviewChat } from '@/api/apps/aiApi'
import type {
  AiPreviewAgentEvent,
  AiPreviewHistoryMessage,
  AiPreviewTodoItem,
  AiRunUsage,
  AiSearchSummary,
} from '@/types/ai'

function sessionTimeZone(): string | undefined {
  try {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone?.trim()
    return timezone || undefined
  } catch {
    return undefined
  }
}

function sessionLocation(): Promise<{ latitude: number; longitude: number; accuracyMeters?: number } | undefined> {
  if (typeof navigator === 'undefined' || !navigator.geolocation) {
    return Promise.resolve(undefined)
  }
  return new Promise((resolve) => {
    const timer = window.setTimeout(() => resolve(undefined), 12_000)
    navigator.geolocation.getCurrentPosition(
      (position) => {
        window.clearTimeout(timer)
        const { latitude, longitude, accuracy } = position.coords
        if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
          resolve(undefined)
          return
        }
        resolve({
          latitude,
          longitude,
          ...(Number.isFinite(accuracy) && accuracy >= 0 ? { accuracyMeters: accuracy } : {}),
        })
      },
      () => {
        window.clearTimeout(timer)
        resolve(undefined)
      },
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    )
  })
}

function textOf(message: {
  content?: string | ReadonlyArray<{ type?: string; text?: string }>
}): string {
  if (typeof message.content === 'string') return message.content
  if (!Array.isArray(message.content)) return ''
  return message.content
    .filter((part) => part.type === 'text' && typeof part.text === 'string')
    .map((part) => part.text ?? '')
    .join('')
}

function parseSseBlock(block: string): { event: string; data: string } | null {
  const lines = block.split('\n')
  let event = 'message'
  const dataLines: string[] = []
  for (const line of lines) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (dataLines.length === 0) return null
  return { event, data: dataLines.join('\n') }
}

function parseJson(data: string): Record<string, unknown> | null {
  try {
    const value: unknown = JSON.parse(data)
    return value && typeof value === 'object' ? (value as Record<string, unknown>) : null
  } catch {
    return null
  }
}

function stringList(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function toUsage(value: unknown): AiRunUsage | null {
  if (!value || typeof value !== 'object') return null
  const raw = value as Record<string, unknown>
  const num = (key: string) => (typeof raw[key] === 'number' ? (raw[key] as number) : 0)
  return {
    requests: num('requests'),
    toolCalls: num('toolCalls'),
    inputTokens: num('inputTokens'),
    outputTokens: num('outputTokens'),
  }
}

function toSearchSummary(value: unknown): AiSearchSummary | undefined {
  if (!value || typeof value !== 'object') return undefined
  const raw = value as Record<string, unknown>
  return {
    query: typeof raw.query === 'string' ? raw.query : '',
    hitCount: typeof raw.hitCount === 'number' ? raw.hitCount : 0,
  }
}

function toAgentEvent(event: string, payload: Record<string, unknown>): AiPreviewAgentEvent | null {
  switch (event) {
    case 'START':
      return {
        type: 'start',
        skillsApplied: stringList(payload.skillsApplied),
        skillsMissing: stringList(payload.skillsMissing),
      }
    case 'TOOL_START':
      return {
        type: 'toolStart',
        toolCallId: String(payload.toolCallId ?? ''),
        toolName: String(payload.toolName ?? ''),
      }
    case 'TOOL_RESULT':
      return {
        type: 'toolResult',
        toolCallId: String(payload.toolCallId ?? ''),
        toolName: String(payload.toolName ?? ''),
        ok: payload.ok !== false,
        summary: toSearchSummary(payload.summary),
      }
    case 'DONE':
      // agent-services 只在非空时输出 todos：缺省即「本轮结束后无 todo」
      return {
        type: 'done',
        todos: Array.isArray(payload.todos) ? (payload.todos as AiPreviewTodoItem[]) : [],
        usage: toUsage(payload.usage),
        model: typeof payload.model === 'string' && payload.model.trim() ? payload.model.trim() : null,
      }
    default:
      return null
  }
}

export interface PreviewAdapterOptions {
  /** 每轮发送时读取最新选择，避免因选择变化重建 runtime（会丢失线程）。 */
  getSkills: () => string[]
  getTodos: () => AiPreviewTodoItem[]
  getMode: () => 'CLI' | 'KEY'
  getModelEntryId: () => number | null
  getRequestedSpec: () => string
  onAgentEvent: (event: AiPreviewAgentEvent) => void
}

function labeled(text: string, requestedModel: string) {
  return {
    content: [{ type: 'text' as const, text }],
    metadata: { custom: { requestedModel } },
  }
}

export function createJavaPreviewAdapter(
  agentId: number,
  options: PreviewAdapterOptions,
): ChatModelAdapter {
  return {
    async *run({ messages, abortSignal }) {
      const last = messages[messages.length - 1]
      const message = last ? textOf(last).trim() : ''
      if (!message) {
        yield { content: [{ type: 'text' as const, text: '' }] }
        return
      }

      // AdminAiPreviewChatRequest：history ≤ 20，单条 content ≤ 8000。只带最近一轮窗口，避免长线程 400。
      const history: AiPreviewHistoryMessage[] = messages
        .slice(0, -1)
        .flatMap((item) => {
          const role: AiPreviewHistoryMessage['role'] | null =
            item.role === 'assistant' ? 'ASSISTANT' : item.role === 'user' ? 'USER' : null
          if (!role) return []
          const content = textOf(item).trim().slice(0, 8000)
          return content ? [{ role, content }] : []
        })
        .slice(-20)
      const skills = options.getSkills()
      const todos = options.getTodos()
      const modelEntryId = options.getModelEntryId()
      const requestedModel = options.getRequestedSpec()
      const timezone = sessionTimeZone()
      const location = await sessionLocation()

      const response = await streamPreviewChat(
        agentId,
        {
          message,
          history: history.length ? history : undefined,
          skills,
          todos: todos.length ? todos : undefined,
          mode: options.getMode(),
          ...(modelEntryId != null ? { modelEntryId } : {}),
          ...(timezone ? { timezone } : {}),
          ...(location ?? {}),
        },
        abortSignal,
      )
      if (!response.ok || !response.body) {
        let detail = `preview stream failed (${response.status})`
        let message: string | undefined
        try {
          const payload = (await response.json()) as { message?: string; errorCode?: string }
          detail = payload.errorCode ?? payload.message ?? detail
          message = payload.message
        } catch {
          // keep default
        }
        options.onAgentEvent({ type: 'error', errorCode: 'REQUEST_FAILED', message: message ?? detail })
        throw new Error(detail)
      }

      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let text = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const parts = buffer.split('\n\n')
        buffer = parts.pop() ?? ''
        for (const block of parts) {
          const parsed = parseSseBlock(block)
          if (!parsed) continue
          const payload = parseJson(parsed.data)
          if (parsed.event === 'TEXT_DELTA') {
            const delta = payload?.delta
            if (typeof delta === 'string') {
              text += delta
              yield labeled(text, requestedModel)
            }
          } else if (parsed.event === 'THINKING') {
            const delta = payload?.delta
            if (typeof delta === 'string' && delta) {
              options.onAgentEvent({ type: 'thinking', delta })
            }
          } else if (parsed.event === 'ERROR') {
            const code = typeof payload?.errorCode === 'string' ? payload.errorCode : 'SYSTEM_ERROR'
            const message = typeof payload?.message === 'string' ? payload.message : undefined
            options.onAgentEvent({ type: 'error', errorCode: code, message })
            const shown = message || code
            text = text ? `${text}\n${shown}` : shown
            yield labeled(text, requestedModel)
            return
          } else if (payload) {
            const agentEvent = toAgentEvent(parsed.event, payload)
            if (agentEvent) options.onAgentEvent(agentEvent)
          }
        }
      }

      yield labeled(text, requestedModel)
    },
  }
}
