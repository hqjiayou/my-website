/** 系统管理页共用的控件类，跟壳层主题色和密度走。 */
const adminFieldChrome =
  'rounded-lg border border-admin bg-admin-surface px-3 text-sm text-admin outline-none placeholder:text-admin-muted focus:ring-2 focus:ring-[rgb(var(--primary)/0.28)] disabled:bg-admin-surface-muted disabled:text-admin-muted admin-control'

export const adminFieldClass = `w-full ${adminFieldChrome}`

/** 列表筛选：固定短宽度，不把一行拉满。 */
export const adminFilterFieldClass = `w-40 ${adminFieldChrome}`

export const adminFormActionsClass = 'mt-4 flex justify-end gap-2'

export const adminPrimaryButtonClass =
  'inline-flex items-center justify-center gap-2 rounded-lg bg-admin-primary px-3 text-sm font-medium text-admin-on-primary transition hover:opacity-90 disabled:opacity-60 admin-control'

export const adminSecondaryButtonClass =
  'inline-flex items-center justify-center gap-1.5 rounded-lg border border-admin bg-admin-surface px-3 text-sm font-medium text-admin transition hover:bg-admin-surface-muted disabled:opacity-60 admin-control'

export const adminGhostButtonClass =
  'inline-flex items-center gap-1 rounded-md border border-admin bg-admin-surface px-2 py-1 text-xs text-admin transition hover:bg-admin-surface-muted disabled:cursor-not-allowed disabled:opacity-40'

export const adminDangerButtonClass =
  'inline-flex items-center gap-1 rounded-md border border-rose-300 px-2 py-1 text-xs text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-40'
