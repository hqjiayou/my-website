import type { ChatModelAdapter } from '@assistant-ui/react'

import { streamPreviewChat } from '@/api/apps/aiApi'
import type { AiPreviewHistoryMessage } from '@/types/ai'

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

export function createJavaPreviewAdapter(siteId: number): ChatModelAdapter {
  return {
    async *run({ messages, abortSignal }) {
      const last = messages[messages.length - 1]
      const message = last ? textOf(last).trim() : ''
      if (!message) {
        yield { content: [{ type: 'text' as const, text: '' }] }
        return
      }

      const history: AiPreviewHistoryMessage[] = messages.slice(0, -1).flatMap((item) => {
        const role: AiPreviewHistoryMessage['role'] | null =
          item.role === 'assistant' ? 'ASSISTANT' : item.role === 'user' ? 'USER' : null
        if (!role) return []
        const content = textOf(item).trim()
        return content ? [{ role, content }] : []
      })

      const response = await streamPreviewChat(
        siteId,
        { message, history: history.length ? history : undefined },
        abortSignal,
      )
      if (!response.ok || !response.body) {
        let detail = `preview stream failed (${response.status})`
        try {
          const payload = (await response.json()) as { message?: string; errorCode?: string }
          detail = payload.errorCode ?? payload.message ?? detail
        } catch {
          // keep default
        }
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
          if (parsed.event === 'TEXT_DELTA') {
            try {
              const payload = JSON.parse(parsed.data) as { delta?: string }
              text += payload.delta ?? ''
              yield { content: [{ type: 'text' as const, text }] }
            } catch {
              // ignore malformed delta
            }
          } else if (parsed.event === 'ERROR') {
            let code = 'SYSTEM_ERROR'
            try {
              code = (JSON.parse(parsed.data) as { errorCode?: string }).errorCode ?? code
            } catch {
              // keep default
            }
            throw new Error(code)
          }
        }
      }

      yield { content: [{ type: 'text' as const, text }] }
    },
  }
}
