import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { cn } from '@/lib/cn'

export interface AdminBreadcrumbItem {
  label: string
  to?: string
}

interface AdminPageHeaderProps {
  title: string
  description?: string
  breadcrumbs?: AdminBreadcrumbItem[]
  action?: ReactNode
  hint?: ReactNode
  className?: string
}

export function AdminPageHeader({
  title,
  description,
  breadcrumbs,
  action,
  hint,
  className,
}: AdminPageHeaderProps) {
  const { t } = useTranslation('common')

  return (
    <header className={cn('mb-6', className)}>
      {breadcrumbs && breadcrumbs.length > 0 ? (
        <nav className="mb-2 flex flex-wrap items-center gap-2 text-sm text-admin-muted" aria-label={t('breadcrumb')}>
          {breadcrumbs.map((item, index) => {
            const isCurrent = index === breadcrumbs.length - 1
            return (
              <span key={`${item.label}-${index}`} className="inline-flex items-center gap-2">
                {index > 0 ? <span aria-hidden>›</span> : null}
                {item.to && !isCurrent ? (
                  <Link to={item.to} className="transition hover:text-admin">
                    {item.label}
                  </Link>
                ) : (
                  <span className={isCurrent ? 'font-medium text-primary' : undefined}>{item.label}</span>
                )}
              </span>
            )
          })}
        </nav>
      ) : null}

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <h1 className="text-xl font-semibold leading-[1.3] text-admin">{title}</h1>
          {description ? <p className="mt-1 text-sm text-admin-muted">{description}</p> : null}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>

      {hint ? (
        <div className="mt-3 rounded-lg border border-admin bg-admin-surface-muted px-3 py-2 text-sm text-admin-muted">{hint}</div>
      ) : null}
    </header>
  )
}
