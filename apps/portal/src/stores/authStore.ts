import { create } from 'zustand'

import { authApi } from '@/api/authApi'
import type { SiteAuthUser } from '@/types/api'
import { refreshAccessToken } from '@/utils/http/axios'
import { clearAccessToken, getAccessToken, setAccessToken, subscribeAccessToken } from '@/utils/token'

export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'unauthenticated'

interface AuthState {
  accessToken: string | null
  user: SiteAuthUser | null
  status: AuthStatus
  bootstrap: () => Promise<void>
  establishSession: (accessToken: string) => Promise<boolean>
  clearAuth: () => void
  logout: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  user: null,
  status: 'idle',

  bootstrap: async () => {
    const status = get().status
    if (status === 'loading' || status === 'authenticated') return
    set({ status: 'loading' })
    try {
      const token = await refreshAccessToken()
      const me = await authApi.me()
      set({ accessToken: token, user: me.user, status: 'authenticated' })
    } catch {
      get().clearAuth()
    }
  },

  establishSession: async (accessToken) => {
    setAccessToken(accessToken)
    set({ accessToken, status: 'loading' })
    try {
      const me = await authApi.me()
      set({ user: me.user, status: 'authenticated' })
      return true
    } catch {
      get().clearAuth()
      return false
    }
  },

  clearAuth: () => {
    clearAccessToken()
    set({ accessToken: null, user: null, status: 'unauthenticated' })
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
