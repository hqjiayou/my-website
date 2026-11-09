import type {
  AiChartCardPayload,
  AiChartPoint,
  AiChartSeries,
  AiChartType,
  AiCitation,
  AiCitationLocation,
  AiPreviewAgentEvent,
  AiPreviewTodoItem,
  AiRunUsage,
  AiSearchSummary,
  AiVisualizationEnvelope,
  AiWeatherCardPayload,
  AiWeatherCurrent,
  AiWeatherDailyPoint,
  AiWeatherHourlyPoint,
} from '@/types/ai'

// 预览 SSE 解析与消费：与 assistant-ui / axios 解耦（仅 type import），便于 Node 单测
// （scripts/check-preview-sse.ts）。不改后端 SSE 协议。
export function parseSseBlock(block: string): { event: string; data: string } | null {
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

function toCitationLocation(value: unknown): AiCitationLocation | null {
  if (!value || typeof value !== 'object') return null
  const raw = value as Record<string, unknown>
  const location: AiCitationLocation = {
    page: typeof raw.page === 'number' ? raw.page : null,
    sheet: typeof raw.sheet === 'string' && raw.sheet ? raw.sheet : null,
    rowStart: typeof raw.rowStart === 'number' ? raw.rowStart : null,
    rowEnd: typeof raw.rowEnd === 'number' ? raw.rowEnd : null,
  }
  return location.page != null || location.sheet != null || location.rowStart != null || location.rowEnd != null
    ? location
    : null
}

/** DONE 的 citations（cite/sourceId/chunkIndex/score，title/location 可选）：坏条目整条丢弃，不让脏数据进呈现层。 */
function toCitations(value: unknown): AiCitation[] {
  if (!Array.isArray(value)) return []
  const citations: AiCitation[] = []
  for (const item of value) {
    if (!item || typeof item !== 'object') continue
    const raw = item as Record<string, unknown>
    if (typeof raw.sourceId !== 'string' || !raw.sourceId) continue
    if (typeof raw.chunkIndex !== 'number' || typeof raw.score !== 'number' || !Number.isFinite(raw.score)) continue
    citations.push({
      sourceId: raw.sourceId,
      chunkIndex: raw.chunkIndex,
      score: raw.score,
      title: typeof raw.title === 'string' && raw.title ? raw.title : null,
      location: toCitationLocation(raw.location),
      cite: typeof raw.cite === 'number' ? raw.cite : null,
    })
  }
  return citations
}

function finiteNumber(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined
}

/** 天气卡清洗（Issue #179）：current.time 是关键字段，缺失整卡丢弃；数值字段缺失/非数即不写出，逐时/逐日坏行整行丢弃。 */
function toWeatherPayload(raw: Record<string, unknown>): AiWeatherCardPayload | null {
  const currentRaw = raw.current
  if (!currentRaw || typeof currentRaw !== 'object') return null
  const current = currentRaw as Record<string, unknown>
  if (typeof current.time !== 'string' || !current.time) return null
  const currentCard: AiWeatherCurrent = { time: current.time }
  const temperatureC = finiteNumber(current.temperatureC)
  if (temperatureC !== undefined) currentCard.temperatureC = temperatureC
  const apparentTemperatureC = finiteNumber(current.apparentTemperatureC)
  if (apparentTemperatureC !== undefined) currentCard.apparentTemperatureC = apparentTemperatureC
  const humidityPercent = finiteNumber(current.humidityPercent)
  if (humidityPercent !== undefined) currentCard.humidityPercent = humidityPercent
  const windSpeedKmh = finiteNumber(current.windSpeedKmh)
  if (windSpeedKmh !== undefined) currentCard.windSpeedKmh = windSpeedKmh
  const precipitationMm = finiteNumber(current.precipitationMm)
  if (precipitationMm !== undefined) currentCard.precipitationMm = precipitationMm
  const weatherCode = finiteNumber(current.weatherCode)
  if (weatherCode !== undefined) currentCard.weatherCode = weatherCode
  if (typeof current.weatherText === 'string' && current.weatherText) {
    currentCard.weatherText = current.weatherText
  }

  const hourly: AiWeatherHourlyPoint[] = []
  if (Array.isArray(raw.hourly)) {
    for (const item of raw.hourly) {
      if (!item || typeof item !== 'object') continue
      const row = item as Record<string, unknown>
      const rowTemperature = finiteNumber(row.temperatureC)
      if (typeof row.time !== 'string' || !row.time || rowTemperature === undefined) continue
      const point: AiWeatherHourlyPoint = { time: row.time, temperatureC: rowTemperature }
      const probability = finiteNumber(row.precipitationProbabilityPercent)
      if (probability !== undefined) point.precipitationProbabilityPercent = probability
      const code = finiteNumber(row.weatherCode)
      if (code !== undefined) point.weatherCode = code
      hourly.push(point)
    }
  }

  const daily: AiWeatherDailyPoint[] = []
  if (Array.isArray(raw.daily)) {
    for (const item of raw.daily) {
      if (!item || typeof item !== 'object') continue
      const row = item as Record<string, unknown>
      const max = finiteNumber(row.temperatureMaxC)
      const min = finiteNumber(row.temperatureMinC)
      if (typeof row.date !== 'string' || !row.date || max === undefined || min === undefined) continue
      const point: AiWeatherDailyPoint = { date: row.date, temperatureMaxC: max, temperatureMinC: min }
      const probability = finiteNumber(row.precipitationProbabilityMaxPercent)
      if (probability !== undefined) point.precipitationProbabilityMaxPercent = probability
      const code = finiteNumber(row.weatherCode)
      if (code !== undefined) point.weatherCode = code
      if (typeof row.weatherText === 'string' && row.weatherText) point.weatherText = row.weatherText
      daily.push(point)
    }
  }

  const payload: AiWeatherCardPayload = { current: currentCard, hourly, daily }
  const latitude = finiteNumber(raw.latitude)
  if (latitude !== undefined) payload.latitude = latitude
  const longitude = finiteNumber(raw.longitude)
  if (longitude !== undefined) payload.longitude = longitude
  if (typeof raw.timezone === 'string' && raw.timezone) payload.timezone = raw.timezone
  return payload
}

const CHART_TYPES: ReadonlySet<string> = new Set(['line', 'bar', 'area', 'pie'])

/** 图表卡清洗（Issue #179）：type 枚举外整卡丢弃；坏点丢点、空系列丢系列、无系列整卡丢弃。 */
function toChartPayload(raw: Record<string, unknown>): AiChartCardPayload | null {
  if (typeof raw.type !== 'string' || !CHART_TYPES.has(raw.type)) return null
  if (!Array.isArray(raw.series)) return null
  const series: AiChartSeries[] = []
  for (const item of raw.series) {
    if (!item || typeof item !== 'object') continue
    const entry = item as Record<string, unknown>
    if (typeof entry.name !== 'string' || !entry.name) continue
    if (!Array.isArray(entry.points)) continue
    const points: AiChartPoint[] = []
    for (const rawPoint of entry.points) {
      if (!rawPoint || typeof rawPoint !== 'object') continue
      const point = rawPoint as Record<string, unknown>
      const value = finiteNumber(point.value)
      if (typeof point.label !== 'string' || value === undefined) continue
      points.push({ label: point.label, value })
    }
    if (points.length) series.push({ name: entry.name, points })
  }
  if (!series.length) return null
  return { type: raw.type as AiChartType, series }
}

/**
 * VISUALIZATION 信封校验：kind 未知或关键字段缺失 → null（丢弃不渲染）。
 * 执行层已校验过一轮，这里是呈现层防线，顺带把字段清洗成类型口径。
 */
function toVisualization(value: unknown): AiVisualizationEnvelope | null {
  if (!value || typeof value !== 'object') return null
  const raw = value as Record<string, unknown>
  if (!raw.payload || typeof raw.payload !== 'object') return null
  const payload = raw.payload as Record<string, unknown>
  const title = typeof raw.title === 'string' && raw.title.trim() ? raw.title : undefined
  if (raw.kind === 'weather') {
    const weather = toWeatherPayload(payload)
    return weather ? { kind: 'weather', ...(title ? { title } : {}), payload: weather } : null
  }
  if (raw.kind === 'chart') {
    const chart = toChartPayload(payload)
    return chart ? { kind: 'chart', ...(title ? { title } : {}), payload: chart } : null
  }
  return null
}

export function toAgentEvent(event: string, payload: Record<string, unknown>): AiPreviewAgentEvent | null {
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
        citations: toCitations(payload.citations),
      }
    default:
      return null
  }
}

/** 本轮助手消息的内容块：文本累积进尾部文本块，卡片按到达序独立成块（Issue #179）。 */
type PreviewContentBlock =
  | { kind: 'text'; text: string }
  | { kind: 'card'; envelope: AiVisualizationEnvelope }

/** 内容块 → assistant-ui parts 快照：卡片产出 data part（name=visualization），与文本同级按序排布。 */
function snapshotContent(
  blocks: PreviewContentBlock[],
  requestedModel: string,
  citations?: AiCitation[],
) {
  const content = blocks.length
    ? blocks.map((block) =>
        block.kind === 'text'
          ? { type: 'text' as const, text: block.text }
          : { type: 'data' as const, name: 'visualization', data: block.envelope },
      )
    : [{ type: 'text' as const, text: '' }]
  return {
    content,
    metadata: {
      custom: {
        requestedModel,
        // citations 只在 DONE 后随最终帧挂上：正文里的 [cN] 标记编号由执行层
        // 分配且恒定，呈现层按 cite 对应角标，不存在流式中途重排。
        ...(citations?.length ? { citations } : {}),
      },
    },
  }
}

export interface ConsumePreviewSseOptions {
  /** 本轮请求的模型 spec，原样挂在每帧 metadata 上（与 DONE.model 的实际服务模型对照）。 */
  requestedModel: string
  /** 用户取消：读流立即中止并抛 AbortError（与 fetch 被 abort 的行为一致）。 */
  abortSignal?: AbortSignal
  /**
   * 代际守卫：返回 false 表示本流所属的 Agent / 面板已失效（切换 Agent、卸载）。
   * 失效后不再派发任何事件、不再产出快照，并取消读流。
   */
  isCurrent?: () => boolean
  onAgentEvent: (event: AiPreviewAgentEvent) => void
}

/**
 * 消费预览 SSE 响应体：按 `\n\n` 切帧（帧可被任意切分到多个网络分片，UTF-8 多字节可跨片），
 * TEXT_DELTA / VISUALIZATION 累积成助手消息快照，其余事件经 onAgentEvent 派发。
 * 协议与后端一致，不在此处改动。
 */
export async function* consumePreviewSse(body: ReadableStream<Uint8Array>, options: ConsumePreviewSseOptions) {
  const { requestedModel, abortSignal, isCurrent, onAgentEvent } = options
  const stale = () => isCurrent !== undefined && !isCurrent()
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const blocks: PreviewContentBlock[] = []
  let citations: AiCitation[] = []

  const appendText = (delta: string) => {
    if (!delta) return
    const last = blocks[blocks.length - 1]
    if (last?.kind === 'text') last.text += delta
    else blocks.push({ kind: 'text', text: delta })
  }

  // 读流阻塞时也要能被取消：abort 时主动 cancel，挂起的 read() 随即 resolve(done)。
  const onAbort = () => {
    void reader.cancel().catch(() => undefined)
  }
  abortSignal?.addEventListener('abort', onAbort, { once: true })

  try {
    while (true) {
      abortSignal?.throwIfAborted()
      const { done, value } = await reader.read()
      abortSignal?.throwIfAborted()
      if (stale()) return
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const parts = buffer.split('\n\n')
      buffer = parts.pop() ?? ''
      for (const block of parts) {
        if (stale()) return
        const parsed = parseSseBlock(block)
        if (!parsed) continue
        const payload = parseJson(parsed.data)
        if (parsed.event === 'TEXT_DELTA') {
          const delta = payload?.delta
          if (typeof delta === 'string') {
            appendText(delta)
            yield snapshotContent(blocks, requestedModel)
          }
        } else if (parsed.event === 'VISUALIZATION') {
          // 卡片在工具结果后到达：校验通过才成块插入，坏信封静默丢弃（不打断文本流）。
          const envelope = toVisualization(payload)
          if (envelope) {
            blocks.push({ kind: 'card', envelope })
            yield snapshotContent(blocks, requestedModel)
          }
        } else if (parsed.event === 'THINKING') {
          const delta = payload?.delta
          if (typeof delta === 'string' && delta) {
            onAgentEvent({ type: 'thinking', delta })
          }
        } else if (parsed.event === 'ERROR') {
          const code = typeof payload?.errorCode === 'string' ? payload.errorCode : 'SYSTEM_ERROR'
          const message = typeof payload?.message === 'string' ? payload.message : undefined
          onAgentEvent({ type: 'error', errorCode: code, message })
          const shown = message || code
          const last = blocks[blocks.length - 1]
          appendText(last?.kind === 'text' && last.text ? `\n${shown}` : shown)
          yield snapshotContent(blocks, requestedModel)
          return
        } else if (parsed.event === 'CONVERSATION') {
          const rawId = payload?.conversationId
          const id = typeof rawId === 'number' ? rawId : typeof rawId === 'string' ? Number(rawId) : NaN
          if (Number.isFinite(id) && id > 0) {
            onAgentEvent({ type: 'conversation', conversationId: id })
          }
        } else if (payload) {
          const agentEvent = toAgentEvent(parsed.event, payload)
          if (agentEvent) {
            if (agentEvent.type === 'done') citations = agentEvent.citations
            onAgentEvent(agentEvent)
          }
        }
      }
    }
    if (stale()) return
    yield snapshotContent(blocks, requestedModel, citations)
  } finally {
    abortSignal?.removeEventListener('abort', onAbort)
    // 早退（abort / 失效 / 错误 / 消费方提前 return）时释放底层连接；正常读完时 cancel 为空操作。
    void reader.cancel().catch(() => undefined)
  }
}
