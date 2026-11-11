import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'

type EditState = 'clean' | 'dirty' | 'saving' | 'saved' | 'error'

/**
 * 配置子页统一壳：说明 → 摘要 → 内容 → 操作反馈。
 * 不承载业务保存逻辑，只统一视觉层次。
 */
export function AgentConfigSectionShell({
  title,
  description,
  summary,
  editState,
  editStateLabel,
  actions,
  children,
  className,
}: {
  title: string
  description?: string
  summary?: ReactNode
  editState?: EditState
  editStateLabel?: string
  actions?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={cn('space-y-4', className)}>
      <header className="space-y-2">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            <h2 className="text-base font-semibold text-admin">{title}</h2>
            {description ? <p className="mt-1 text-sm text-admin-muted">{description}</p> : null}
          </div>
          {editState && editState !== 'clean' && editStateLabel ? (
            <span
              className={cn(
                'sticky top-2 z-[1] shrink-0 rounded-full px-2.5 py-1 text-xs font-medium',
                editState === 'dirty' && 'bg-warning-muted text-warning-emphasis',
                editState === 'saving' && 'bg-info-muted text-info-emphasis',
                editState === 'saved' && 'bg-success-muted text-success-emphasis',
                editState === 'error' && 'bg-danger-muted text-danger-emphasis',
              )}
              role="status"
            >
              {editStateLabel}
            </span>
          ) : null}
        </div>
        {summary ? (
          <div className="rounded-lg border border-admin bg-admin-surface-muted/40 px-3 py-2 text-sm text-admin">
            {summary}
          </div>
        ) : null}
      </header>
      <div className="min-w-0 space-y-4">{children}</div>
      {actions ? <div className="flex flex-wrap items-center gap-2 border-t border-admin pt-3">{actions}</div> : null}
    </section>
  )
}
