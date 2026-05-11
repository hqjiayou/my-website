import { FastifyInstance } from 'fastify'

export async function registerHealthRoutes(app: FastifyInstance) {
  app.get('/health', async () => {
    return {
      status: 'ok',
      service: 'agent-service',
      timestamp: new Date().toISOString(),
    }
  })
}
