import { cn } from '@/lib/cn'

export interface FilterTabItem {
  key: string
  label: string
  count?: number
}

interface FilterTabsProps {
  tabs: FilterTabItem[]
  activeKey: string
  onChange: (key: string) => void
  className?: string
}

export function FilterTabs({ tabs, activeKey, onChange, className }: FilterTabsProps) {
  return (
    <div className={cn('admin-seg w-fit', className)} role="tablist">
      {tabs.map((tab) => {
        const active = tab.key === activeKey
        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(tab.key)}
            className={cn('admin-focus-ring inline-flex items-center gap-1.5', active && 'is-on')}
          >
            <span>{tab.label}</span>
            {tab.count != null ? (
              <span className="text-xs font-semibold text-primary tabular-nums">{tab.count}</span>
            ) : null}
          </button>
        )
      })}
    </div>
  )
}
