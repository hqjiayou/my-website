import { Route, Routes } from 'react-router-dom'

import { ContentPage } from '@/pages/content/contentPage'
import { GuidePage } from '@/pages/guide/guidePage'
import { SiteHomePage } from '@/pages/site/siteHomePage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<GuidePage />} />
      <Route path="/:siteCode/contents/:contentId" element={<ContentPage />} />
      <Route path="/:siteCode" element={<SiteHomePage />} />
      <Route path="*" element={<GuidePage />} />
    </Routes>
  )
}
