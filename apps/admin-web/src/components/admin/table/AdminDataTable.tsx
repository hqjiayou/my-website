import type { ReactNode } from 'react'

import { cn } from '@/lib/cn'

import { DataTable, type DataTableProps } from '../dataTable'

type AdminDataTableProps<T> = DataTableProps<T> & {
  toolbar?: ReactNode
  bulkBar?: ReactNode
  className?: string
  shellClassName?: string
}

/**
 * 组合壳：工具栏 / 批量条 / 表体分层，不吞业务数据逻辑。
 * 表体仍是同一个 DataTable，旧页面可继续直接用 DataTable。
 */
export function AdminDataTable<T>({
  toolbar,
  bulkBar,
  shellClassName,
  className,
  ...tableProps
}: AdminDataTableProps<T>) {
  return (
    <div className={cn('space-y-3', shellClassName)}>
      {toolbar}
      {bulkBar}
      <DataTable {...tableProps} className={className} />
    </div>
  )
}
