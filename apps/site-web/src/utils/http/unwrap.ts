import { ApiError } from './apiError'
import type { HttpMessageBody } from '@/types/api'

/** 成功主體的 code 省略或為 0。data 為空時視為失敗。 */
export function unwrapData<T>(body: HttpMessageBody<T>): T {
  if (body.code != null && body.code !== 0) {
    throw new ApiError(body.message || '請求失敗', { code: body.code })
  }
  if (body.data == null) {
    throw new ApiError(body.message || '請求失敗', { code: body.code })
  }
  return body.data
}
