import type { HttpMessageBody } from '@/types/api'
import type { PublicContentDetail, PublicContentList, PublicSite } from '@/types/public'
import { httpClient } from '@/utils/http/axios'
import { unwrapData } from '@/utils/http/unwrap'

function sitePath(siteCode: string): string {
  return `/api/site/public/${encodeURIComponent(siteCode)}`
}

export const publicApi = {
  getSite(siteCode: string) {
    return httpClient.get<HttpMessageBody<PublicSite>>(sitePath(siteCode)).then(unwrapData)
  },

  listContents(siteCode: string) {
    return httpClient.get<HttpMessageBody<PublicContentList>>(`${sitePath(siteCode)}/contents`).then(unwrapData)
  },

  getContent(siteCode: string, contentId: number) {
    return httpClient
      .get<HttpMessageBody<PublicContentDetail>>(`${sitePath(siteCode)}/contents/${contentId}`)
      .then(unwrapData)
  },
}
