/**
 * 统一 API 错误类型，供 axios 拦截器与 React Query 全局处理复用。
 */
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

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}
