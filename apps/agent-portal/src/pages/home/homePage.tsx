import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { BottomNav } from '@/components/bottomNav'
import { Screen } from '@/components/screen'
import { readAccessTokenSiteId, resolvePortalSiteId } from '@/config/env'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import { errorMessage } from '@/utils/http/apiError'

export function HomePage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const sessionSite = useAuthStore((state) => state.site)
  const accessToken = useAuthStore((state) => state.accessToken)
  const logout = useAuthStore((state) => state.logout)
  const site = resolvePortalSiteId(sessionSite?.siteId)
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
  const siteIdFromSession = sessionSite != null && site.ok && String(sessionSite.siteId) === site.siteId

  return (
    <Screen>
      <header className="safe-top safe-x flex items-center justify-between px-page pt-4">
        <p className="text-sm text-gpt-muted">{displayName}</p>
        <button
          type="button"
          onClick={() => void onLogout()}
          disabled={loggingOut}
          className="h-10 rounded-lg px-3 text-sm text-gpt-ink hover:bg-gpt-hover disabled:opacity-60"
        >
          {loggingOut ? '正在登出…' : '登出'}
        </button>
      </header>

      <main className="safe-x with-bottom-nav flex flex-1 flex-col px-page pt-8">
        <h1 className="text-3xl font-semibold tracking-tight">Agent Portal</h1>
        <p className="mt-3 text-sm leading-6 text-gpt-muted">從這裡進入 AI 對話，或在知識庫裡查找內容。</p>

        <div className="mt-section flex flex-col gap-stack">
          <button
            type="button"
            disabled={!site.ok}
            onClick={() => navigate(ROUTES.chat)}
            className="rounded-2xl border border-gpt-line bg-gpt-canvas p-4 text-left hover:bg-gpt-hover disabled:opacity-60"
          >
            <span className="block text-lg font-semibold">AI 對話</span>
            <span className="mt-1 block text-sm leading-6 text-gpt-muted">串流回覆，並查看對話歷史</span>
          </button>
          <button
            type="button"
            disabled={!site.ok}
            onClick={() => navigate(ROUTES.knowledge)}
            className="rounded-2xl border border-gpt-line bg-gpt-canvas p-4 text-left hover:bg-gpt-hover disabled:opacity-60"
          >
            <span className="block text-lg font-semibold">知識搜尋</span>
            <span className="mt-1 block text-sm leading-6 text-gpt-muted">在這個站點的知識庫裡查找相關內容</span>
          </button>
        </div>

        <section className="mt-section rounded-2xl border border-gpt-line p-4">
          <h2 className="text-sm font-medium">帳號</h2>
          {user?.avatarUrl ? (
            <img
              src={user.avatarUrl}
              alt=""
              className="mt-3 h-12 w-12 rounded-pill object-cover"
              onError={(event) => {
                event.currentTarget.hidden = true
              }}
            />
          ) : null}
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <div>
              <dt className="text-xs text-gpt-muted">顯示名稱</dt>
              <dd className="mt-0.5">{displayName}</dd>
            </div>
            {user?.username ? (
              <div>
                <dt className="text-xs text-gpt-muted">使用者名稱</dt>
                <dd className="mt-0.5 break-all">{user.username}</dd>
              </div>
            ) : null}
            {user?.userCode ? (
              <div>
                <dt className="text-xs text-gpt-muted">使用者代碼</dt>
                <dd className="mt-0.5 break-all">{user.userCode}</dd>
              </div>
            ) : null}
          </dl>

          <h2 className="mt-4 text-sm font-medium">目前站點</h2>
          {sessionSite ? (
            <dl className="mt-3 flex flex-col gap-2 text-sm">
              <div>
                <dt className="text-xs text-gpt-muted">站點名稱</dt>
                <dd className="mt-0.5">{sessionSite.siteName}</dd>
              </div>
              <div>
                <dt className="text-xs text-gpt-muted">站點代碼</dt>
                <dd className="mt-0.5 break-all">{sessionSite.siteCode}</dd>
              </div>
            </dl>
          ) : site.ok ? (
            <p className="mt-1 text-lg font-medium">站點 ID {site.siteId}</p>
          ) : (
            <p className="mt-1 text-sm leading-6 text-danger">{site.message}</p>
          )}
          {sessionSite ? null : (
            <p className="mt-2 text-sm leading-6 text-gpt-muted">
              契約沒有站點清單，也沒有回傳站點名稱的 API，所以這裡只顯示環境變數裡的站點 ID。
            </p>
          )}
          {sessionSite && !site.ok ? (
            <p className="mt-2 text-sm leading-6 text-danger">{site.message}</p>
          ) : null}
          {tokenSiteId ? <p className="mt-2 text-sm text-gpt-muted">登入權杖 site_id：{tokenSiteId}</p> : null}
          {siteMismatch ? (
            <p className="mt-2 text-sm leading-6 text-danger">
              {siteIdFromSession
                ? '權杖裡的 site_id 與目前站點不一致。站點 AI 會依路徑上的站點 ID 驗證，存取會被拒絕。'
                : '權杖裡的 site_id 與 VITE_PORTAL_SITE_ID 不一致。站點 AI 會依路徑上的站點 ID 驗證，存取會被拒絕。'}
            </p>
          ) : null}
        </section>

        {logoutError ? (
          <p className="mt-4 rounded-control bg-danger-soft px-3 py-2 text-sm text-danger" role="alert">
            {logoutError}
          </p>
        ) : null}
      </main>
      <BottomNav />
    </Screen>
  )
}
