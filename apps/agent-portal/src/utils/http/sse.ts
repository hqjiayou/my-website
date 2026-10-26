import { apiUrl } from '@/config/env'
import type { SiteAiChatRequest, SiteAiCitation } from '@/types/api'
import { getAccessToken } from '@/utils/token'

import { ApiError } from './apiError'
import { refreshAccessToken } from './axios'

const STREAM_TIMEOUT_MS = 130_000

export type SiteSseEvent =
  | { event: 'START' }
  | { event: 'TEXT_DELTA'; delta: string }
  | { event: 'DONE'; reply: string; providerStatus: string; citations?: SiteAiCitation[] }
  | { event: 'CONVERSATION'; conversationId: string }
  | { event: 'ERROR'; errorCode: string }

export async function streamSiteChat(
  siteId: string,
  body: SiteAiChatRequest,
  onEvent: (event: SiteSseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  await readStream(siteId, body, onEvent, signal, false)
}

async function readStream(
  siteId: string,
  body: SiteAiChatRequest,
  onEvent: (event: SiteSseEvent) => void,
  signal: AbortSignal | undefined,
  retried: boolean,
): Promise<void> {
  const controller = new AbortController()
  let timedOut = false
  const timeoutId = window.setTimeout(() => {
    timedOut = true
    controller.abort()
  }, STREAM_TIMEOUT_MS)
  const abortFromCaller = () => controller.abort()
  signal?.addEventListener('abort', abortFromCaller)

  try {
    const headers = new Headers({
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    })
    const token = getAccessToken()
    if (token) headers.set('Authorization', `Bearer ${token}`)

    const response = await fetch(apiUrl(`/api/site/${siteId}/ai/chat/stream`), {
      method: 'POST',
      credentials: 'include',
      headers,
      body: JSON.stringify(body),
      signal: controller.signal,
    })

    if (response.status === 401 && !retried) {
      await response.body?.cancel()
      await refreshAccessToken()
      await readStream(siteId, body, onEvent, signal, true)
      return
    }

    if (!response.ok) {
      throw new ApiError(await readErrorMessage(response), { status: response.status })
    }

    const contentType = response.headers.get('content-type') ?? ''
    if (contentType.includes('application/json')) {
      throw new ApiError(await readErrorMessage(response), { status: response.status })
    }

    await consumeSse(response, onEvent)
  } catch (error) {
    if (timedOut) throw new ApiError('等待回覆逾時')
    throw error
  } finally {
    window.clearTimeout(timeoutId)
    signal?.removeEventListener('abort', abortFromCaller)
  }
}

async function consumeSse(response: Response, onEvent: (event: SiteSseEvent) => void): Promise<void> {
  const reader = response.body?.getReader()
  if (!reader) throw new ApiError('回應沒有可讀內容')

  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    buffer = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
    let splitAt = buffer.indexOf('\n\n')
    while (splitAt !== -1) {
      const raw = buffer.slice(0, splitAt)
      buffer = buffer.slice(splitAt + 2)
      const event = parseFrame(raw)
      if (event) onEvent(event)
      splitAt = buffer.indexOf('\n\n')
    }
  }
}

function parseFrame(raw: string): SiteSseEvent | null {
  let eventName = ''
  const dataLines: string[] = []
  for (const line of raw.split('\n')) {
    if (!line || line.startsWith(':')) continue
    const colon = line.indexOf(':')
    if (colon === -1) continue
    const field = line.slice(0, colon)
    let value = line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') eventName = value
    if (field === 'data') dataLines.push(value)
  }
  if (!eventName) return null

  let data: unknown = {}
  const payload = dataLines.join('\n')
  if (payload) {
    try {
      data = JSON.parse(payload) as unknown
    } catch {
      throw new ApiError(`無法解析 ${eventName} 事件`)
    }
  }
  return toSiteEvent(eventName, data)
}

function toSiteEvent(eventName: string, data: unknown): SiteSseEvent | null {
  const record = isRecord(data) ? data : {}
  switch (eventName) {
    case 'START':
      return { event: 'START' }
    case 'TEXT_DELTA':
      return { event: 'TEXT_DELTA', delta: readDelta(record) }
    case 'DONE':
      return {
        event: 'DONE',
        reply: readString(record, 'reply'),
        providerStatus: readString(record, 'providerStatus'),
        citations: readCitations(record),
      }
    case 'CONVERSATION':
      return { event: 'CONVERSATION', conversationId: readConversationId(record) }
    case 'ERROR':
      return { event: 'ERROR', errorCode: readString(record, 'errorCode') }
    default:
      return null
  }
}

/**
 * agent-services 的 TEXT_DELTA 同時帶 text 與 delta，兩者是同一段增量。
 * Java 原樣轉送。兩個都接起來會把回覆寫兩遍，所以只取 delta，沒有 delta 時才用 text。
 */
function readDelta(record: Record<string, unknown>): string {
  if (typeof record.delta === 'string') return record.delta
  if (typeof record.text === 'string') return record.text
  return ''
}

function readConversationId(record: Record<string, unknown>): string {
  const value = record.conversationId
  if (typeof value === 'string') return value
  if (typeof value === 'number' && Number.isSafeInteger(value)) return String(value)
  return ''
}

function readString(record: Record<string, unknown>, key: string): string {
  const value = record[key]
  return typeof value === 'string' ? value : ''
}

/**
 * 沒有 grounding 時 citations 不出現，Java 也可能送空陣列。
 * 缺失、非陣列，或沒有任何格式正確的項目時為 undefined，不讓 DONE 解析失敗。
 */
function readCitations(record: Record<string, unknown>): SiteAiCitation[] | undefined {
  if (!Object.prototype.hasOwnProperty.call(record, 'citations') || record.citations == null) return undefined
  if (!Array.isArray(record.citations)) return undefined
  const citations: SiteAiCitation[] = []
  for (const item of record.citations) {
    const citation = readCitation(item)
    if (!citation) continue
    citations.push(citation)
  }
  return citations.length > 0 ? citations : undefined
}

function readCitation(value: unknown): SiteAiCitation | null {
  if (!isRecord(value)) return null
  const { sourceId, chunkIndex, score } = value
  if (typeof sourceId !== 'string' || sourceId.length === 0) return null
  if (typeof chunkIndex !== 'number' || !Number.isInteger(chunkIndex)) return null
  if (typeof score !== 'number' || !Number.isFinite(score)) return null
  return { sourceId, chunkIndex, score }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: unknown }
    if (typeof body.message === 'string' && body.message) return body.message
  } catch {
    // 非 JSON 錯誤主體
  }
  return `請求失敗（HTTP ${response.status}）`
}
