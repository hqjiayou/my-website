/**
 * 「访问站点」地址：优先 VITE_SITE_WEB_BASE_URL（部署的前台地址，路径带 siteCode）；
 * 本地开发（localhost）回退到 agent-portal 的 5174 根路径——本地实际在跑、且
 * 站点使用者真正进入的是 Portal（5174），site-web（5175）本地通常没起。
 * 推导不出时返回 null，UI 不展示「访问站点」入口（不给假链接）。
 */
export function siteWebUrl(siteCode: string): string | null {
  const configured = (import.meta.env.VITE_SITE_WEB_BASE_URL as string | undefined)?.replace(/\/+$/, '')
  if (configured) {
    return `${configured}/${encodeURIComponent(siteCode)}`
  }
  if (
    typeof window !== 'undefined' &&
    (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')
  ) {
    return `http://${window.location.hostname}:5174/`
  }
  return null
}
