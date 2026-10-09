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
        <nav className="mb-2 flex flex-wrap items-center gap-1 text-xs text-slate-500" aria-label={t('breadcrumb')}>
          {breadcrumbs.map((item, index) => (
            <span key={`${item.label}-${index}`} className="inline-flex items-center gap-1">
              {index > 0 ? <span className="text-slate-300">/</span> : null}
              {item.to ? (
                <Link to={item.to} className="transition hover:text-slate-700">
                  {item.label}
                </Link>
              ) : (
                <span className="text-slate-700">{item.label}</span>
              )}
            </span>
          ))}
        </nav>
      ) : null}

      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
          {description ? <p className="mt-1 text-sm text-slate-500">{description}</p> : null}
        </div>
        {action ? <div className="shrink-0">{action}</div> : null}
      </div>

      {hint ? (
        <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">{hint}</div>
      ) : null}
    </header>
  )
}
