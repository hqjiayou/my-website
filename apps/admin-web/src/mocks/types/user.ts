export type RoleKey = 'super-admin' | 'developer' | 'operator' | 'editor' | 'reservation-agent'

export interface Role {
  id: string
  key: RoleKey
  name: string
}

export interface User {
  id: string
  siteId: string
  name: string
  email: string
  roleKey: RoleKey
  status: 'active' | 'inactive'
}
