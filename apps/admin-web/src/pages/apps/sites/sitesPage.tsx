import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate } from 'react-router-dom'

import { agentApi } from '@/api'
import { AdminPageHeader } from '@/components/admin'
import { adminPrimaryButtonClass } from '@/components/admin/controls'
import { ROUTES } from '@/router/config/routePaths'
import { PermissionGate } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

export function SitesPage() {
  const { t } = useTranslation('sites')
  const { t: tCommon } = useTranslation('common')
  const navigate = useNavigate()
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', { pageNum: 1, pageSize: 50 }],
    queryFn: () => agentApi.listSites({ pageNum: 1, pageSize: 50 }),
  })

  if (isPending) {
    return <p className="text-sm text-admin-muted">{t('workspace.loading')}</p>
  }
  if (isError) {
    return (
      <p className="text-sm text-danger-emphasis">
        {error instanceof Error ? error.message : t('list.empty')}{' '}
        <button type="button" className="underline" onClick={() => void refetch()}>
          {tCommon('table.retry')}
        </button>
      </p>
    )
  }

  const records = data?.records ?? []
  const target = [...records].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))[0]
  if (target) {
    return <Navigate to={ROUTES.apps.agent(target.id)} replace />
  }

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('list.title')} />
      <div className="rounded-xl border border-admin bg-admin-surface px-5 py-10">
        <p className="text-sm text-admin-muted">{t('list.empty')}</p>
        <PermissionGate code={PERMISSION_CODES.agentCreate}>
          <button type="button" onClick={() => navigate(ROUTES.apps.agentCreate)} className={`${adminPrimaryButtonClass} mt-4`}>
            <Plus className="h-4 w-4" />
            {t('list.createSite')}
          </button>
        </PermissionGate>
      </div>
    </section>
  )
}
