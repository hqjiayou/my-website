/**
 * 站点前台（site-web）访问地址：优先 VITE_SITE_WEB_BASE_URL；
 * 本地开发（localhost）回退到 site-web 的 vite 端口 5175。
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
    return `http://${window.location.hostname}:5175/${encodeURIComponent(siteCode)}`
  }
  return null
}
