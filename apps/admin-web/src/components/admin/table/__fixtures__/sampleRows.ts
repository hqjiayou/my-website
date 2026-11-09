/** PR-1 测试夹具：非生产业务数据，仅用于纯函数/边界脚本。 */
export interface SampleRow {
  id: number
  name: string
  status: 'ACTIVE' | 'DISABLED'
  updatedAt: string
}

export const SAMPLE_COLUMNS = [
  { id: 'name', hideable: false, defaultVisible: true, sortValue: (row: SampleRow) => row.name },
  { id: 'status', hideable: true, defaultVisible: true, sortValue: (row: SampleRow) => row.status },
  { id: 'updatedAt', hideable: true, defaultVisible: true, sortValue: (row: SampleRow) => row.updatedAt },
  { id: 'actions', hideable: false, defaultVisible: true },
] as const

export const SAMPLE_ROWS: SampleRow[] = [
  { id: 1, name: 'Beta', status: 'DISABLED', updatedAt: '2026-11-01T10:00:00Z' },
  { id: 2, name: 'Alpha', status: 'ACTIVE', updatedAt: '2026-11-08T10:00:00Z' },
  { id: 3, name: 'Gamma', status: 'ACTIVE', updatedAt: '2026-11-05T10:00:00Z' },
]
