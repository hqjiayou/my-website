import type { ChatModelAdapter } from '@assistant-ui/react'

import { streamPreviewChat } from '@/api/apps/aiApi'
import type { AiPreviewAgentEvent, AiPreviewHistoryMessage, AiPreviewTodoItem } from '@/types/ai'

import { consumePreviewSse } from './agentAiPreviewSse'

function sessionTimeZone(): string | undefined {
  try {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone?.trim()
    return timezone || undefined
  } catch {
    return undefined
  }
}

export interface PreviewLocation {
  latitude: number
  longitude: number
  accuracyMeters?: number
}

/**
 * 浏览器定位授权：只在调试 Inspector 里用户显式打开定位开关时调用（须由用户手势触发）。
 * 发送链路（adapter.run）从不调用它，也不等待它；失败/超时/拒绝一律返回 undefined。
 */
export function requestPreviewLocation(): Promise<PreviewLocation | undefined> {
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

/** assistant-ui 把引用挂在 metadata.custom.quote；发给预览 API 时拼进正文，否则模型看不到。 */
function outboundText(message: {
  content?: string | ReadonlyArray<{ type?: string; text?: string }>
  metadata?: { custom?: Record<string, unknown> }
}): string {
  const body = textOf(message).trim()
  const raw = message.metadata?.custom?.quote
  const quoted =
    raw && typeof raw === 'object' && typeof (raw as { text?: unknown }).text === 'string'
      ? ((raw as { text: string }).text).trim()
      : ''
  if (!quoted) return body
  const block = quoted
    .split('\n')
    .map((line) => `> ${line}`)
    .join('\n')
  return body ? `${block}\n\n${body}` : block
}

export interface PreviewAdapterOptions {
  /** 每轮发送时读取最新选择，避免因选择变化重建 runtime（会丢失线程）。 */
  getSkills: () => string[]
  getTodos: () => AiPreviewTodoItem[]
  getMode: () => 'CLI' | 'KEY'
  getModelEntryId: () => number | null
  getRequestedSpec: () => string
  getConversationId: () => number | null
  /** 本地线程为空时（例如刷新后）用库内消息拼 history。 */
  getPersistedHistory: () => AiPreviewHistoryMessage[]
  /** 定位默认关闭；仅 Inspector 里显式开启后才随请求发送。 */
  getLocationEnabled: () => boolean
  /** 已授权并缓存的坐标；adapter 只读缓存，不触发浏览器定位、不等待。 */
  getCachedLocation: () => PreviewLocation | undefined
  onAgentEvent: (event: AiPreviewAgentEvent) => void
  /** 代际守卫：该 adapter 所属的面板已卸载 / 被换成别的 Agent 时返回 false，迟到事件一律丢弃。 */
  isCurrent?: () => boolean
}

export function createJavaPreviewAdapter(
  agentId: number,
  options: PreviewAdapterOptions,
): ChatModelAdapter {
  return {
    async *run({ messages, abortSignal }) {
      const last = messages[messages.length - 1]
      const message = last ? outboundText(last).trim() : ''
      if (!message) {
        yield { content: [{ type: 'text' as const, text: '' }] }
        return
      }

      // AdminAiPreviewChatRequest：history ≤ 20，单条 content ≤ 8000。只带最近一轮窗口，避免长线程 400。
      const liveHistory: AiPreviewHistoryMessage[] = messages
        .slice(0, -1)
        .flatMap((item) => {
          const role: AiPreviewHistoryMessage['role'] | null =
            item.role === 'assistant' ? 'ASSISTANT' : item.role === 'user' ? 'USER' : null
          if (!role) return []
          const content = outboundText(item).trim().slice(0, 8000)
          return content ? [{ role, content }] : []
        })
      // 刷新后本地线程为空：用已恢复的库内消息；有本地线程时以本地为准。
      const history = (liveHistory.length ? liveHistory : options.getPersistedHistory()).slice(-20)
      const skills = options.getSkills()
      const todos = options.getTodos()
      const modelEntryId = options.getModelEntryId()
      const conversationId = options.getConversationId()
      const requestedModel = options.getRequestedSpec()
      const timezone = sessionTimeZone()
      const location = options.getLocationEnabled() ? options.getCachedLocation() : undefined

      const response = await streamPreviewChat(
        agentId,
        {
          message,
          history: history.length ? history : undefined,
          skills,
          todos: todos.length ? todos : undefined,
          mode: options.getMode(),
          ...(modelEntryId != null ? { modelEntryId } : {}),
          ...(conversationId != null ? { conversationId } : {}),
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
        if (options.isCurrent?.() !== false) {
          options.onAgentEvent({ type: 'error', errorCode: 'REQUEST_FAILED', message: message ?? detail })
        }
        throw new Error(detail)
      }

      yield* consumePreviewSse(response.body, {
        requestedModel,
        abortSignal,
        isCurrent: options.isCurrent,
        onAgentEvent: options.onAgentEvent,
      })
    },
  }
}