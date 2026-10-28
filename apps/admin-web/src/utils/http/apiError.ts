/**
 * 统一 API 错误类型，供 axios 拦截器与 React Query 全局处理复用。
 */
export interface ApiErrorConflict {
  agentId: number
  dimensions: number
}

export class ApiError extends Error {
  readonly status?: number
  readonly code?: number | null
  readonly conflicts?: ApiErrorConflict[]

  constructor(
    message: string,
    options?: { status?: number; code?: number | null; conflicts?: ApiErrorConflict[] },
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = options?.status
    this.code = options?.code
    this.conflicts = options?.conflicts
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
