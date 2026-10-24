export const ROUTES = {
  guide: '/',
} as const

export function sitePath(siteCode: string): string {
  return `/${encodeURIComponent(siteCode)}`
}

export function contentPath(siteCode: string, contentId: number): string {
  return `/${encodeURIComponent(siteCode)}/contents/${contentId}`
}
