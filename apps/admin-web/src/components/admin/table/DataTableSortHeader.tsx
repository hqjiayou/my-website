import { ArrowDown, ArrowUp, ArrowUpDown } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

import type { TableSortOrder, TableSortScope } from './types'

export function DataTableSortHeader({
  label,
  active,
  order,
  scope,
  onToggle,
}: {
  label: ReactNode
  active: boolean
  order: TableSortOrder
  scope: TableSortScope
  onToggle: () => void
}) {
  const { t } = useTranslation('common')
  const Icon = !active ? ArrowUpDown : order === 'asc' ? ArrowUp : ArrowDown

  return (
    <button
      type="button"
      className="admin-focus-ring inline-flex max-w-full items-center gap-1 rounded-md text-left uppercase"
      title={scope === 'client-page' ? t('table.sortScopeClientPage') : t('table.sortScopeServer')}
      onClick={onToggle}
    >
      <span className="truncate">{label}</span>
      <Icon className={cn('h-3.5 w-3.5 shrink-0', active ? 'text-primary' : 'text-admin-muted')} aria-hidden />
      <span className="sr-only">
        {scope === 'client-page' ? t('table.sortScopeClientPage') : t('table.sortScopeServer')}
      </span>
    </button>
  )
}
