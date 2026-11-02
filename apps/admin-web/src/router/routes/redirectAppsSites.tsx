import { Navigate, useLocation } from 'react-router-dom'

import { ROUTES } from '../config/routePaths'

/** 旧路径 /apps/sites/* 通配重定向到 /agent/*，保留子路径与查询串。 */
export function RedirectAppsSites() {
  const { pathname, search, hash } = useLocation()
  const rest = pathname.slice(ROUTES.legacy.appsSites.length)
  return <Navigate to={`${ROUTES.apps.sites}${rest}${search}${hash}`} replace />
}
