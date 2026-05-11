export type ReservationStatus = 'new' | 'confirmed' | 'done' | 'cancelled'

export interface Reservation {
  id: string
  siteId: string
  customerName: string
  phone: string
  reservedAt: string
  serviceType: string
  status: ReservationStatus
  createdAt: string
}
