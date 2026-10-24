import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { Screen } from '@/components/screen'
import { readAccessTokenSiteId, readPortalSiteId } from '@/config/env'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import { errorMessage } from '@/utils/http/apiError'

export function HomePage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const accessToken = useAuthStore((state) => state.accessToken)
  const logout = useAuthStore((state) => state.logout)
  const site = readPortalSiteId()
  const tokenSiteId = readAccessTokenSiteId(accessToken)
  const [logoutError, setLogoutError] = useState<string | null>(null)
  const [loggingOut, setLoggingOut] = useState(false)

  async function onLogout() {
    setLoggingOut(true)
    setLogoutError(null)
    try {
      await logout()
      navigate(ROUTES.login, { replace: true })
    } catch (error) {
      setLogoutError(errorMessage(error))
      setLoggingOut(false)
    }
  }

  const displayName = user?.displayName || user?.username || '已登入'
  const siteMismatch = site.ok && tokenSiteId !== null && tokenSiteId !== site.siteId

  return (
    <Screen>
      <header className="safe-top safe-x flex items-center justify-between px-5 pt-4">
        <p className="text-sm text-muted-foreground">{displayName}</p>
        <button
          type="button"
          onClick={() => void onLogout()}
          disabled={loggingOut}
          className="h-10 rounded-full px-3 text-sm text-primary disabled:opacity-60"
        >
          {loggingOut ? '正在登出…' : '登出'}
        </button>
      </header>

      <main className="safe-x safe-bottom flex flex-1 flex-col px-5 pb-8 pt-8">
        <h1 className="text-3xl font-semibold tracking-tight">站點門戶</h1>
        <p className="mt-3 text-sm leading-6 text-muted-foreground">在手機上查看這個站點的 AI 對話和對話歷史。</p>

        <section className="mt-8 rounded-2xl border border-border bg-card p-4">
          <p className="text-xs text-muted-foreground">目前站點</p>
          {site.ok ? (
            <p className="mt-1 text-lg font-medium">站點 ID {site.siteId}</p>
          ) : (
            <p className="mt-1 text-sm leading-6 text-danger">{site.message}</p>
          )}
          <p className="mt-2 text-sm leading-6 text-muted-foreground">
            契約沒有站點清單，也沒有回傳站點名稱的 API，所以這裡只顯示環境變數裡的站點 ID。
          </p>
          {tokenSiteId ? <p className="mt-2 text-sm text-muted-foreground">登入權杖 site_id：{tokenSiteId}</p> : null}
          {siteMismatch ? (
            <p className="mt-2 text-sm leading-6 text-danger">
              權杖裡的 site_id 與 VITE_PORTAL_SITE_ID 不一致。站點 AI 會依路徑上的站點 ID 驗證，存取會被拒絕。
            </p>
          ) : null}
        </section>

        {logoutError ? (
          <p className="mt-4 rounded-xl bg-[rgb(159_45_45/0.08)] px-3 py-2 text-sm text-danger" role="alert">
            {logoutError}
          </p>
        ) : null}

        <button
          type="button"
          disabled={!site.ok}
          onClick={() => navigate(ROUTES.chat)}
          className="mt-6 h-14 rounded-2xl bg-primary px-4 text-left text-base font-medium text-primary-foreground disabled:opacity-50"
        >
          <span className="block">AI 對話</span>
          <span className="mt-0.5 block text-sm font-normal text-primary-foreground/80">串流回覆，並查看對話歷史</span>
        </button>
      </main>
    </Screen>
  )
}
