import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'

import type { HttpMessageBody, SiteTokenResponse } from '@/types/api'
import { apiUrl } from '@/config/env'
import { clearAccessToken, getAccessToken, setAccessToken } from '@/utils/token'

import { ApiError } from './apiError'
import { unwrapData } from './unwrap'

const AUTH_LOGIN_PATH = '/api/auth/site/login'
const AUTH_REFRESH_PATH = '/api/auth/site/refresh'

type SessionExpiredHandler = () => void

let sessionExpiredHandler: SessionExpiredHandler | null = null
let refreshInFlight: Promise<string> | null = null

export function setSessionExpiredHandler(handler: SessionExpiredHandler | null) {
  sessionExpiredHandler = handler
}

function readErrorMessage(
  payload: { message?: string } | undefined,
  fallback: string,
  status: number | undefined,
): string {
  if (typeof payload?.message === 'string' && payload.message) return payload.message
  if (status == null) return '連不上伺服器'
  if (status === 502 || status === 503 || status === 504) {
    return '連不上 API 閘道。請先啟動本機 gateway（8080），登入還需要 auth（8083）。'
  }
  return fallback
}

function isAuthBypassPath(url: string | undefined) {
  if (!url) return false
  return url.includes(AUTH_LOGIN_PATH) || url.includes(AUTH_REFRESH_PATH)
}

function triggerSessionExpired() {
  clearAccessToken()
  sessionExpiredHandler?.()
}

export const axiosClient = axios.create({
  baseURL: apiUrl(''),
  timeout: 15_000,
  withCredentials: true,
})

axiosClient.interceptors.request.use((config) => {
  if (!isAuthBypassPath(config.url)) {
    const token = getAccessToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

axiosClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) {
      return Promise.reject(error)
    }

    const status = error.response?.status
    const originalConfig = error.config
    const payload = error.response?.data as { message?: string; code?: number | null } | undefined
    const message = readErrorMessage(payload, error.message, status)

    if (status === 401 && originalConfig && isAuthBypassPath(originalConfig.url)) {
      if (originalConfig.url?.includes(AUTH_REFRESH_PATH)) {
        triggerSessionExpired()
      }
      return Promise.reject(new ApiError(message, { status, code: payload?.code }))
    }

    if (status !== 401 || !originalConfig || originalConfig._retry) {
      return Promise.reject(new ApiError(message, { status, code: payload?.code }))
    }

    originalConfig._retry = true
    try {
      const token = await refreshAccessToken()
      originalConfig.headers.Authorization = `Bearer ${token}`
      return axiosClient.request(originalConfig)
    } catch (refreshError) {
      triggerSessionExpired()
      return Promise.reject(refreshError)
    }
  },
)

/** Refresh 只存在 HttpOnly Cookie 裡，請求主體為空。並行的 401 共用同一次重新取得。 */
export function refreshAccessToken(): Promise<string> {
  if (refreshInFlight) return refreshInFlight
  refreshInFlight = axiosClient
    .post<HttpMessageBody<SiteTokenResponse>>(AUTH_REFRESH_PATH)
    .then((response) => {
      const token = unwrapData(response.data).accessToken
      if (!token) {
        throw new ApiError(response.data.message || '重新取得登入失敗')
      }
      setAccessToken(token)
      return token
    })
    .finally(() => {
      refreshInFlight = null
    })
  return refreshInFlight
}

async function requestData<T>(config: AxiosRequestConfig): Promise<T> {
  const response: AxiosResponse<T> = await axiosClient.request<T>(config)
  return response.data
}

export const httpClient = {
  get<T>(url: string, config?: Omit<AxiosRequestConfig, 'url' | 'method'>) {
    return requestData<T>({ ...config, method: 'get', url })
  },
  post<T, B = unknown>(url: string, data?: B, config?: Omit<AxiosRequestConfig, 'url' | 'method' | 'data'>) {
    return requestData<T>({ ...config, method: 'post', url, data })
  },
  patch<T, B = unknown>(url: string, data?: B, config?: Omit<AxiosRequestConfig, 'url' | 'method' | 'data'>) {
    return requestData<T>({ ...config, method: 'patch', url, data })
  },
  delete<T>(url: string, config?: Omit<AxiosRequestConfig, 'url' | 'method'>) {
    return requestData<T>({ ...config, method: 'delete', url })
  },
}

declare module 'axios' {
  export interface InternalAxiosRequestConfig {
    _retry?: boolean
  }
}
