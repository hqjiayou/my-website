import { useState, type FormEvent, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Eye, EyeOff, Sparkles } from 'lucide-react'

import { authApi } from '@/api/authApi'
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
  const [showPassword, setShowPassword] = useState(false)
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
    <div className="mx-auto flex min-h-dvh w-full max-w-lg flex-col bg-background text-gpt-ink">
      <main className="safe-top safe-x safe-bottom msg-in mx-auto flex w-full max-w-md flex-1 flex-col justify-center px-5 py-8">
        <div className="flex flex-col items-center text-center">
          <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-gpt-ink text-gpt-canvas">
            <Sparkles size={22} />
          </span>
          <p className="mt-4 text-xs font-medium tracking-widest text-gpt-muted">SITE</p>
          <h1 className="mt-2 text-2xl font-semibold">Agent Portal</h1>
          <p className="mt-2 max-w-xs text-sm leading-6 text-gpt-muted">
            使用站點帳號登入。站點編碼是登入 API 的 siteCode，不是數字站點 ID。
          </p>
        </div>

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
            <span className="relative block">
              <input
                id="password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                autoComplete="current-password"
                maxLength={PASSWORD_MAX}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                className={`${inputClass} pr-11`}
              />
              <button
                type="button"
                onClick={() => setShowPassword((current) => !current)}
                aria-label={showPassword ? '隱藏密碼' : '顯示密碼'}
                title={showPassword ? '隱藏密碼' : '顯示密碼'}
                className="absolute right-1.5 top-1/2 flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-lg text-gpt-muted transition-colors hover:bg-gpt-hover hover:text-gpt-ink"
              >
                {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
              </button>
            </span>
          </Field>

          {formError ? (
            <p className="rounded-control bg-danger-soft px-3 py-2 text-sm text-danger" role="alert">
              {formError}
            </p>
          ) : null}

          <button
            type="submit"
            disabled={submitting}
            className="mt-2 h-12 rounded-full bg-gpt-ink text-base font-medium text-gpt-canvas transition hover:opacity-90 active:scale-[0.99] disabled:opacity-30"
          >
            {submitting ? '正在登入…' : '登入'}
          </button>
        </form>
      </main>
    </div>
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
  'h-12 w-full rounded-xl border border-gpt-line bg-gpt-canvas px-3 text-base font-normal text-gpt-ink outline-none transition focus:border-gpt-muted'
