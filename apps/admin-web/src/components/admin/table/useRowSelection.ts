import { useCallback, useMemo, useState } from 'react'

import { setPageSelection, toggleKeySelection } from './tableHelpers'

/**
 * 初期约定：仅当前页选择语义由调用方在换页/筛选时 clear()。
 * 本 hook 不跨页持久化，避免伪装「全局已选」。
 */
export function useRowSelection(options?: {
  initialSelected?: Array<string | number>
}) {
  const [selectedKeys, setSelectedKeys] = useState<Array<string | number>>(
    () => options?.initialSelected ?? [],
  )

  const selectedSet = useMemo(() => new Set(selectedKeys), [selectedKeys])

  const isSelected = useCallback((key: string | number) => selectedSet.has(key), [selectedSet])

  const toggle = useCallback((key: string | number, checked: boolean) => {
    setSelectedKeys((prev) => toggleKeySelection(prev, key, checked))
  }, [])

  const setPage = useCallback((pageKeys: Array<string | number>, checked: boolean) => {
    setSelectedKeys((prev) => setPageSelection(prev, pageKeys, checked))
  }, [])

  const clear = useCallback(() => setSelectedKeys([]), [])

  return {
    selectedKeys,
    selectedCount: selectedKeys.length,
    isSelected,
    toggle,
    setPage,
    clear,
    setSelectedKeys,
  }
}
