import { FastifyInstance } from 'fastify'

interface ReservationParams {
  siteId: string
}

interface ReservationBody {
  customerName: string
  phone: string
  preferredDate?: string
  message?: string
}

export async function registerReservationRoutes(app: FastifyInstance) {
  app.post<{
    Params: ReservationParams
    Body: ReservationBody
  }>('/api/site/:siteId/reservations', async (request) => {
    const { siteId } = request.params
    const { customerName, phone } = request.body

    return {
      success: true,
      message: 'Reservation accepted (mock).',
      data: {
        reservationId: `resv-${Date.now()}`,
        siteId,
        customerName,
        phone,
        status: 'RECEIVED',
      },
    }
  })
}
