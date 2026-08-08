import type { FastifyInstance } from 'fastify'

/**
 * reservation / site-config 已退出生产路径；仅当 ENABLE_DEV_MOCK_ROUTES=true 时挂载，供本地考古。
 */
export async function registerDevOnlyRoutes(app: FastifyInstance) {
  app.post('/api/site/:siteId/reservations', async (request) => {
    const { siteId } = request.params as { siteId: string }
    const body = (request.body ?? {}) as { customerName?: string; phone?: string }
    return {
      success: true,
      message: 'Reservation accepted (DEV_ONLY mock; not a production path).',
      data: {
        reservationId: `resv-${Date.now()}`,
        siteId,
        customerName: body.customerName,
        phone: body.phone,
        status: 'RECEIVED',
      },
    }
  })

  app.get('/api/site/:domain/config', async (request) => {
    const { domain } = request.params as { domain: string }
    return {
      success: true,
      message: 'DEV_ONLY mock; not a production path.',
      data: {
        siteId: 'site-dev-only',
        domain,
        siteName: 'Dev Only Site',
        locale: 'ja-JP',
        templateKey: 'garage',
        sections: ['hero', 'services'],
      },
    }
  })
}
