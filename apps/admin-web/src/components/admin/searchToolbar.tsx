import { RefreshCw, RotateCcw, Search } from 'lucide-react'
import { useEffect, useRef, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

import { adminSecondaryButtonClass } from './controls'
import { useDebouncedValue } from './table/useDebouncedValue'

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
  /** >0 时对 value 防抖后触发 onSearch；Enter 仍立即提交当前 value。挂载时不触发。 */
  debounceMs?: number
  /** 无服务端搜索能力时隐藏输入框，只保留筛选 slot 与重置/刷新。 */
  showSearch?: boolean
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
  debounceMs = 0,
  showSearch = true,
}: SearchToolbarProps) {
  const { t } = useTranslation('common')
  const debouncedValue = useDebouncedValue(value, debounceMs)
  const skipDebounceEcho = useRef(true)

  useEffect(() => {
    if (!onSearch || debounceMs <= 0) return
    if (skipDebounceEcho.current) {
      skipDebounceEcho.current = false
      return
    }
    onSearch()
    // eslint-disable-next-line react-hooks/exhaustive-deps -- debounce mirror only
  }, [debouncedValue, debounceMs])

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    onSearch?.()
  }

  return (
    <div className={cn('flex flex-wrap items-center gap-2', className)}>
      {showSearch ? (
        <form onSubmit={handleSubmit} className="flex items-center gap-2">
          <div className="relative w-56">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-admin-muted" />
            <input
              className="admin-field-chrome h-9 w-full rounded-lg border border-admin bg-admin-surface pr-3 pl-9 text-sm text-admin outline-none placeholder:text-admin-muted"
              placeholder={placeholder ?? t('table.searchPlaceholder')}
              value={value}
              onChange={(event) => onChange(event.target.value)}
              aria-label={placeholder ?? t('table.searchPlaceholder')}
            />
          </div>
        </form>
      ) : null}

      {extra ? <div className="flex flex-wrap items-center gap-2">{extra}</div> : null}

      <div className="flex flex-wrap items-center gap-2">
        <button type="button" onClick={onReset} className={adminSecondaryButtonClass}>
          <RotateCcw className="h-4 w-4" />
          {t('table.reset')}
        </button>
        <button
          type="button"
          onClick={onRefresh}
          disabled={isRefreshing}
          className={adminSecondaryButtonClass}
        >
          <RefreshCw className={cn('h-4 w-4', isRefreshing && 'animate-spin')} />
          {t('table.refresh')}
        </button>
      </div>
    </div>
  )
}
