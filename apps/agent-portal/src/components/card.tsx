import type { ReactNode } from 'react'

/**
 * 門戶統一卡片：表面色＋邊框 token＋面板圓角＋輕陰影。
 * 首頁模塊與知識庫列表共用，各頁不再各自拼 rounded/border 散裝樣式。
 */
export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <section className={`rounded-panel border border-border bg-surface p-4 shadow-raised ${className}`}>
      {children}
    </section>
  )
}

export function CardTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-2">
      <h2 className="text-sm font-semibold text-foreground">{children}</h2>
      {action}
    </div>
  )
}
