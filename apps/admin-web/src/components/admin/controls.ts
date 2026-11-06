/** 系统管理页共用的控件类，跟壳层主题色和密度走。 */

const adminFocusRing = 'admin-focus-ring'

const adminFieldChrome =
  'admin-field-chrome rounded-lg border border-admin bg-admin-surface px-3 text-sm text-admin outline-none placeholder:text-admin-muted disabled:cursor-not-allowed disabled:bg-admin-surface-muted disabled:text-admin-muted admin-control'

export const adminFieldClass = `w-full ${adminFieldChrome}`

/** 列表筛选：固定短宽度，不把一行拉满。 */
export const adminFilterFieldClass = `w-40 ${adminFieldChrome}`

export const adminFormActionsClass = 'mt-4 flex justify-end gap-2'

export const adminPrimaryButtonClass =
  `inline-flex items-center justify-center gap-2 rounded-lg bg-admin-primary px-3 text-sm font-medium text-admin-on-primary transition hover:opacity-90 active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 admin-control ${adminFocusRing}`

export const adminSecondaryButtonClass =
  `inline-flex items-center justify-center gap-1.5 rounded-lg border border-admin bg-admin-surface px-3 text-sm font-medium text-admin transition hover:bg-admin-surface-muted disabled:cursor-not-allowed disabled:opacity-60 admin-control ${adminFocusRing}`

/** 次按钮 sm 档（28）：Alert 重试、行内次级操作等紧凑场景；与 md 档同一视觉家族。 */
export const adminSecondaryButtonSmClass =
  `inline-flex h-7 items-center justify-center gap-1 rounded-md border border-admin bg-admin-surface px-2 text-xs font-medium text-admin transition hover:bg-admin-surface-muted disabled:cursor-not-allowed disabled:opacity-40 ${adminFocusRing}`

export const adminGhostButtonClass =
  `inline-flex items-center gap-1 rounded-md border border-admin bg-admin-surface px-2 py-1 text-xs text-admin transition hover:bg-admin-surface-muted disabled:cursor-not-allowed disabled:opacity-40 ${adminFocusRing}`

const adminDangerSm = 'h-7 gap-1 rounded-md px-2 text-xs'
const adminDangerMd = 'gap-2 rounded-lg px-3 text-sm admin-control'
const adminDangerLg = 'h-11 gap-2 rounded-lg px-4 text-sm'

const adminDangerSolid =
  'admin-danger-solid inline-flex items-center justify-center border border-transparent bg-admin-danger font-medium text-admin-on-danger transition active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60'

export const adminDangerButtonSmClass = `${adminDangerSolid} ${adminDangerSm} ${adminFocusRing}`
export const adminDangerButtonMdClass = `${adminDangerSolid} ${adminDangerMd} ${adminFocusRing}`
export const adminDangerButtonLgClass = `${adminDangerSolid} ${adminDangerLg} ${adminFocusRing}`

/** 实心危险按钮，默认 sm（28）。行内删除若要描边样式，改用 danger-ghost。 */
export const adminDangerButtonClass = adminDangerButtonSmClass

const adminDangerGhost =
  'admin-danger-ghost inline-flex items-center justify-center border bg-transparent font-medium text-admin-danger transition disabled:cursor-not-allowed disabled:opacity-40'

export const adminDangerGhostButtonSmClass = `${adminDangerGhost} ${adminDangerSm} ${adminFocusRing}`
export const adminDangerGhostButtonMdClass = `${adminDangerGhost} ${adminDangerMd} ${adminFocusRing}`
export const adminDangerGhostButtonLgClass = `${adminDangerGhost} ${adminDangerLg} ${adminFocusRing}`
export const adminDangerGhostButtonClass = adminDangerGhostButtonSmClass
