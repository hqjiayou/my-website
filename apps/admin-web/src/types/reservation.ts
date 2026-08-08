/** Admin Reservation（Phase 8）真实契约类型；对齐 AdminReservationDtos。 */

import type { PageResult } from '@/types/site'

export type ReservationStatus = 'NEW' | 'CONFIRMED' | 'DONE' | 'CANCELLED'

export const RESERVATION_STATUS = {
  NEW: 'NEW',
  CONFIRMED: 'CONFIRMED',
  DONE: 'DONE',
  CANCELLED: 'CANCELLED',
} as const

export const RESERVATION_STATUS_VALUES: ReservationStatus[] = [
  'NEW',
  'CONFIRMED',
  'DONE',
  'CANCELLED',
]

/** 合法 status 迁移（与后端 AdminReservationConstants 一致）。 */
export const RESERVATION_ALLOWED_TRANSITIONS: Record<ReservationStatus, ReservationStatus[]> = {
  NEW: ['CONFIRMED', 'CANCELLED'],
  CONFIRMED: ['DONE', 'CANCELLED'],
  DONE: [],
  CANCELLED: [],
}

export function getReservationStatusOptions(current: string): ReservationStatus[] {
  const currentStatus = RESERVATION_STATUS_VALUES.includes(current as ReservationStatus)
    ? (current as ReservationStatus)
    : null
  if (!currentStatus) return [...RESERVATION_STATUS_VALUES]
  const next = RESERVATION_ALLOWED_TRANSITIONS[currentStatus]
  return [currentStatus, ...next]
}

export interface ReservationListQuery {
  pageNum?: number
  pageSize?: number
  status?: string
}

export interface ReservationListItem {
  id: number
  siteId: number
  customerName: string
  phone: string
  serviceType: string
  reservedAt: string
  status: string
  version: number
  createdAt: string
  updatedAt: string
}

export interface ReservationDetail {
  id: number
  siteId: number
  customerName: string
  phone: string
  serviceType: string
  reservedAt: string
  status: string
  note: string | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface CreateReservationInput {
  customerName: string
  phone: string
  serviceType: string
  reservedAt: string
  note?: string | null
}

export interface UpdateReservationStatusInput {
  status: string
  version: number
}

export type { PageResult }
