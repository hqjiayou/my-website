import { RefreshCw, RotateCcw, Search } from 'lucide-react'
import type { FormEvent, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

interface SearchToolbarProps {
  value: string
  onChange: (value: string) => void
  onSearch?: () => void
  onReset: () => void
  onRefresh: () => void
  placeholder?: string
  isRefreshing?: boolean
  extra?: ReactNode
  className?: string
}

export function SearchToolbar({
  value,
  onChange,
  onSearch,
  onReset,
  onRefresh,
  placeholder,
  isRefreshing = false,
  extra,
  className,
}: SearchToolbarProps) {
  const { t } = useTranslation('common')

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onSearch?.()
  }

  return (
    <div className={cn('flex flex-wrap items-center gap-2', className)}>
      <form onSubmit={handleSubmit} className="flex items-center gap-2">
        <div className="relative w-56">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-admin-muted" />
          <input
            className="w-full rounded-lg border border-admin bg-admin-surface py-2 pl-9 pr-3 text-sm text-admin outline-none placeholder:text-admin-muted focus:ring-2 focus:ring-[rgb(var(--primary)/0.28)]"
            placeholder={placeholder ?? t('table.searchPlaceholder')}
            value={value}
            onChange={(event) => onChange(event.target.value)}
          />
        </div>
      </form>

      {extra ? <div className="flex flex-wrap items-center gap-2">{extra}</div> : null}

      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={onReset}
          className="inline-flex items-center gap-1.5 rounded-lg border border-admin bg-admin-surface px-3 py-2 text-sm font-medium text-admin transition hover:bg-admin-surface-muted"
        >
          <RotateCcw className="h-4 w-4" />
          {t('table.reset')}
        </button>
        <button
          type="button"
          onClick={onRefresh}
          disabled={isRefreshing}
          className="inline-flex items-center gap-1.5 rounded-lg border border-admin bg-admin-surface px-3 py-2 text-sm font-medium text-admin transition hover:bg-admin-surface-muted disabled:opacity-60"
        >
          <RefreshCw className={cn('h-4 w-4', isRefreshing && 'animate-spin')} />
          {t('table.refresh')}
        </button>
      </div>
    </div>
  )
}
