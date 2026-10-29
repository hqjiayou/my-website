import { useEffect, type ReactNode } from 'react'

/** 系统管理增删改共用的居中弹层。点遮罩或按 Esc 关闭。 */
export function FormDialog({
  title,
  description,
  closeLabel,
  onClose,
  wide = false,
  children,
}: {
  title: string
  description?: string
  closeLabel: string
  onClose: () => void
  wide?: boolean
  children: ReactNode
}) {
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <button type="button" className="absolute inset-0 bg-slate-950/45" aria-label={closeLabel} onClick={onClose} />
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="form-dialog-title"
        className={`relative z-10 max-h-[min(90vh,48rem)] w-full overflow-y-auto rounded-xl border border-admin bg-admin-surface p-6 shadow-xl ${wide ? 'max-w-3xl' : 'max-w-lg'}`}
      >
        <h2 id="form-dialog-title" className="text-lg font-semibold text-admin">
          {title}
        </h2>
        {description ? <p className="mt-1 text-sm text-admin-muted">{description}</p> : null}
        <div className="mt-4">{children}</div>
      </div>
    </div>
  )
}
