/**
 * PR-6：Agent 预览 SSE 解析 / 消费单测（纯逻辑，无 DOM、无网络）。
 * 运行：node --experimental-strip-types scripts/check-preview-sse.ts（npm run test:sse）
 */
import assert from 'node:assert/strict'
import test from 'node:test'

import { consumePreviewSse, parseSseBlock, type ConsumePreviewSseOptions } from '../src/pages/agents/agentAiPreviewSse.ts'

type AgentEvent = Parameters<ConsumePreviewSseOptions['onAgentEvent']>[0]

const encoder = new TextEncoder()

function frame(event: string, payload: unknown): string {
  return `event: ${event}\ndata: ${JSON.stringify(payload)}\n\n`
}

function streamOf(chunks: Uint8Array[]): ReadableStream<Uint8Array> {
  let index = 0
  return new ReadableStream<Uint8Array>({
    pull(controller) {
      if (index < chunks.length) controller.enqueue(chunks[index++])
      else controller.close()
    },
  })
}

/** 把整段字节按给定尺寸序列切成网络分片（尺寸循环使用）。 */
function splitBytes(bytes: Uint8Array, sizes: number[]): Uint8Array[] {
  const chunks: Uint8Array[] = []
  let offset = 0
  let i = 0
  while (offset < bytes.length) {
    const size = sizes[i++ % sizes.length]
    chunks.push(bytes.slice(offset, offset + size))
    offset += size
  }
  return chunks
}

async function consume(chunks: Uint8Array[], extra: Partial<ConsumePreviewSseOptions> = {}) {
  const events: AgentEvent[] = []
  const snapshots = []
  for await (const snapshot of consumePreviewSse(streamOf(chunks), {
    requestedModel: 'openai:gpt-4o',
    onAgentEvent: (event) => events.push(event),
    ...extra,
  })) {
    snapshots.push(snapshot)
  }
  return { events, snapshots }
}

function textOfSnapshot(snapshot: { content: Array<{ type: string; text?: string }> }): string {
  return snapshot.content
    .filter((part) => part.type === 'text')
    .map((part) => part.text ?? '')
    .join('')
}

const NORMAL_BODY =
  frame('START', { skillsApplied: [], skillsMissing: [] }) +
  frame('TEXT_DELTA', { delta: 'Hello ' }) +
  frame('TEXT_DELTA', { delta: 'world' }) +
  frame('DONE', { model: 'openai:gpt-4o' })

test('parseSseBlock: event / 多行 data / 缺省事件名 / 无 data', () => {
  assert.deepEqual(parseSseBlock('event: TEXT_DELTA\ndata: {"delta":"a"}'), {
    event: 'TEXT_DELTA',
    data: '{"delta":"a"}',
  })
  assert.deepEqual(parseSseBlock('data: a\ndata: b'), { event: 'message', data: 'a\nb' })
  assert.equal(parseSseBlock('event: PING'), null)
  assert.equal(parseSseBlock(': comment only'), null)
})

test('正常分片：TEXT_DELTA 累积 + START/DONE 事件', async () => {
  const { events, snapshots } = await consume([encoder.encode(NORMAL_BODY)])
  assert.deepEqual(
    snapshots.map(textOfSnapshot),
    ['Hello ', 'Hello world', 'Hello world'],
    '每个 delta 一帧，DONE 后补一帧最终快照',
  )
  assert.deepEqual(events.map((event) => event.type), ['start', 'done'])
  const done = events[1]
  assert.equal(done.type === 'done' && done.model, 'openai:gpt-4o')
  assert.equal(done.type === 'done' && done.usage, null)
})

test('SSE 帧被任意切分到多个网络分片，结果与整块一致', async () => {
  const bytes = encoder.encode(NORMAL_BODY)
  const whole = await consume([bytes])
  for (const sizes of [[1], [2], [3, 1, 7], [5, 11], [bytes.length - 1, 1]]) {
    const split = await consume(splitBytes(bytes, sizes))
    assert.deepEqual(split.snapshots, whole.snapshots, `sizes=${sizes.join(',')} snapshots`)
    assert.deepEqual(split.events, whole.events, `sizes=${sizes.join(',')} events`)
  }
})

test('多字节 UTF-8（中文 / emoji）跨分片不乱码', async () => {
  const text = '你好，世界🙂 こんにちは'
  const bytes = encoder.encode(frame('TEXT_DELTA', { delta: text }) + frame('DONE', {}))
  // 逐字节切：每个多字节字符都会被劈开。
  const { snapshots } = await consume(splitBytes(bytes, [1]))
  assert.equal(textOfSnapshot(snapshots[snapshots.length - 1]), text)
  assert.ok(!textOfSnapshot(snapshots[snapshots.length - 1]).includes('\uFFFD'))
})

test('ERROR 事件：派发错误、文本追加错误信息、其后帧不再处理', async () => {
  const body =
    frame('TEXT_DELTA', { delta: 'partial' }) +
    frame('ERROR', { errorCode: 'MCP_UNAVAILABLE', message: 'mcp down' }) +
    frame('TEXT_DELTA', { delta: 'ignored' }) +
    frame('DONE', {})
  const { events, snapshots } = await consume([encoder.encode(body)])
  assert.deepEqual(events, [{ type: 'error', errorCode: 'MCP_UNAVAILABLE', message: 'mcp down' }])
  assert.equal(textOfSnapshot(snapshots[snapshots.length - 1]), 'partial\nmcp down')

  const bare = await consume([encoder.encode(frame('ERROR', {}))])
  assert.deepEqual(bare.events, [{ type: 'error', errorCode: 'SYSTEM_ERROR', message: undefined }])
  assert.equal(textOfSnapshot(bare.snapshots[0]), 'SYSTEM_ERROR')
})

test('AbortSignal 取消：读流阻塞时立即抛 AbortError，并取消底层流', async () => {
  let cancelled = false
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(encoder.encode(frame('TEXT_DELTA', { delta: 'hi' })))
      // 不 close：模拟模型还在生成。
    },
    cancel() {
      cancelled = true
    },
  })
  const controller = new AbortController()
  const snapshots: unknown[] = []
  await assert.rejects(
    async () => {
      for await (const snapshot of consumePreviewSse(body, {
        requestedModel: 'm',
        abortSignal: controller.signal,
        onAgentEvent: () => undefined,
      })) {
        snapshots.push(snapshot)
        setTimeout(() => controller.abort(), 0)
      }
    },
    (error: unknown) => error instanceof Error && error.name === 'AbortError',
  )
  assert.equal(snapshots.length, 1)
  assert.equal(cancelled, true, '底层流应被取消')

  const already = new AbortController()
  already.abort()
  await assert.rejects(
    consume([encoder.encode(NORMAL_BODY)], { abortSignal: already.signal }),
    (error: unknown) => error instanceof Error && error.name === 'AbortError',
  )
})

test('中止之后新一轮可以正常进行（无跨轮共享状态）', async () => {
  const first = new AbortController()
  first.abort()
  await assert.rejects(consume([encoder.encode(NORMAL_BODY)], { abortSignal: first.signal }))

  const second = new AbortController()
  const { events, snapshots } = await consume([encoder.encode(NORMAL_BODY)], { abortSignal: second.signal })
  assert.equal(textOfSnapshot(snapshots[snapshots.length - 1]), 'Hello world')
  assert.deepEqual(events.map((event) => event.type), ['start', 'done'])
})

test('请求模型与实际服务模型不同：快照保留请求 spec，DONE.model 为实际模型', async () => {
  const body =
    frame('TEXT_DELTA', { delta: 'x' }) + frame('DONE', { model: '  openai:gpt-4o-mini ' })
  const { events, snapshots } = await consume([encoder.encode(body)], { requestedModel: 'openai:gpt-4o' })
  for (const snapshot of snapshots) {
    assert.equal(snapshot.metadata.custom.requestedModel, 'openai:gpt-4o')
  }
  const done = events.find((event) => event.type === 'done')
  assert.ok(done && done.type === 'done')
  assert.equal(done.model, 'openai:gpt-4o-mini', 'trim 后的实际服务模型')

  const noModel = await consume([encoder.encode(frame('DONE', {}))])
  const noModelDone = noModel.events[0]
  assert.ok(noModelDone.type === 'done')
  assert.equal(noModelDone.model, null)
})

test('工具 / skills / todo / usage / citations / conversation / thinking 事件', async () => {
  const todos = [{ content: 'step 1', status: 'pending' }]
  const body =
    frame('CONVERSATION', { conversationId: 42 }) +
    frame('START', { skillsApplied: ['a'], skillsMissing: ['b'] }) +
    frame('THINKING', { delta: 'hmm' }) +
    frame('TOOL_START', { toolCallId: 't1', toolName: 'kb_search' }) +
    frame('TOOL_RESULT', {
      toolCallId: 't1',
      toolName: 'kb_search',
      ok: true,
      summary: { query: 'q', hitCount: 3 },
    }) +
    frame('TEXT_DELTA', { delta: 'answer [c1]' }) +
    frame('DONE', {
      todos,
      usage: { requests: 2, toolCalls: 1, inputTokens: 10, outputTokens: 20 },
      model: 'openai:gpt-4o',
      citations: [
        { sourceId: 's1', chunkIndex: 0, score: 0.9, cite: 1, title: 'Doc' },
        { sourceId: '', chunkIndex: 0, score: 0.5 },
      ],
    })
  const { events, snapshots } = await consume([encoder.encode(body)])
  assert.deepEqual(events.map((event) => event.type), [
    'conversation',
    'start',
    'thinking',
    'toolStart',
    'toolResult',
    'done',
  ])
  assert.deepEqual(events[0], { type: 'conversation', conversationId: 42 })
  assert.deepEqual(events[1], { type: 'start', skillsApplied: ['a'], skillsMissing: ['b'] })
  assert.deepEqual(events[3], { type: 'toolStart', toolCallId: 't1', toolName: 'kb_search' })
  assert.deepEqual(events[4], {
    type: 'toolResult',
    toolCallId: 't1',
    toolName: 'kb_search',
    ok: true,
    summary: { query: 'q', hitCount: 3 },
  })
  const done = events[5]
  assert.ok(done.type === 'done')
  assert.deepEqual(done.todos, todos)
  assert.deepEqual(done.usage, { requests: 2, toolCalls: 1, inputTokens: 10, outputTokens: 20 })
  assert.equal(done.citations.length, 1, '缺 sourceId 的坏引用被丢弃')

  // citations 只挂在 DONE 后的最终快照上。
  const last = snapshots[snapshots.length - 1]
  assert.equal(last.metadata.custom.citations?.length, 1)
  assert.equal(snapshots[0].metadata.custom.citations, undefined)
})

test('代际守卫：Agent 切换（isCurrent=false）后旧流事件与快照全部丢弃，并取消读流', async () => {
  let cancelled = false
  let generation = 1
  let lateSent = false
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(encoder.encode(frame('START', { skillsApplied: [], skillsMissing: [] })))
      controller.enqueue(encoder.encode(frame('TEXT_DELTA', { delta: 'old agent' })))
    },
    pull(controller) {
      // 切换后才到达的迟到分片；不 close，保证取消能传到底层（已关闭的流 cancel 不会触达 source）。
      if (lateSent) return
      lateSent = true
      controller.enqueue(
        encoder.encode(
          frame('CONVERSATION', { conversationId: 7 }) +
            frame('TEXT_DELTA', { delta: ' late' }) +
            frame('DONE', { todos: [{ content: 'leak', status: 'pending' }] }),
        ),
      )
    },
    cancel() {
      cancelled = true
    },
  })
  const events: AgentEvent[] = []
  const snapshots = []
  for await (const snapshot of consumePreviewSse(body, {
    requestedModel: 'm',
    isCurrent: () => generation === 1,
    onAgentEvent: (event) => events.push(event),
  })) {
    snapshots.push(snapshot)
    generation = 2 // 用户在第一帧文本之后切到了另一个 Agent
  }
  assert.deepEqual(events.map((event) => event.type), ['start'], '切换前的事件已派发，之后的丢弃')
  assert.equal(snapshots.length, 1)
  assert.equal(textOfSnapshot(snapshots[0]), 'old agent')
  assert.equal(cancelled, true)
})
