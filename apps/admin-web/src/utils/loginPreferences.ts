const REMEMBER_USERNAME_KEY = 'admin_login_remember_username'
const SAVED_USERNAME_KEY = 'admin_login_saved_username'

export function loadRememberUsername(): boolean {
  if (typeof localStorage === 'undefined') return false
  return localStorage.getItem(REMEMBER_USERNAME_KEY) === '1'
}

export function loadSavedUsername(): string {
  if (typeof localStorage === 'undefined') return ''
  return localStorage.getItem(SAVED_USERNAME_KEY) ?? ''
}

export function persistLoginUsername(username: string, remember: boolean) {
  if (typeof localStorage === 'undefined') return
  if (remember) {
    localStorage.setItem(REMEMBER_USERNAME_KEY, '1')
    localStorage.setItem(SAVED_USERNAME_KEY, username.trim())
    return
  }
  localStorage.removeItem(REMEMBER_USERNAME_KEY)
  localStorage.removeItem(SAVED_USERNAME_KEY)
}
