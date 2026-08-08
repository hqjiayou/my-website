import type { HttpMessageBody } from '@/types/rbac'

import { ApiError } from './apiError'

/** 解析成功响应体；业务码非 0 或 data 为空时抛出 ApiError。 */
export function unwrapData<T>(body: HttpMessageBody<T>): T {
  if (body.code != null && body.code !== 0) {
    throw new ApiError(body.message || 'Request failed', { code: body.code })
  }
  if (body.data == null) {
    throw new ApiError(body.message || 'Request failed')
  }
  return body.data
}
