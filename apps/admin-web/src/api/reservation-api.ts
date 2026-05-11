import { reservationMockService } from '@/mocks/services/reservation.mock'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

function notImplemented(name: string): never {
  throw new Error(`${name} is not implemented yet`)
}

export const reservationApi = USE_MOCK
  ? reservationMockService
  : {
      listReservations: async (_siteId?: string) => notImplemented('site-service listReservations'),
    }
