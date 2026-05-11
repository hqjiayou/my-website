import { FastifyInstance } from 'fastify'
import { runAgentChat } from '../agent/runner.js'

interface ChatParams {
  siteId: string
}

interface ChatBody {
  message: string
}

export async function registerChatRoutes(app: FastifyInstance) {
  app.post<{
    Params: ChatParams
    Body: ChatBody
  }>('/api/site/:siteId/ai/chat', async (request) => {
    const { siteId } = request.params
    const { message } = request.body

    const reply = await runAgentChat(siteId, message)

    return {
      success: true,
      data: {
        siteId,
        reply,
      },
    }
  })
}
