import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'

import { SearchToolbar } from '../searchToolbar'

/**
 * 工具栏层次：左侧搜索 + 筛选（filters）→ 右侧列管理/主操作（trailing）。
 * 搜索行为委托给 SearchToolbar（受控、回车、可选防抖）。
 */
export function DataTableToolbar({
  value,
  onChange,
  onSearch,
  onReset,
  onRefresh,
  placeholder,
  isRefreshing,
  debounceMs,
  showSearch = true,
  filters,
  trailing,
  className,
}: {
  value: string
  onChange: (value: string) => void
  onSearch?: () => void
  onReset: () => void
  onRefresh: () => void
  placeholder?: string
  isRefreshing?: boolean
  debounceMs?: number
  showSearch?: boolean
  filters?: ReactNode
  trailing?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex flex-wrap items-start justify-between gap-3', className)}>
      <SearchToolbar
        value={value}
        onChange={onChange}
        onSearch={onSearch}
        onReset={onReset}
        onRefresh={onRefresh}
        placeholder={placeholder}
        isRefreshing={isRefreshing}
        debounceMs={debounceMs}
        showSearch={showSearch}
        extra={filters}
        className="min-w-0 flex-1"
      />
      {trailing ? <div className="flex flex-wrap items-center gap-2">{trailing}</div> : null}
    </div>
  )
}
