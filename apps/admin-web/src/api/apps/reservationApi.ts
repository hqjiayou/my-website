/**
 * 站点预约 API。
 * Reservation（list/get/create/status/delete）→ 始终 REAL `/admin/sites/{siteId}/reservations`，不受 USE_MOCK 门控。
 */
import type {
  CreateReservationInput,
  PageResult,
  ReservationDetail,
  ReservationListItem,
  ReservationListQuery,
  UpdateReservationStatusInput,
} from '@/types/reservation'
import type { HttpMessageBody } from '@/types/rbac'
import { ApiError } from '@/utils/http/apiError'
import { httpClient } from '@/utils/http/axios'
import { unwrapData } from '@/utils/http/unwrap'

function toQueryString(query: Record<string, string | number | undefined>) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === '') continue
    params.set(key, String(value))
  }
  const serialized = params.toString()
  return serialized ? `?${serialized}` : ''
}

function unwrapVoid(body: HttpMessageBody<null | void>) {
  if (body.code != null && body.code !== 0) {
    throw new ApiError(body.message || 'Request failed', { code: body.code })
  }
}

function reservationBase(siteId: number) {
  return `/admin/sites/${siteId}/reservations`
}

export const reservationApi = {
  listReservations(
    siteId: number,
    query: ReservationListQuery = {},
  ): Promise<PageResult<ReservationListItem>> {
    const qs = toQueryString({
      pageNum: query.pageNum ?? 1,
      pageSize: query.pageSize ?? 20,
      status: query.status,
    })
    return httpClient
      .get<HttpMessageBody<PageResult<ReservationListItem>>>(`${reservationBase(siteId)}${qs}`)
      .then(unwrapData)
  },

  getReservation(siteId: number, id: number): Promise<ReservationDetail> {
    return httpClient
      .get<HttpMessageBody<ReservationDetail>>(`${reservationBase(siteId)}/${id}`)
      .then(unwrapData)
  },

  createReservation(siteId: number, input: CreateReservationInput): Promise<ReservationDetail> {
    return httpClient
      .post<HttpMessageBody<ReservationDetail>, CreateReservationInput>(
        reservationBase(siteId),
        input,
      )
      .then(unwrapData)
  },

  updateReservationStatus(
    siteId: number,
    id: number,
    input: UpdateReservationStatusInput,
  ): Promise<ReservationDetail> {
    return httpClient
      .patch<HttpMessageBody<ReservationDetail>, UpdateReservationStatusInput>(
        `${reservationBase(siteId)}/${id}/status`,
        input,
      )
      .then(unwrapData)
  },

  deleteReservation(siteId: number, id: number): Promise<void> {
    return httpClient
      .delete<HttpMessageBody<null>>(`${reservationBase(siteId)}/${id}`)
      .then(unwrapVoid)
  },
}

export type {
  CreateReservationInput,
  ReservationDetail,
  ReservationListItem,
  ReservationListQuery,
  ReservationStatus,
  UpdateReservationStatusInput,
} from '@/types/reservation'

export {
  RESERVATION_ALLOWED_TRANSITIONS,
  RESERVATION_STATUS,
  RESERVATION_STATUS_VALUES,
  getReservationStatusOptions,
} from '@/types/reservation'
