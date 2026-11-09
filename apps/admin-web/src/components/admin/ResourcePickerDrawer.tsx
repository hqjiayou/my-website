import { Search } from 'lucide-react'
import { useMemo, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'

import {
  adminFieldClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from './controls'
import { FormDialog } from './formDialog'

export type ResourcePickerMode = 'single' | 'multiple'

export interface ResourcePickerColumn<T> {
  id: string
  header: string
  className?: string
  cell: (item: T) => ReactNode
}

export interface ResourcePickerDrawerProps<T> {
  open: boolean
  title: string
  description?: string
  items: T[]
  getId: (item: T) => string
  columns: Array<ResourcePickerColumn<T>>
  /** 打开时的已选 ID（抽屉内编辑副本；确认才回传）。 */
  initialSelectedIds?: string[]
  mode?: ResourcePickerMode
  /** 返回说明则该项不可新选；已选中的失效项仍可取消勾选。 */
  disabledReason?: (item: T) => string | null
  /** 已绑定/已在链中：展示标记，默认不可再选。 */
  alreadySelectedIds?: string[]
  alreadySelectedLabel?: string
  alreadySelectedSelectable?: boolean
  isLoading?: boolean
  isError?: boolean
  errorMessage?: string
  onRetry?: () => void
  emptyMessage?: string
  searchPlaceholder?: string
  searchText?: (item: T) => string
  confirmLabel?: string
  cancelLabel?: string
  discardTitle?: string
  discardBody?: string
  discardConfirmLabel?: string
  /** 确认仅回传选中 ID，不代表已写入服务端。 */
  onConfirm: (ids: string[]) => void
  onClose: () => void
}

/**
 * 通用资源选择抽屉：搜索 / 状态 / 单多选 / 确认与取消。
 * 业务过滤与服务端保存由调用方适配层负责。
 * open→挂载内层并冻结基线；关闭卸载。打开期间勿用 initialSelectedIds 作 key，避免后台刷新重置勾选。
 */
export function ResourcePickerDrawer<T>(props: ResourcePickerDrawerProps<T>) {
  if (!props.open) return null
  return <ResourcePickerDrawerSession {...props} />
}

function ResourcePickerDrawerSession<T>({
  title,
  description,
  items,
  getId,
  columns,
  initialSelectedIds = [],
  mode = 'multiple',
  disabledReason,
  alreadySelectedIds = [],
  alreadySelectedLabel,
  alreadySelectedSelectable = false,
  isLoading = false,
  isError = false,
  errorMessage,
  onRetry,
  emptyMessage,
  searchPlaceholder,
  searchText,
  confirmLabel,
  cancelLabel,
  discardTitle,
  discardBody,
  discardConfirmLabel,
  onConfirm,
  onClose,
}: ResourcePickerDrawerProps<T>) {
  const { t } = useTranslation('common')
  const [query, setQuery] = useState('')
  // 打开瞬间快照：dirty 与初始勾选都相对此基线，不随父级后续 props 漂移。
  const [baselineIds] = useState(() => [...initialSelectedIds])
  const [selectedIds, setSelectedIds] = useState(() => [...initialSelectedIds])
  const [discardOpen, setDiscardOpen] = useState(false)

  const alreadySet = useMemo(() => new Set(alreadySelectedIds), [alreadySelectedIds])
  const selectedSet = useMemo(() => new Set(selectedIds), [selectedIds])

  const dirty = useMemo(() => {
    if (selectedIds.length !== baselineIds.length) return true
    const baseline = new Set(baselineIds)
    return selectedIds.some((id) => !baseline.has(id))
  }, [baselineIds, selectedIds])

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase()
    if (!needle) return items
    return items.filter((item) => {
      const haystack = (searchText?.(item) ?? getId(item)).toLowerCase()
      return haystack.includes(needle)
    })
  }, [getId, items, query, searchText])

  function requestClose() {
    if (dirty) {
      setDiscardOpen(true)
      return
    }
    onClose()
  }

  function toggle(id: string, locked: boolean) {
    if (locked) return
    if (mode === 'single') {
      setSelectedIds((current) => (current[0] === id ? [] : [id]))
      return
    }
    setSelectedIds((current) =>
      current.includes(id) ? current.filter((value) => value !== id) : [...current, id],
    )
  }

  return (
    <>
      <FormDialog
        title={title}
        description={description}
        closeLabel={cancelLabel ?? t('resourcePicker.cancel')}
        onClose={requestClose}
        placement="end"
        size="l"
      >
        <div className="flex h-full min-h-0 flex-col gap-3" data-testid="resource-picker-session">
          <label className="relative block">
            <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-admin-muted" aria-hidden />
            <input
              className={cn(adminFieldClass, 'pl-9')}
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={searchPlaceholder ?? t('resourcePicker.searchPlaceholder')}
              aria-label={searchPlaceholder ?? t('resourcePicker.searchPlaceholder')}
            />
          </label>

          {isLoading ? (
            <p className="text-sm text-admin-muted">{t('table.loading')}</p>
          ) : isError ? (
            <div className="rounded-lg border border-danger/35 bg-danger-muted px-3 py-2 text-sm text-danger-emphasis">
              <p>{errorMessage ?? t('table.error')}</p>
              {onRetry ? (
                <button type="button" className="mt-2 underline" onClick={onRetry}>
                  {t('table.retry')}
                </button>
              ) : null}
            </div>
          ) : filtered.length === 0 ? (
            <p className="text-sm text-admin-muted">{emptyMessage ?? t('table.noMatches')}</p>
          ) : (
            <div className="min-h-0 flex-1 overflow-auto rounded-xl border border-admin">
              <table className="w-full min-w-[28rem] border-collapse text-left text-sm">
                <thead className="sticky top-0 bg-admin-surface-muted text-xs font-semibold tracking-wide text-admin-muted uppercase">
                  <tr>
                    <th className="w-10 px-3 py-2" scope="col">
                      <span className="sr-only">{t('table.selectRow')}</span>
                    </th>
                    {columns.map((column) => (
                      <th key={column.id} className={cn('px-3 py-2', column.className)} scope="col">
                        {column.header}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((item) => {
                    const id = getId(item)
                    const reason = disabledReason?.(item) ?? null
                    const already = alreadySet.has(id)
                    const checked = selectedSet.has(id)
                    const locked =
                      (Boolean(reason) && !checked) || (already && !alreadySelectedSelectable && !checked)
                    return (
                      <tr
                        key={id}
                        className={cn(
                          'border-t border-admin',
                          locked ? 'bg-admin-surface-muted/60 opacity-70' : 'hover:bg-admin-surface-muted/40',
                        )}
                      >
                        <td className="px-3 py-2 align-middle">
                          <input
                            type={mode === 'single' ? 'radio' : 'checkbox'}
                            className="admin-check"
                            name={mode === 'single' ? 'resource-picker-single' : undefined}
                            checked={checked}
                            disabled={locked}
                            aria-label={id}
                            title={reason ?? undefined}
                            onChange={() => toggle(id, locked)}
                          />
                        </td>
                        {columns.map((column) => (
                          <td key={column.id} className={cn('px-3 py-2 align-middle', column.className)}>
                            {column.cell(item)}
                            {column.id === columns[0]?.id ? (
                              <span className="mt-1 flex flex-wrap gap-1">
                                {already ? (
                                  <span className="rounded-full bg-info-muted px-1.5 py-0.5 text-[10px] font-medium text-info-emphasis">
                                    {alreadySelectedLabel ?? t('resourcePicker.alreadySelected')}
                                  </span>
                                ) : null}
                                {reason ? (
                                  <span className="rounded-full bg-warning-muted px-1.5 py-0.5 text-[10px] font-medium text-warning-emphasis">
                                    {reason}
                                  </span>
                                ) : null}
                              </span>
                            ) : null}
                          </td>
                        ))}
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}

          <div className={cn(adminFormActionsClass, 'mt-auto border-t border-admin pt-3')}>
            <p className="mr-auto text-xs text-admin-muted">
              {t('table.selectedCount', { count: selectedIds.length })}
            </p>
            <button type="button" className={adminGhostButtonClass} onClick={requestClose}>
              {cancelLabel ?? t('resourcePicker.cancel')}
            </button>
            <button
              type="button"
              className={adminPrimaryButtonClass}
              disabled={isLoading || isError}
              onClick={() => onConfirm(selectedIds)}
            >
              {confirmLabel ?? t('resourcePicker.confirm')}
            </button>
          </div>
        </div>
      </FormDialog>

      {discardOpen ? (
        <FormDialog
          title={discardTitle ?? t('resourcePicker.discardTitle')}
          closeLabel={cancelLabel ?? t('resourcePicker.cancel')}
          onClose={() => setDiscardOpen(false)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">{discardBody ?? t('resourcePicker.discardBody')}</p>
          <div className={adminFormActionsClass}>
            <button type="button" className={adminSecondaryButtonClass} onClick={() => setDiscardOpen(false)}>
              {cancelLabel ?? t('resourcePicker.cancel')}
            </button>
            <button
              type="button"
              className={adminPrimaryButtonClass}
              onClick={() => {
                setDiscardOpen(false)
                onClose()
              }}
            >
              {discardConfirmLabel ?? t('resourcePicker.discardConfirm')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </>
  )
}
