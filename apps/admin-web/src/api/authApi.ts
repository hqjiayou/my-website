/**
 * Admin 认证相关 API：login / refresh / logout / me。
 */
import type { HttpMessageBody, MenuNodeDto } from '@/types/rbac'
import { httpClient } from '@/utils/http/axios'
import { unwrapData } from '@/utils/http/unwrap'

export type { HttpMessageBody, MenuNodeDto }

export interface AdminLoginResponse {
  accessToken: string
  accessTokenExpiresIn: number
}

export interface AdminAuthMeUser {
  userCode: string
  username: string
  displayName: string
  avatarUrl: string | null
  status: string
}

export interface AdminAuthMeDto {
  user: AdminAuthMeUser
  roles: string[]
  permissions: string[]
  menus: MenuNodeDto[]
}

export interface AdminLoginInput {
  username: string
  password: string
}

export const authApi = {
  login(input: AdminLoginInput) {
    return httpClient
      .post<HttpMessageBody<AdminLoginResponse>, AdminLoginInput>('/auth/admin/login', input)
      .then(unwrapData)
  },

  refresh() {
    return httpClient.post<HttpMessageBody<AdminLoginResponse>>('/auth/admin/refresh').then(unwrapData)
  },

  logout() {
    return httpClient.post<HttpMessageBody<null>>('/auth/admin/logout')
  },

  me() {
    return httpClient.get<HttpMessageBody<AdminAuthMeDto>>('/admin/auth/me').then(unwrapData)
  },
}
