/**
 * PR-1：表格底座纯逻辑验收（无 DOM）。
 * 运行：node --experimental-strip-types scripts/check-table-foundation.ts
 */
import assert from 'node:assert/strict'

import {
  parseAdminTableQuery,
  resolveVisibleColumns,
  serializeAdminTableQuery,
  setPageSelection,
  sortClientPageRows,
  toggleColumnHidden,
  toggleKeySelection,
  toggleSortOrder,
  withPageReset,
} from '../src/components/admin/table/tableHelpers.ts'
import { SAMPLE_COLUMNS, SAMPLE_ROWS } from '../src/components/admin/table/__fixtures__/sampleRows.ts'

function run() {
  const sorted = sortClientPageRows(
    SAMPLE_ROWS,
    [...SAMPLE_COLUMNS],
    { columnId: 'name', order: 'asc' },
  )
  assert.deepEqual(
    sorted.map((row) => row.name),
    ['Alpha', 'Beta', 'Gamma'],
    'client-page sort by name asc',
  )

  const nextSort = toggleSortOrder({ columnId: 'name', order: 'asc', scope: 'client-page' }, 'name')
  assert.equal(nextSort.order, 'desc')

  let hidden = toggleColumnHidden([...SAMPLE_COLUMNS], [], 'status')
  assert.deepEqual(hidden, ['status'])
  // 不可把必需列 name/actions 与全部可隐藏列一起藏光到零列
  hidden = toggleColumnHidden([...SAMPLE_COLUMNS], ['status', 'updatedAt'], 'name')
  assert.deepEqual(hidden, ['status', 'updatedAt'], 'required column stays visible')

  const visible = resolveVisibleColumns([...SAMPLE_COLUMNS], ['status', 'updatedAt', 'name', 'actions'])
  assert.ok(visible.length >= 1, 'always keep at least one column')
  assert.ok(visible.includes('name') || visible.includes('actions'))

  const query = withPageReset(
    { page: 3, pageSize: 20, status: 'ACTIVE' },
    { q: 'alpha' },
  )
  assert.equal(query.page, 1, 'filter change resets page')
  assert.equal(query.q, 'alpha')

  const pageKept = withPageReset({ page: 2, pageSize: 20 }, { page: 4 })
  assert.equal(pageKept.page, 4)

  const params = serializeAdminTableQuery({
    page: 2,
    pageSize: 20,
    q: 'bot',
    sort: 'name',
    order: 'desc',
  })
  assert.equal(params.q, 'bot')
  const parsed = parseAdminTableQuery(params)
  assert.equal(parsed.page, 2)
  assert.equal(parsed.order, 'desc')

  let selected = toggleKeySelection([1], 2, true)
  assert.deepEqual(selected, [1, 2])
  selected = setPageSelection(selected, [2, 3], false)
  assert.deepEqual(selected, [1])

  // 批量动作契约：available:false 不得被当成可展示（组件层过滤；此处钉住约定）
  const actions = [
    { id: 'disable', available: false },
    { id: 'export', available: true },
  ]
  const usable = actions.filter((action) => action.available !== false)
  assert.deepEqual(
    usable.map((action) => action.id),
    ['export'],
  )

  console.log('ok: table foundation helpers')
}

run()
