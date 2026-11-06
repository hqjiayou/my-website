import { Navigate, Route } from 'react-router-dom'

import { ROUTES } from '../config/routePaths'

import { RedirectAppsSites } from './redirectAppsSites'

export const legacyRedirects = (
  <>
    <Route path={ROUTES.legacy.sites} element={<Navigate to={ROUTES.apps.sites} replace />} />
    <Route path={ROUTES.legacy.appsSites} element={<Navigate to={ROUTES.apps.sites} replace />} />
    <Route path={`${ROUTES.legacy.appsSites}/*`} element={<RedirectAppsSites />} />
    <Route path={ROUTES.legacy.api} element={<Navigate to={ROUTES.ai.modelEntries} replace />} />
    <Route path={ROUTES.legacy.proxyServices} element={<Navigate to={ROUTES.ai.modelEntries} replace />} />
    <Route path={ROUTES.legacy.chainPresets} element={<Navigate to={ROUTES.ai.modelEntries} replace />} />
    <Route path={ROUTES.legacy.configPresets} element={<Navigate to={ROUTES.ai.promptTemplates} replace />} />
    <Route path={ROUTES.legacy.ai} element={<Navigate to={ROUTES.ai.settings} replace />} />
    <Route path={ROUTES.legacy.settings} element={<Navigate to={ROUTES.governance.system} replace />} />
  </>
)
