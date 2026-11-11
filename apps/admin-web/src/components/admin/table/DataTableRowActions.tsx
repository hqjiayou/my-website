import { MoreHorizontal } from 'lucide-react'
import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

import { adminGhostButtonClass } from '../controls'
import type { TableRowAction } from './types'

/**
 * 行操作菜单用 fixed + portal，避免被 DataTable 的 overflow 裁切。
 * 靠近视口底部时向上展开，保证末行菜单完整可见。
 */
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
  const [coords, setCoords] = useState<{ top: number; left: number; openUp: boolean } | null>(null)
  const rootRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)
  const menuId = useId()
  const visibleActions = actions.filter(Boolean)

  useLayoutEffect(() => {
    if (!open || !buttonRef.current) {
      setCoords(null)
      return
    }
    function place() {
      const button = buttonRef.current
      const menu = menuRef.current
      if (!button) return
      const rect = button.getBoundingClientRect()
      const menuHeight = menu?.offsetHeight ?? visibleActions.length * 36 + 12
      const menuWidth = menu?.offsetWidth ?? 160
      const gap = 4
      const spaceBelow = window.innerHeight - rect.bottom
      const openUp = spaceBelow < menuHeight + gap && rect.top > spaceBelow
      const top = openUp
        ? Math.max(8, rect.top - menuHeight - gap)
        : Math.min(window.innerHeight - menuHeight - 8, rect.bottom + gap)
      const left = Math.min(Math.max(8, rect.right - menuWidth), window.innerWidth - menuWidth - 8)
      setCoords({ top, left, openUp })
    }
    place()
    // 菜单挂载后按真实尺寸再校正一次（末行向上展开依赖实测高度）。
    const raf = window.requestAnimationFrame(place)
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, true)
    return () => {
      window.cancelAnimationFrame(raf)
      window.removeEventListener('resize', place)
      window.removeEventListener('scroll', place, true)
    }
  }, [open, visibleActions.length])

  useEffect(() => {
    if (!open) return
    function onPointerDown(event: MouseEvent) {
      const target = event.target as Node
      if (rootRef.current?.contains(target) || menuRef.current?.contains(target)) return
      setOpen(false)
      setConfirmId(null)
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
        setConfirmId(null)
        buttonRef.current?.focus()
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

  const menu = open ? (
    <div
      ref={menuRef}
      id={menuId}
      role="menu"
      style={
        coords
          ? { top: coords.top, left: coords.left }
          : { top: 0, left: 0, visibility: 'hidden' as const }
      }
      className="fixed z-30 min-w-40 rounded-lg border border-admin bg-admin-surface p-1 shadow-admin-pop"
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
  ) : null

  return (
    <div ref={rootRef} className="relative inline-flex justify-end">
      <button
        ref={buttonRef}
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
      {menu ? createPortal(menu, document.body) : null}
    </div>
  )
}
