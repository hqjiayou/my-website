import { create } from 'zustand'

import { authApi } from '@/api/authApi'
import type { AgentAuthMe, AgentAuthUser } from '@/types/api'
import { refreshAccessToken } from '@/utils/http/axios'
import { clearAccessToken, getAccessToken, setAccessToken, subscribeAccessToken } from '@/utils/token'

export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'unauthenticated'

type SessionSite = NonNullable<AgentAuthMe['site']>

interface AuthState {
  accessToken: string | null
  user: AgentAuthUser | null
  site: SessionSite | null
  status: AuthStatus
  bootstrap: () => Promise<void>
  establishSession: (accessToken: string) => Promise<boolean>
  clearAuth: () => void
  logout: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  user: null,
  site: null,
  status: 'idle',

  bootstrap: async () => {
    const status = get().status
    if (status === 'loading' || status === 'authenticated') return
    set({ status: 'loading' })
    try {
      const token = await refreshAccessToken()
      const me = await authApi.me()
      set({ accessToken: token, user: me.user, site: me.site ?? null, status: 'authenticated' })
    } catch {
      get().clearAuth()
    }
  },

  establishSession: async (accessToken) => {
    setAccessToken(accessToken)
    set({ accessToken, status: 'loading' })
    try {
      const me = await authApi.me()
      set({ user: me.user, site: me.site ?? null, status: 'authenticated' })
      return true
    } catch {
      get().clearAuth()
      return false
    }
  },

  clearAuth: () => {
    clearAccessToken()
    set({ accessToken: null, user: null, site: null, status: 'unauthenticated' })
  },

  logout: async () => {
    await authApi.logout()
    get().clearAuth()
  },
}))

subscribeAccessToken((token) => {
  useAuthStore.setState({ accessToken: token ?? getAccessToken() })
})

export function selectIsAuthenticated(state: AuthState) {
  return state.status === 'authenticated'
}
