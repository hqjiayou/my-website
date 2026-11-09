import type { ReactNode } from 'react'

/** 1-based 列表查询视图状态；字段是否可发给服务端由业务页决定。 */
export interface AdminTableQuery {
  q?: string
  status?: string
  provider?: string
  sort?: string
  order?: 'asc' | 'desc'
  page: number
  pageSize: number
}

export type TableSortOrder = 'asc' | 'desc'

/**
 * server：排序作用于服务端/已加载全集，由调用方负责。
 * client-page：仅对传入表格的 `data`（当前页或当前已加载结果）排序，不得宣称全局。
 */
export type TableSortScope = 'server' | 'client-page'

export interface TableSortState {
  columnId: string | null
  order: TableSortOrder
  scope: TableSortScope
}

export interface DataTableColumnDef<T> {
  id: string
  header: ReactNode
  cell: (row: T) => ReactNode
  className?: string
  headerClassName?: string
  /** 参与排序时表头可点击；client-page 需提供 sortValue。 */
  sortable?: boolean
  sortValue?: (row: T) => string | number | null | undefined
  /** false 时列管理不可隐藏；默认 true。 */
  hideable?: boolean
  /** 默认是否显示；默认 true。 */
  defaultVisible?: boolean
}

export interface TableRowAction<T> {
  id: string
  label: string
  onSelect: (row: T) => void
  disabled?: boolean
  disabledReason?: string
  danger?: boolean
  /** 危险操作二次确认文案；有值时 RowActions 先确认再执行。 */
  confirmLabel?: string
}

export interface TableBulkAction {
  id: string
  label: string
  onSelect: (selectedKeys: Array<string | number>) => void
  disabled?: boolean
  disabledReason?: string
  danger?: boolean
  /** 无真实后端能力时设为 false，批量条不渲染该操作。 */
  available?: boolean
}

export type TableEmptyKind = 'empty' | 'filtered' | 'forbidden'
