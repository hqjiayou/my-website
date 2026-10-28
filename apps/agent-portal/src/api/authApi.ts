import type { HttpMessageBody, AgentAuthMe, AgentLoginInput, AgentTokenResponse } from '@/types/api'
import { httpClient } from '@/utils/http/axios'
import { ApiError } from '@/utils/http/apiError'
import { unwrapData } from '@/utils/http/unwrap'

export const authApi = {
  login(input: AgentLoginInput) {
    return httpClient
      .post<HttpMessageBody<AgentTokenResponse>, AgentLoginInput>('/api/auth/agent/login', input)
      .then(unwrapData)
  },

  logout() {
    return httpClient.post<HttpMessageBody<null>>('/api/auth/agent/logout').then((body) => {
      if (body.code != null && body.code !== 0) {
        throw new ApiError(body.message || '登出失敗', { code: body.code })
      }
    })
  },

  me() {
    return httpClient.get<HttpMessageBody<AgentAuthMe>>('/api/agent/auth/me').then(unwrapData)
  },
}
