import { cn } from '@/lib/cn'
import { useLayoutStore } from '@/stores/layout'

export function AdminMobileBackdrop() {
  const { isSidebarOpen, closeSidebar } = useLayoutStore()

  return (
    <div
      aria-hidden
      className={cn(
        'fixed inset-0 z-30 bg-slate-900/40 transition-opacity lg:hidden',
        isSidebarOpen ? 'opacity-100' : 'pointer-events-none opacity-0',
      )}
      onClick={closeSidebar}
    />
  )
}
