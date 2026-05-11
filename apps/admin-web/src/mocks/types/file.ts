export type FileAssetStatus = 'ready' | 'processing'

export interface FileAsset {
  id: string
  siteId: string
  name: string
  url: string
  mimeType: string
  size: number
  status: FileAssetStatus
  createdAt: string
}
