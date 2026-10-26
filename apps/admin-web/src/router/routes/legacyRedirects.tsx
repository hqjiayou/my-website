import { Navigate, Route } from 'react-router-dom'

import { ROUTES } from '../config/routePaths'

export const legacyRedirects = (
  <>
    <Route path={ROUTES.legacy.sites} element={<Navigate to={ROUTES.apps.sites} replace />} />
    <Route path={ROUTES.legacy.ai} element={<Navigate to={ROUTES.ai.settings} replace />} />
    <Route path={ROUTES.legacy.settings} element={<Navigate to={ROUTES.governance.system} replace />} />
  </>
)
