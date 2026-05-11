import { MOCK_RESERVATIONS } from '@/mocks/db/reservations'
import { mockDelay } from '@/mocks/utils/delay'

export const reservationMockService = {
  async listReservations(siteId?: string) {
    await mockDelay()
    if (!siteId) return [...MOCK_RESERVATIONS]
    return MOCK_RESERVATIONS.filter((item) => item.siteId === siteId)
  },
}
