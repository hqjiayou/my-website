import type { Reservation } from '@/mocks/types/reservation'

export const MOCK_RESERVATIONS: Reservation[] = [
  {
    id: 'resv-1001',
    siteId: 'tokyo-car-repair',
    customerName: '山田太郎',
    phone: '090-1111-2222',
    reservedAt: '2026-05-13T03:30:00Z',
    serviceType: '定期保养',
    status: 'confirmed',
    createdAt: '2026-05-10T05:00:00Z',
  },
  {
    id: 'resv-1002',
    siteId: 'sweet-heart-cake',
    customerName: '佐藤花子',
    phone: '090-9876-2222',
    reservedAt: '2026-05-12T07:00:00Z',
    serviceType: '生日蛋糕预订',
    status: 'new',
    createdAt: '2026-05-11T00:20:00Z',
  },
  {
    id: 'resv-1003',
    siteId: 'ginza-salon',
    customerName: '刘美',
    phone: '080-3333-4444',
    reservedAt: '2026-05-18T10:00:00Z',
    serviceType: '护理套餐',
    status: 'cancelled',
    createdAt: '2026-05-09T10:10:00Z',
  },
]
