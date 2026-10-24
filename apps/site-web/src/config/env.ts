/** 閘道 origin。留空則請求落在目前來源的 /api，由 Vite 代理到閘道。 */
export function apiUrl(path: string): string {
  const base = (import.meta.env.VITE_API_BASE_URL ?? '').trim().replace(/\/$/, '')
  return `${base}${path}`
}
