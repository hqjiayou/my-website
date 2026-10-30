import { X, TriangleAlert } from 'lucide-react'
import { useEffect, type ReactNode } from 'react'

import { cn } from '@/lib/cn'

/** 系统管理增删改共用的浮层。普通弹窗点遮罩或按 Esc 关闭；危险确认必须显式选择。 */
export function FormDialog({
  title,
  description,
  closeLabel,
  onClose,
  wide = false,
  size,
  placement = 'center',
  danger = false,
  children,
}: {
  title: string
  description?: string
  closeLabel: string
  onClose: () => void
  wide?: boolean
  size?: 's' | 'm' | 'l'
  placement?: 'center' | 'end'
  danger?: boolean
  children: ReactNode
}) {
  const resolvedSize = size ?? (wide ? 'l' : 'm')

  useEffect(() => {
    if (danger) return
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [danger, onClose])

  const widthClass =
    placement === 'end' ? 'h-full w-full max-w-[560px]' : resolvedSize === 's' ? 'max-w-[400px]' : resolvedSize === 'l' ? 'max-w-[720px]' : 'max-w-[560px]'

  return (
    <div
      className={cn(
        'fixed inset-0 z-50 flex',
        placement === 'end' ? 'justify-end' : 'items-center justify-center p-4',
      )}
    >
      <button
        type="button"
        className="admin-overlay absolute inset-0"
        aria-label={closeLabel}
        onClick={danger ? undefined : onClose}
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="form-dialog-title"
        className={cn(
          'shadow-admin-overlay relative z-10 flex w-full flex-col border border-admin bg-admin-surface',
          placement === 'end' ? 'h-full border-y-0 border-r-0' : 'max-h-[min(90vh,48rem)] overflow-hidden rounded-xl',
          widthClass,
        )}
      >
        <div className="flex h-16 shrink-0 items-center justify-between gap-3 border-b border-admin px-5">
          <div className="flex min-w-0 items-center gap-3">
            {danger ? (
              <span className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-admin-danger-muted text-admin-danger">
                <TriangleAlert className="h-5 w-5" />
              </span>
            ) : null}
            <div className="min-w-0">
              <h2 id="form-dialog-title" className="truncate text-base font-semibold text-admin">
                {title}
              </h2>
              {description ? <p className="truncate text-sm text-admin-muted">{description}</p> : null}
            </div>
          </div>
          {danger ? null : (
            <button type="button" onClick={onClose} className="admin-icon-button admin-focus-ring" aria-label={closeLabel}>
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-5">{children}</div>
      </div>
    </div>
  )
}
