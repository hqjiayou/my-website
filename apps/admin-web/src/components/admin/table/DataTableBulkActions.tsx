import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

import {
  adminDangerGhostButtonSmClass,
  adminGhostButtonClass,
  adminSecondaryButtonSmClass,
} from '../controls'
import type { TableBulkAction } from './types'

/**
 * 仅渲染 available !== false 的操作。没有真实后端能力时，业务页应设 available:false
 * 或不传入该 action，禁止显示为可点的假批量。
 */
export function DataTableBulkActions({
  selectedKeys,
  actions,
  onClear,
  hint,
  className,
}: {
  selectedKeys: Array<string | number>
  actions: TableBulkAction[]
  onClear: () => void
  hint?: string
  className?: string
}) {
  const { t } = useTranslation('common')
  const selectedCount = selectedKeys.length
  if (selectedCount <= 0) return null

  const usable = actions.filter((action) => action.available !== false)

  return (
    <div
      className={cn(
        'flex flex-wrap items-center gap-2 rounded-xl border border-admin bg-admin-surface-muted px-3 py-2',
        className,
      )}
      role="status"
      aria-live="polite"
    >
      <span className="text-xs font-medium text-admin">
        {t('table.selectedCount', { count: selectedCount })}
      </span>
      {hint ? <span className="text-xs text-admin-muted">{hint}</span> : null}
      <div className="ml-auto flex flex-wrap items-center gap-1.5">
        {usable.map((action) => (
          <button
            key={action.id}
            type="button"
            disabled={action.disabled}
            title={action.disabled ? action.disabledReason : undefined}
            className={action.danger ? adminDangerGhostButtonSmClass : adminSecondaryButtonSmClass}
            onClick={() => {
              if (action.disabled) return
              action.onSelect(selectedKeys)
            }}
          >
            {action.label}
          </button>
        ))}
        <button type="button" className={adminGhostButtonClass} onClick={onClear}>
          {t('table.clearSelection')}
        </button>
      </div>
    </div>
  )
}
