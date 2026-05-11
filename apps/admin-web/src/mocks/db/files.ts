import type { FileAsset } from '@/mocks/types/file'

export const MOCK_FILES: FileAsset[] = [
  {
    id: 'file-car-logo',
    siteId: 'tokyo-car-repair',
    name: 'car-logo.png',
    url: 'https://example.com/mock/car-logo.png',
    mimeType: 'image/png',
    size: 245_120,
    status: 'ready',
    createdAt: '2026-05-02T04:20:00Z',
  },
  {
    id: 'file-cake-banner',
    siteId: 'sweet-heart-cake',
    name: 'cake-banner.webp',
    url: 'https://example.com/mock/cake-banner.webp',
    mimeType: 'image/webp',
    size: 381_550,
    status: 'processing',
    createdAt: '2026-05-08T03:30:00Z',
  },
]
