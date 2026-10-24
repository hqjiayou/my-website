import type { HttpMessageBody, SiteAuthMe, SiteLoginInput, SiteTokenResponse } from '@/types/api'
import { httpClient } from '@/utils/http/axios'
import { ApiError } from '@/utils/http/apiError'
import { unwrapData } from '@/utils/http/unwrap'

export const authApi = {
  login(input: SiteLoginInput) {
    return httpClient
      .post<HttpMessageBody<SiteTokenResponse>, SiteLoginInput>('/api/auth/site/login', input)
      .then(unwrapData)
  },

  logout() {
    return httpClient.post<HttpMessageBody<null>>('/api/auth/site/logout').then((body) => {
      if (body.code != null && body.code !== 0) {
        throw new ApiError(body.message || '登出失敗', { code: body.code })
      }
    })
  },

  me() {
    return httpClient.get<HttpMessageBody<SiteAuthMe>>('/api/site/auth/me').then(unwrapData)
  },
}
