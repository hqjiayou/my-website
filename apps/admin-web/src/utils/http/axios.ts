/**
 * 功能注解：统一封装 axios 实例与常用请求方法，供 api 层复用。
 */
import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'

import { clearAccessToken, getAccessToken, setAccessToken } from '@/utils/token'

import { ApiError, type ApiErrorConflict } from './apiError'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'
const API_TIMEOUT = 15_000

const AUTH_LOGIN_PATH = '/auth/admin/login'
const AUTH_REFRESH_PATH = '/auth/admin/refresh'

type SessionExpiredHandler = () => void

let sessionExpiredHandler: SessionExpiredHandler | null = null
let isRefreshing = false
let refreshWaitQueue: Array<(token: string | null) => void> = []

export function setSessionExpiredHandler(handler: SessionExpiredHandler | null) {
  sessionExpiredHandler = handler
}

function isAuthBypassPath(url: string | undefined) {
  if (!url) return false
  return url.includes(AUTH_LOGIN_PATH) || url.includes(AUTH_REFRESH_PATH)
}

function flushRefreshQueue(token: string | null) {
  refreshWaitQueue.forEach((callback) => callback(token))
  refreshWaitQueue = []
}

function triggerSessionExpired() {
  clearAccessToken()
  sessionExpiredHandler?.()
}

export const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT,
  withCredentials: true,
})

axiosClient.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
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

    if (status !== 401 || !originalConfig || originalConfig._retry || isAuthBypassPath(originalConfig.url)) {
      const payload = error.response?.data as
        | { message?: string; code?: number | null; conflicts?: ApiErrorConflict[] }
        | undefined
      const message = payload?.message ?? error.message
      if (status === 401 && originalConfig && isAuthBypassPath(originalConfig.url)) {
        triggerSessionExpired()
      }
      return Promise.reject(
        new ApiError(message, {
          status,
          code: payload?.code,
          conflicts: payload?.conflicts,
        }),
      )
    }

    if (isRefreshing) {
      return new Promise((resolve, reject) => {
        refreshWaitQueue.push((token) => {
          if (!token) {
            reject(
              new ApiError(error.response?.data?.message ?? '登录已过期', {
                status: 401,
              }),
            )
            return
          }
          originalConfig.headers.Authorization = `Bearer ${token}`
          resolve(axiosClient.request(originalConfig))
        })
      })
    }

    originalConfig._retry = true
    isRefreshing = true

    try {
      const response = await axiosClient.post<{ message: string; data: { accessToken: string } }>(AUTH_REFRESH_PATH)
      const nextToken = response.data.data?.accessToken
      if (!nextToken) {
        throw new Error(response.data.message || '刷新登录失败')
      }
      setAccessToken(nextToken)
      flushRefreshQueue(nextToken)
      originalConfig.headers.Authorization = `Bearer ${nextToken}`
      return axiosClient.request(originalConfig)
    } catch (refreshError) {
      flushRefreshQueue(null)
      triggerSessionExpired()
      if (axios.isAxiosError(refreshError)) {
        const payload = refreshError.response?.data as { message?: string; code?: number | null } | undefined
        const message = payload?.message ?? refreshError.message
        return Promise.reject(
          new ApiError(message, {
            status: refreshError.response?.status,
            code: payload?.code,
          }),
        )
      }
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }
  },
)

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
  put<T, B = unknown>(url: string, data?: B, config?: Omit<AxiosRequestConfig, 'url' | 'method' | 'data'>) {
    return requestData<T>({ ...config, method: 'put', url, data })
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
