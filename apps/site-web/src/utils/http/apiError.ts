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

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404
}
