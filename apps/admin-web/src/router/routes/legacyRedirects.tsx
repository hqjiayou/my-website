import { Navigate, Route, useLocation } from 'react-router-dom'

import { ROUTES } from '../config/routePaths'

function RedirectAppsSites() {
  const { pathname, search, hash } = useLocation()
  const rest = pathname.slice(ROUTES.legacy.appsSites.length)
  return <Navigate to={`${ROUTES.apps.sites}${rest}${search}${hash}`} replace />
}

export const legacyRedirects = (
  <>
    <Route path={ROUTES.legacy.sites} element={<Navigate to={ROUTES.apps.sites} replace />} />
    <Route path={ROUTES.legacy.appsSites} element={<Navigate to={ROUTES.apps.sites} replace />} />
    <Route path={`${ROUTES.legacy.appsSites}/*`} element={<RedirectAppsSites />} />
    <Route path={ROUTES.legacy.proxyServices} element={<Navigate to={ROUTES.apps.api} replace />} />
    <Route path={ROUTES.legacy.ai} element={<Navigate to={ROUTES.ai.settings} replace />} />
    <Route path={ROUTES.legacy.settings} element={<Navigate to={ROUTES.governance.system} replace />} />
  </>
)
