/** 閘道 origin。留空則請求落在目前來源的 /api，由 Vite 代理到閘道。 */
export function apiUrl(path: string): string {
  const base = (import.meta.env.VITE_API_BASE_URL ?? '').trim().replace(/\/$/, '')
  return `${base}${path}`
}

const SITE_ID_PATTERN = /^[1-9]\d*$/

export type PortalSiteId = { ok: true; siteId: string } | { ok: false; message: string }

/** AI 路徑用的數字站點 ID。登入用的 siteCode 不在這裡。 */
export function readPortalSiteId(): PortalSiteId {
  const raw = import.meta.env.VITE_PORTAL_SITE_ID?.trim() ?? ''
  if (!SITE_ID_PATTERN.test(raw)) {
    return {
      ok: false,
      message: '未設定有效的 VITE_PORTAL_SITE_ID。它必須是正整數站點 ID，用於 /api/site/{siteId}/ai/**。',
    }
  }
  return { ok: true, siteId: raw }
}

/** 優先使用會話 siteId（轉成字串），沒有時後備 readPortalSiteId。 */
export function resolvePortalSiteId(sessionSiteId: number | null | undefined): PortalSiteId {
  if (sessionSiteId != null && Number.isSafeInteger(sessionSiteId) && SITE_ID_PATTERN.test(String(sessionSiteId))) {
    return { ok: true, siteId: String(sessionSiteId) }
  }
  const fromEnv = readPortalSiteId()
  if (fromEnv.ok) return fromEnv
  return {
    ok: false,
    message:
      '未取得站點資訊。請重新登入以取得目前站點，或設定正整數環境變數 VITE_PORTAL_SITE_ID 作為後備，用於 /api/site/{siteId}/ai/**。',
  }
}

/** 只讀 JWT payload 的 site_id，不驗簽。存取控制以伺服器端為準。 */
export function readAccessTokenSiteId(token: string | null): string | null {
  if (!token) return null
  const part = token.split('.')[1]
  if (!part) return null
  try {
    const json = JSON.parse(decodeBase64Url(part)) as { site_id?: unknown }
    const value = json.site_id
    if (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) return String(value)
    if (typeof value === 'string' && SITE_ID_PATTERN.test(value)) return value
    return null
  } catch {
    return null
  }
}

function decodeBase64Url(value: string): string {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/')
  const pad = base64.length % 4 === 0 ? '' : '='.repeat(4 - (base64.length % 4))
  return atob(base64 + pad)
}
