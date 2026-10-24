import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'

import { apiUrl } from '@/config/env'

import { ApiError } from './apiError'

export const axiosClient = axios.create({
  baseURL: apiUrl(''),
  timeout: 15_000,
})

axiosClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (!axios.isAxiosError(error)) {
      return Promise.reject(error)
    }
    const payload = error.response?.data as { message?: string; code?: number | null } | undefined
    const message = payload?.message ?? error.message
    return Promise.reject(new ApiError(message, { status: error.response?.status, code: payload?.code }))
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
}
