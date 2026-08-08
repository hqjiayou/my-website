import { create } from 'zustand'

import { authApi, type AdminAuthMeDto, type AdminAuthMeUser } from '@/api/authApi'
import type { MenuNodeDto } from '@/types/rbac'
import { clearAccessToken, getAccessToken, setAccessToken } from '@/utils/token'

export type AuthStatus = 'idle' | 'loading' | 'authenticated' | 'unauthenticated'

interface AuthState {
  accessToken: string | null
  user: AdminAuthMeUser | null
  roles: string[]
  permissions: string[]
  menus: MenuNodeDto[]
  status: AuthStatus

  setToken: (token: string) => void
  setCurrentUser: (payload: AdminAuthMeDto) => void
  loadCurrentUser: () => Promise<boolean>
  clearAuth: () => void
  logout: () => Promise<void>
}

const initialToken = getAccessToken()

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: initialToken,
  user: null,
  roles: [],
  permissions: [],
  menus: [],
  status: initialToken ? 'idle' : 'unauthenticated',

  setToken: (token) => {
    setAccessToken(token)
    set({ accessToken: token })
  },

  setCurrentUser: (payload) => {
    set({
      user: payload.user,
      roles: payload.roles,
      permissions: payload.permissions,
      menus: payload.menus ?? [],
      status: 'authenticated',
    })
  },

  loadCurrentUser: async () => {
    const token = get().accessToken ?? getAccessToken()
    if (!token) {
      set({ status: 'unauthenticated' })
      return false
    }

    set({ status: 'loading', accessToken: token })
    try {
      const payload = await authApi.me()
      get().setCurrentUser(payload)
      return true
    } catch {
      get().clearAuth()
      return false
    }
  },

  clearAuth: () => {
    clearAccessToken()
    set({
      accessToken: null,
      user: null,
      roles: [],
      permissions: [],
      menus: [],
      status: 'unauthenticated',
    })
  },

  logout: async () => {
    try {
      await authApi.logout()
    } catch {
      // 网络失败也清理本地态
    } finally {
      get().clearAuth()
    }
  },
}))

export function selectIsAuthenticated(state: AuthState) {
  return state.status === 'authenticated'
}

export function selectHasToken(state: AuthState) {
  return Boolean(state.accessToken ?? getAccessToken())
}
