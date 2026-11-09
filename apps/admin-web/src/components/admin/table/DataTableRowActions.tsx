import { MoreHorizontal } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

import { adminGhostButtonClass } from '../controls'
import type { TableRowAction } from './types'

export function DataTableRowActions<T>({
  row,
  actions,
  label,
}: {
  row: T
  actions: Array<TableRowAction<T>>
  label?: string
}) {
  const { t } = useTranslation('common')
  const [open, setOpen] = useState(false)
  const [confirmId, setConfirmId] = useState<string | null>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const menuId = useId()
  const visibleActions = actions.filter(Boolean)

  useEffect(() => {
    if (!open) return
    function onPointerDown(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) {
        setOpen(false)
        setConfirmId(null)
      }
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
        setConfirmId(null)
      }
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (visibleActions.length === 0) return null

  return (
    <div ref={rootRef} className="relative inline-flex justify-end">
      <button
        type="button"
        className={cn(adminGhostButtonClass, 'h-7 w-7 justify-center px-0')}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        aria-label={label ?? t('table.rowActions')}
        onClick={(event) => {
          event.stopPropagation()
          setOpen((value) => !value)
          setConfirmId(null)
        }}
      >
        <MoreHorizontal className="h-4 w-4" aria-hidden />
      </button>
      {open ? (
        <div
          id={menuId}
          role="menu"
          className="absolute top-full right-0 z-30 mt-1 min-w-40 rounded-lg border border-admin bg-admin-surface p-1 shadow-admin-pop"
          onClick={(event) => event.stopPropagation()}
        >
          {visibleActions.map((action) => {
            const confirming = confirmId === action.id
            return (
              <button
                key={action.id}
                type="button"
                role="menuitem"
                disabled={action.disabled}
                title={action.disabled ? action.disabledReason : undefined}
                className={cn(
                  'flex w-full items-center rounded-md px-2.5 py-1.5 text-left text-xs font-medium transition',
                  action.danger ? 'text-admin-danger hover:bg-admin-danger-muted' : 'text-admin hover:bg-admin-surface-muted',
                  action.disabled && 'cursor-not-allowed opacity-40 hover:bg-transparent',
                )}
                onClick={() => {
                  if (action.disabled) return
                  if (action.danger && action.confirmLabel && !confirming) {
                    setConfirmId(action.id)
                    return
                  }
                  action.onSelect(row)
                  setOpen(false)
                  setConfirmId(null)
                }}
              >
                {confirming ? action.confirmLabel : action.label}
              </button>
            )
          })}
        </div>
      ) : null}
    </div>
  )
}
