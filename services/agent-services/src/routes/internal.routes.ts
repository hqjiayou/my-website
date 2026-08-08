import type { FastifyInstance } from 'fastify'
import { runAgentChat } from '../agent/runner.js'
import type { PromptProfile } from '../agent/prompt-builder.js'
import { createInternalTokenPreHandler } from '../auth/internal-token.js'
import type { AiProvider } from '../providers/provider.js'

interface ChatBody {
  siteId: string
  message: string
  locale?: string
  conversationId?: string | null
  promptProfile?: PromptProfile | null
}

export async function registerInternalRoutes(
  app: FastifyInstance,
  provider: AiProvider,
  options: { internalToken: string },
) {
  app.addHook('preHandler', createInternalTokenPreHandler(options.internalToken))

  app.get('/internal/v1/health', async () => {
    return {
      ok: true,
      service: 'agent-service',
      provider: provider.name,
      providerStatus: provider.name === 'blocked' ? 'BLOCKED' : 'LIVE',
      timestamp: new Date().toISOString(),
    }
  })

  app.post<{ Body: ChatBody }>('/internal/v1/chat', async (request, reply) => {
    const body = request.body
    if (!body?.siteId?.trim() || !body?.message?.trim()) {
      return reply.code(400).send({
        ok: false,
        errorCode: 'BAD_REQUEST',
        message: 'siteId and message are required.',
      })
    }

    const result = await runAgentChat(provider, {
      siteId: body.siteId.trim(),
      message: body.message.trim(),
      promptProfile: body.promptProfile ?? null,
    })

    if (!result.ok) {
      // 无 Provider Key：诚实 503，禁止伪造成功 reply。
      return reply.code(503).send({
        ok: false,
        errorCode: result.errorCode,
        message: result.message,
        providerStatus: result.status,
      })
    }

    return {
      ok: true,
      data: {
        reply: result.text,
        conversationId: body.conversationId ?? null,
        providerStatus: result.status,
      },
    }
  })
}
