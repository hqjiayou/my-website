import { Columns3 } from 'lucide-react'
import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

import { adminGhostButtonClass, adminSecondaryButtonSmClass } from '../controls'

export interface DataTableColumnMenuItem {
  id: string
  label: ReactNode
  hideable?: boolean
  visible: boolean
}

export function DataTableColumnMenu({
  columns,
  onToggle,
  onReset,
}: {
  columns: DataTableColumnMenuItem[]
  onToggle: (columnId: string) => void
  onReset?: () => void
}) {
  const { t } = useTranslation('common')
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const menuId = useId()

  useEffect(() => {
    if (!open) return
    function onPointerDown(event: MouseEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        className={adminGhostButtonClass}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        aria-label={t('table.columns')}
        onClick={() => setOpen((value) => !value)}
      >
        <Columns3 className="h-3.5 w-3.5" aria-hidden />
        {t('table.columns')}
      </button>
      {open ? (
        <div
          id={menuId}
          role="menu"
          className="absolute top-full right-0 z-30 mt-1 min-w-48 rounded-lg border border-admin bg-admin-surface p-2 shadow-admin-pop"
        >
          <p className="px-1.5 pb-1.5 text-xs font-semibold tracking-wide text-admin-muted uppercase">
            {t('table.columnVisibility')}
          </p>
          <ul className="space-y-0.5">
            {columns.map((column) => {
              const locked = column.hideable === false
              return (
                <li key={column.id}>
                  <label
                    className={cn(
                      'flex cursor-pointer items-center gap-2 rounded-md px-1.5 py-1.5 text-xs text-admin hover:bg-admin-surface-muted',
                      locked && 'cursor-not-allowed opacity-60 hover:bg-transparent',
                    )}
                  >
                    <input
                      type="checkbox"
                      className="admin-check"
                      checked={column.visible}
                      disabled={locked}
                      onChange={() => {
                        if (!locked) onToggle(column.id)
                      }}
                    />
                    <span className="min-w-0 truncate">{column.label}</span>
                  </label>
                </li>
              )
            })}
          </ul>
          {onReset ? (
            <button
              type="button"
              className={cn(adminSecondaryButtonSmClass, 'mt-2 w-full')}
              onClick={() => {
                onReset()
                setOpen(false)
              }}
            >
              {t('table.resetColumns')}
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
