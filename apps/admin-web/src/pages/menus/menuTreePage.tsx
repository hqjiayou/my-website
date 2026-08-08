import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { rbacApi } from '@/api/rbacApi'
import { PageHeader } from '@/components/page'
import type { MenuNodeDto } from '@/types/rbac'

export function MenuTreePage() {
  const { t } = useTranslation('menus')
  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['menus'],
    queryFn: () => rbacApi.listMenus(),
  })

  const menus = data ?? []

  return (
    <section>
      <PageHeader title={t('title')} description={t('description')} />

      <div className="mb-4 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
        {t('hint')}
      </div>

      <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        {isPending ? (
          <p className="text-sm text-slate-500">{t('loading')}</p>
        ) : isError ? (
          <div className="text-sm text-rose-700">
            <p>{error instanceof Error ? error.message : t('error')}</p>
            <button type="button" onClick={() => void refetch()} className="mt-2 underline">
              {t('retry')}
            </button>
          </div>
        ) : menus.length === 0 ? (
          <p className="text-sm text-slate-500">{t('empty')}</p>
        ) : (
          <ul className="space-y-2">
            {menus.map((node) => (
              <MenuTreeNode key={node.menuCode} node={node} depth={0} />
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}

function MenuTreeNode({ node, depth }: { node: MenuNodeDto; depth: number }) {
  const { t } = useTranslation('menus')

  return (
    <li>
      <div
        className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-lg border border-slate-100 px-3 py-2 text-sm"
        style={{ marginLeft: depth * 16 }}
      >
        <span className="font-medium text-slate-900">{node.menuName}</span>
        <span className="text-xs text-slate-500">{node.menuCode}</span>
        <span className="text-xs text-slate-500">
          {t('labels.path')}: {node.path || '—'}
        </span>
        <span className="text-xs text-slate-500">
          {t('labels.icon')}: {node.icon || '—'}
        </span>
        <span className="text-xs text-slate-500">
          {t('labels.sort')}: {node.sort}
        </span>
      </div>
      {node.children?.length ? (
        <ul className="mt-2 space-y-2">
          {node.children.map((child) => (
            <MenuTreeNode key={child.menuCode} node={child} depth={depth + 1} />
          ))}
        </ul>
      ) : null}
    </li>
  )
}
