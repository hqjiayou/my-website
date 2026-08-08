import { toast } from 'sonner'

import i18n from '@/i18n'

import { isApiError } from './apiError'

function t(key: string, fallback: string) {
  if (i18n.isInitialized && i18n.exists(key)) {
    return i18n.t(key)
  }
  return fallback
}

/** 全局 API 错误提示：HTTP 状态、业务码、网络异常。 */
export function handleApiError(error: unknown, options?: { silent?: boolean }) {
  if (options?.silent) return

  if (isApiError(error)) {
    if (error.status === 401) {
      toast.error(t('common:error.unauthorized', '登录已过期，请重新登录'))
      return
    }
    if (error.status === 403 || error.code === 1003) {
      toast.error(t('common:error.forbidden', '无权限访问'))
      return
    }
    if (error.status != null && error.status >= 500) {
      toast.error(t('common:error.server', '服务器错误，请稍后重试'))
      return
    }
    toast.error(error.message || t('common:error.unknown', '请求失败'))
    return
  }

  if (error instanceof Error) {
    if (error.message.toLowerCase().includes('network')) {
      toast.error(t('common:error.network', '网络错误，请稍后重试'))
      return
    }
    toast.error(error.message || t('common:error.unknown', '请求失败'))
    return
  }

  toast.error(t('common:error.unknown', '请求失败'))
}

/** mutation 成功时：message 为 success 则不弹窗，否则 toast。 */
export function handleApiSuccess(message?: string) {
  if (!message || message === 'success') return
  toast.success(message)
}
