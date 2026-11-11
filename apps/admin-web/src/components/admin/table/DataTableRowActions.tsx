import { MoreHorizontal } from 'lucide-react'
import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

import { adminGhostButtonClass } from '../controls'
import type { TableRowAction } from './types'

/**
 * 行操作菜单用 fixed + portal，避免被 DataTable 的 overflow 裁切。
 * 靠近视口底部时向上展开；内容变化 / 滚动 / 缩放时重测，保证末行与 200% 缩放可见。
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
  const selectingRef = useRef(false)
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
      const vv = window.visualViewport
      const viewportWidth = vv?.width ?? window.innerWidth
      const viewportHeight = vv?.height ?? window.innerHeight
      const originLeft = vv?.offsetLeft ?? 0
      const originTop = vv?.offsetTop ?? 0
      const menuHeight = menu?.offsetHeight ?? visibleActions.length * 36 + 12
      const menuWidth = menu?.offsetWidth ?? 160
      const gap = 4
      const spaceBelow = originTop + viewportHeight - rect.bottom
      const spaceAbove = rect.top - originTop
      const openUp = spaceBelow < menuHeight + gap && spaceAbove > spaceBelow
      const top = openUp
        ? Math.max(originTop + 8, rect.top - menuHeight - gap)
        : Math.min(originTop + viewportHeight - menuHeight - 8, rect.bottom + gap)
      const left = Math.min(
        Math.max(originLeft + 8, rect.right - menuWidth),
        originLeft + viewportWidth - menuWidth - 8,
      )
      setCoords({ top, left, openUp })
    }
    place()
    // 菜单挂载后按真实尺寸再校正一次（末行向上展开依赖实测高度）。
    const raf = window.requestAnimationFrame(place)
    const menuEl = menuRef.current
    const resizeObserver =
      menuEl != null && typeof ResizeObserver !== 'undefined' ? new ResizeObserver(() => place()) : null
    if (menuEl != null) resizeObserver?.observe(menuEl)
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, true)
    window.visualViewport?.addEventListener('resize', place)
    window.visualViewport?.addEventListener('scroll', place)
    return () => {
      window.cancelAnimationFrame(raf)
      resizeObserver?.disconnect()
      window.removeEventListener('resize', place)
      window.removeEventListener('scroll', place, true)
      window.visualViewport?.removeEventListener('resize', place)
      window.visualViewport?.removeEventListener('scroll', place)
    }
  }, [open, visibleActions.length, confirmId])

  useEffect(() => {
    if (!open) {
      selectingRef.current = false
      return
    }
    function onPointerDown(event: MouseEvent) {
      const target = event.target as Node
      if (rootRef.current?.contains(target) || menuRef.current?.contains(target)) return
      setOpen(false)
      setConfirmId(null)
    }
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.stopPropagation()
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

  useEffect(() => {
    if (!open) return
    const raf = window.requestAnimationFrame(() => {
      const first = menuRef.current?.querySelector<HTMLElement>(
        '[role="menuitem"]:not([disabled])',
      )
      first?.focus()
    })
    return () => window.cancelAnimationFrame(raf)
  }, [open, confirmId])

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
              'admin-focus-ring flex w-full items-center rounded-md px-2.5 py-1.5 text-left text-xs font-medium transition',
              action.danger ? 'text-admin-danger hover:bg-admin-danger-muted' : 'text-admin hover:bg-admin-surface-muted',
              action.disabled && 'cursor-not-allowed opacity-40 hover:bg-transparent',
            )}
            onClick={() => {
              if (action.disabled || selectingRef.current) return
              if (action.danger && action.confirmLabel && !confirming) {
                setConfirmId(action.id)
                return
              }
              selectingRef.current = true
              try {
                action.onSelect(row)
              } finally {
                setOpen(false)
                setConfirmId(null)
                buttonRef.current?.focus()
                queueMicrotask(() => {
                  selectingRef.current = false
                })
              }
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
          selectingRef.current = false
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
