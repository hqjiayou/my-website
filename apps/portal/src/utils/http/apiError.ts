export class ApiError extends Error {
  readonly status?: number
  readonly code?: number | null

  constructor(message: string, options?: { status?: number; code?: number | null }) {
    super(message)
    this.name = 'ApiError'
    this.status = options?.status
    this.code = options?.code
  }
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message || '請求失敗'
  if (error instanceof Error && error.message) return error.message
  return '請求失敗'
}
