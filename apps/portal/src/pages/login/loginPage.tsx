import { useState, type FormEvent, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { authApi } from '@/api/authApi'
import { Screen } from '@/components/screen'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import { errorMessage } from '@/utils/http/apiError'

const SITE_CODE_MAX = 64
const USERNAME_MAX = 128
const PASSWORD_MAX = 128

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const establishSession = useAuthStore((state) => state.establishSession)
  const [siteCode, setSiteCode] = useState('')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextSiteCode = siteCode.trim()
    const nextUsername = username.trim()
    if (!nextSiteCode || !nextUsername || !password) {
      setFormError('請填寫站點編碼、使用者名稱和密碼')
      return
    }
    setSubmitting(true)
    setFormError(null)
    try {
      const { accessToken } = await authApi.login({
        siteCode: nextSiteCode,
        username: nextUsername,
        password,
      })
      const loaded = await establishSession(accessToken)
      if (!loaded) {
        setFormError('登入已成功，但沒有讀到目前使用者')
        return
      }
      navigate(safeNextPath(location.state), { replace: true })
    } catch (error) {
      setFormError(errorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Screen>
      <main className="safe-top safe-x safe-bottom flex flex-1 flex-col justify-center px-5 py-8">
        <p className="text-xs font-medium tracking-widest text-primary">SITE</p>
        <h1 className="mt-2 text-2xl font-semibold">站點門戶</h1>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">使用站點帳號登入。站點編碼是登入 API 的 siteCode，不是數字站點 ID。</p>

        <form className="mt-8 flex flex-col gap-4" onSubmit={onSubmit} noValidate>
          <Field label="站點編碼" id="site-code">
            <input
              id="site-code"
              name="siteCode"
              autoComplete="organization"
              maxLength={SITE_CODE_MAX}
              value={siteCode}
              onChange={(event) => setSiteCode(event.target.value)}
              className={inputClass}
            />
          </Field>
          <Field label="使用者名稱" id="username">
            <input
              id="username"
              name="username"
              autoComplete="username"
              maxLength={USERNAME_MAX}
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              className={inputClass}
            />
          </Field>
          <Field label="密碼" id="password">
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              maxLength={PASSWORD_MAX}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              className={inputClass}
            />
          </Field>

          {formError ? (
            <p className="rounded-control bg-danger-soft px-3 py-2 text-sm text-danger" role="alert">
              {formError}
            </p>
          ) : null}

          <button
            type="submit"
            disabled={submitting}
            className="mt-2 h-12 rounded-control bg-primary text-base font-medium text-primary-foreground disabled:opacity-60"
          >
            {submitting ? '正在登入…' : '登入'}
          </button>
        </form>
      </main>
    </Screen>
  )
}

function Field({ id, label, children }: { id: string; label: string; children: ReactNode }) {
  return (
    <label htmlFor={id} className="flex flex-col gap-1.5 text-sm font-medium">
      {label}
      {children}
    </label>
  )
}

function safeNextPath(state: unknown): string {
  if (typeof state !== 'object' || state === null || !('from' in state)) return ROUTES.home
  const from = state.from
  if (typeof from !== 'string' || !from.startsWith('/') || from.startsWith('//') || from === ROUTES.login) {
    return ROUTES.home
  }
  return from
}

const inputClass =
  'h-12 w-full rounded-control border border-border bg-surface px-3 text-base font-normal text-foreground outline-none focus:border-primary'
