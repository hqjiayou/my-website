/**
 * 功能注解：预约管理相关 API（预约列表查询）。
 */
import { reservationMockService } from '@/mocks/services/reservation.mock'
import type { Reservation } from '@/mocks/types/reservation'
import { httpClient } from '@/utils/http/axios'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

const reservationHttpService: typeof reservationMockService = {
  listReservations: (siteId?: string) => httpClient.get<Reservation[]>('/site/reservations', { params: { siteId } }),
}

export const reservationApi = USE_MOCK ? reservationMockService : reservationHttpService
