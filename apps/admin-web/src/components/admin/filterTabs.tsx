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
    <div className={cn('flex flex-wrap gap-2', className)} role="tablist">
      {tabs.map((tab) => {
        const active = tab.key === activeKey
        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={active}
            onClick={() => onChange(tab.key)}
            className={cn(
              'inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium transition',
              active ? 'bg-slate-900 text-white' : 'border border-slate-200 text-slate-700 hover:bg-slate-50',
            )}
          >
            <span>{tab.label}</span>
            {tab.count != null ? (
              <span
                className={cn(
                  'rounded-full px-1.5 py-0.5 text-xs font-semibold',
                  active ? 'bg-white/20 text-white' : 'bg-slate-100 text-slate-600',
                )}
              >
                {tab.count}
              </span>
            ) : null}
          </button>
        )
      })}
    </div>
  )
}
