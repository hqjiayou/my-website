import { AlertCircle, Inbox, ShieldOff } from 'lucide-react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

import { adminSecondaryButtonClass } from '../controls'
import type { TableEmptyKind } from './types'

export function DataTableEmptyState({
  kind = 'empty',
  title,
  description,
  action,
  onClearFilters,
  className,
}: {
  kind?: TableEmptyKind
  title?: string
  description?: string
  action?: ReactNode
  onClearFilters?: () => void
  className?: string
}) {
  const { t } = useTranslation('common')
  const resolvedTitle =
    title ??
    (kind === 'filtered'
      ? t('table.noMatches')
      : kind === 'forbidden'
        ? t('table.noPermission')
        : t('table.empty'))
  const Icon = kind === 'forbidden' ? ShieldOff : kind === 'filtered' ? AlertCircle : Inbox

  return (
    <div className={cn('flex flex-col items-center justify-center gap-2 px-4 py-10 text-center', className)}>
      <span className="admin-empty-mark">
        <Icon className="h-6 w-6" aria-hidden />
      </span>
      <p className="text-sm font-medium text-admin">{resolvedTitle}</p>
      {description ? <p className="max-w-md text-xs text-admin-muted">{description}</p> : null}
      {kind === 'filtered' && onClearFilters ? (
        <button type="button" className={adminSecondaryButtonClass} onClick={onClearFilters}>
          {t('table.clearFilters')}
        </button>
      ) : null}
      {action}
    </div>
  )
}
