import type { FastifyInstance } from 'fastify'

/** 进程存活探活（无鉴权、无业务）；生产契约探活见 /internal/v1/health。 */
export async function registerHealthRoutes(app: FastifyInstance) {
  app.get('/health', async () => {
    return {
      status: 'ok',
      service: 'agent-service',
      timestamp: new Date().toISOString(),
    }
  })
}
