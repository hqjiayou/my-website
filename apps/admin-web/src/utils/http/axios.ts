/**
 * 功能注解：统一封装 axios 实例与常用请求方法，供 api 层复用。
 */
import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'
const API_TIMEOUT = 15_000

export const axiosClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: API_TIMEOUT,
})

axiosClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error)) {
      const message = error.response?.data?.message ?? error.message
      return Promise.reject(new Error(message))
    }
    return Promise.reject(error)
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
}
