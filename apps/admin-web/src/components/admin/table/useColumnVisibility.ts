import { useCallback, useMemo, useState } from 'react'

import { readStoredStringArray, resolveVisibleColumns, toggleColumnHidden, writeStoredStringArray } from './tableHelpers'
import type { DataTableColumnDef } from './types'

export function useColumnVisibility<T>(
  columns: Array<Pick<DataTableColumnDef<T>, 'id' | 'hideable' | 'defaultVisible' | 'header'>>,
  options?: { storageKey?: string },
) {
  const storageKey = options?.storageKey
  const [hiddenIds, setHiddenIds] = useState<string[]>(() => {
    if (!storageKey) return []
    return readStoredStringArray(storageKey) ?? []
  })

  const visibleColumnIds = useMemo(
    () => resolveVisibleColumns(columns, hiddenIds),
    [columns, hiddenIds],
  )

  const visibleColumns = useMemo(
    () => columns.filter((column) => visibleColumnIds.includes(column.id)),
    [columns, visibleColumnIds],
  )

  const persist = useCallback(
    (next: string[]) => {
      setHiddenIds(next)
      if (storageKey) writeStoredStringArray(storageKey, next)
    },
    [storageKey],
  )

  const toggle = useCallback(
    (columnId: string) => {
      persist(toggleColumnHidden(columns, hiddenIds, columnId))
    },
    [columns, hiddenIds, persist],
  )

  const reset = useCallback(() => {
    persist([])
  }, [persist])

  const isVisible = useCallback((columnId: string) => visibleColumnIds.includes(columnId), [visibleColumnIds])

  return {
    hiddenIds,
    visibleColumnIds,
    visibleColumns,
    isVisible,
    toggle,
    reset,
  }
}
