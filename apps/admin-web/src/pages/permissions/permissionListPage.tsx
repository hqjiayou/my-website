import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { rbacApi } from '@/api/rbacApi'
import {
  AdminPageHeader,
  CopyableText,
  DataTable,
  FilterTabs,
  SearchToolbar,
  StatusTag,
  type DataTableColumn,
} from '@/components/admin'
import { useClientPagination } from '@/hooks/useClientPagination'
import { useDebounce } from '@/hooks/useDebounce'
import type { PermissionDto } from '@/types/rbac'
import { formatResourceType } from '@/utils/enumLabel'
import { groupPermissionsByResourceType } from '@/router/permission'
import { ROUTES } from '@/router/config/routePaths'
import { isApiError } from '@/utils/http/apiError'

type ResourceTab = 'MENU' | 'ACTION' | 'OTHER'

const PAGE_SIZE = 20

export function PermissionListPage() {
  const { t } = useTranslation('permissions')
  const { t: tc } = useTranslation('common')

  const [tab, setTab] = useState<ResourceTab>('MENU')
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounce(keyword, 300)

  const { data, isPending, isError, error, refetch, isFetching } = useQuery({
    queryKey: ['permissions'],
    queryFn: () => rbacApi.listPermissions(),
  })

  // 错误时不把空数组当成功数据参与计数；DataTable 用 isError 展示失败态
  const grouped = useMemo(
    () => groupPermissionsByResourceType(isError ? [] : (data ?? [])),
    [data, isError],
  )

  const tabCounts = useMemo(
    () => ({
      MENU: grouped.menu.length,
      ACTION: grouped.action.length,
      OTHER: grouped.other.length,
    }),
    [grouped.action.length, grouped.menu.length, grouped.other.length],
  )

  const filtered = useMemo(() => {
    const source =
      tab === 'MENU' ? grouped.menu : tab === 'ACTION' ? grouped.action : grouped.other
    if (!debouncedKeyword.trim()) return source
    const lower = debouncedKeyword.trim().toLowerCase()
    return source.filter(
      (item) =>
        item.permissionCode.toLowerCase().includes(lower) ||
        item.permissionName.toLowerCase().includes(lower) ||
        item.resourceKey.toLowerCase().includes(lower),
    )
  }, [debouncedKeyword, grouped.action, grouped.menu, grouped.other, tab])

  const { pageItems, resetPage, pagination } = useClientPagination(filtered, PAGE_SIZE)

  const columns: DataTableColumn<PermissionDto>[] = useMemo(
    () => [
      {
        id: 'permissionCode',
        header: t('columns.permissionCode'),
        cell: (row) => <CopyableText value={row.permissionCode} />,
      },
      {
        id: 'permissionName',
        header: t('columns.permissionName'),
        cell: (row) => <span className="text-slate-700">{row.permissionName}</span>,
      },
      {
        id: 'resourceType',
        header: t('columns.resourceType'),
        cell: (row) => <span className="text-slate-600">{formatResourceType(tc, row.resourceType)}</span>,
      },
      {
        id: 'resourceKey',
        header: t('columns.resourceKey'),
        cell: (row) => <span className="font-mono text-xs text-slate-600">{row.resourceKey}</span>,
      },
      {
        id: 'status',
        header: t('columns.status'),
        cell: (row) => <StatusTag status={row.status} />,
      },
    ],
    [t, tc],
  )

  const tabs = useMemo(
    () => [
      { key: 'MENU', label: t('tabs.menu'), count: tabCounts.MENU },
      { key: 'ACTION', label: t('tabs.action'), count: tabCounts.ACTION },
      { key: 'OTHER', label: t('tabs.other'), count: tabCounts.OTHER },
    ],
    [t, tabCounts.ACTION, tabCounts.MENU, tabCounts.OTHER],
  )

  function handleTabChange(next: string) {
    setTab(next as ResourceTab)
    resetPage()
  }

  function handleReset() {
    setKeyword('')
    resetPage()
  }

  const errorMessage = isApiError(error) ? error.message : error instanceof Error ? error.message : undefined

  return (
    <section>
      <AdminPageHeader
        title={t('title')}
        description={t('description')}
        hint={
          <>
            {t('hint')}{' '}
            <Link to={ROUTES.system.roles} className="font-medium text-admin underline-offset-2 hover:underline">
              {t('assignLink')}
            </Link>
          </>
        }
      />

      <FilterTabs tabs={tabs} activeKey={tab} onChange={handleTabChange} className="mb-4" />

      <SearchToolbar
        className="mb-4"
        value={keyword}
        onChange={(value) => {
          setKeyword(value)
          resetPage()
        }}
        onReset={handleReset}
        onRefresh={() => void refetch()}
        isRefreshing={isFetching}
        placeholder={t('searchPlaceholder')}
      />

      <DataTable
        columns={columns}
        data={pageItems}
        rowKey={(row) => row.id}
        isLoading={isPending}
        isError={isError}
        errorMessage={errorMessage}
        emptyMessage={t('empty')}
        onRetry={() => void refetch()}
        pagination={pagination}
      />
    </section>
  )
}
