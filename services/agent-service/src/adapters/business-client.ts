export interface SiteConfigSnapshot {
  siteId: string
  siteName: string
  locale: string
}

export async function fetchSiteConfig(siteId: string): Promise<SiteConfigSnapshot> {
  return {
    siteId,
    siteName: 'Sakura Auto Garage',
    locale: 'ja-JP',
  }
}
