import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  DataTable,
  DataTableColumnMenu,
  DataTableRowActions,
  DataTableToolbar,
  StatusTag,
  useColumnVisibility,
  type DataTableColumn,
  type TableRowAction,
  type TableSortOrder,
} from '@/components/admin'
import { adminFilterFieldClass } from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import type { AiModelEntry } from '@/types/ai'
import { modelIdFromSpec } from '@/utils/modelSpec'

import {
  familyTile,
  formatEntryUpdatedAt,
  keyVendorName,
  matchesLocalQuery,
  specPrefix,
} from './modelEntriesShared'

type KeyPaneProps = {
  entries: AiModelEntry[]
  isLoading: boolean
  isError: boolean
  errorMessage?: string
  isRefreshing: boolean
  canUpdate: boolean
  setDefaultPending: boolean
  onRefresh: () => void
  onEdit: (entry: AiModelEntry) => void
  onSetDefault: (entry: AiModelEntry) => void
  onDelete: (entry: AiModelEntry) => void
}

/**
 * KEY 模式列表：DataTable + 本地搜索/厂商筛选；密钥只展示脱敏 hint。
 */
export function ModelEntriesKeyPane({
  entries,
  isLoading,
  isError,
  errorMessage,
  isRefreshing,
  canUpdate,
  setDefaultPending,
  onRefresh,
  onEdit,
  onSetDefault,
  onDelete,
}: KeyPaneProps) {
  const { t } = useTranslation('aiAdmin')
  const [query, setQuery] = useState('')
  const [vendorFilter, setVendorFilter] = useState('')
  const [sortColumnId, setSortColumnId] = useState<string | null>('name')
  const [sortOrder, setSortOrder] = useState<TableSortOrder>('asc')

  const vendors = useMemo(() => {
    const found = new Set(entries.map((entry) => specPrefix(entry.modelSpec)))
    return [...found].sort()
  }, [entries])

  const filtered = useMemo(() => {
    return entries.filter((entry) => {
      if (vendorFilter && specPrefix(entry.modelSpec) !== vendorFilter) return false
      return matchesLocalQuery(
        [
          entry.displayName,
          entry.modelSpec,
          entry.alias,
          entry.apiKeyHint,
          entry.baseUrl,
          keyVendorName(specPrefix(entry.modelSpec)),
        ],
        query,
      )
    })
  }, [entries, query, vendorFilter])

  const filteredEmpty = !isLoading && !isError && filtered.length === 0 && entries.length > 0

  const allColumns = useMemo<DataTableColumn<AiModelEntry>[]>(
    () => [
      {
        id: 'vendor',
        header: t('modelManagement.columns.vendor'),
        cell: (row) => {
          const prefix = specPrefix(row.modelSpec)
          const tile = familyTile(prefix)
          return (
            <span className="inline-flex items-center gap-2">
              <span
                className={cn(
                  'flex h-7 w-7 shrink-0 items-center justify-center rounded-admin-md text-xs font-bold',
                  tile.className,
                )}
              >
                {tile.letter}
              </span>
              <span className="text-sm text-admin">{keyVendorName(prefix)}</span>
            </span>
          )
        },
        sortValue: (row) => specPrefix(row.modelSpec),
        sortable: true,
      },
      {
        id: 'name',
        header: t('modelManagement.columns.modelName'),
        hideable: false,
        cell: (row) => (
          <div className="min-w-0">
            <div className="truncate font-semibold text-admin">
              {row.displayName || modelIdFromSpec(row.modelSpec)}
            </div>
            {row.alias ? <div className="truncate font-mono text-xs text-admin-muted">{row.alias}</div> : null}
          </div>
        ),
        sortValue: (row) => row.displayName || modelIdFromSpec(row.modelSpec),
        sortable: true,
      },
      {
        id: 'modelSpec',
        header: t('modelManagement.columns.modelSpec'),
        cell: (row) => (
          <span className="block max-w-[18rem] break-all font-mono text-xs text-admin" title={row.modelSpec}>
            {row.modelSpec}
          </span>
        ),
        sortValue: (row) => row.modelSpec,
        sortable: true,
      },
      {
        id: 'apiKey',
        header: t('modelManagement.columns.apiKey'),
        cell: (row) =>
          row.apiKeyConfigured ? (
            <span className="rounded-md bg-admin-surface-muted px-1.5 py-0.5 font-mono text-xs text-admin-muted">
              {row.apiKeyHint || t('modelManagement.keyConfigured')}
            </span>
          ) : (
            <StatusTag status="warning" tone="warning" label={t('modelManagement.keyMissing')} />
          ),
      },
      {
        id: 'baseUrl',
        header: t('modelManagement.columns.baseUrl'),
        cell: (row) => (
          <span className="block max-w-[16rem] break-all font-mono text-xs text-admin-muted" title={row.baseUrl ?? undefined}>
            {row.baseUrl || '—'}
          </span>
        ),
      },
      {
        id: 'enabled',
        header: t('modelManagement.columns.enabled'),
        cell: (row) =>
          row.enabled ? (
            <StatusTag status="ACTIVE" tone="active" label={t('modelManagement.enabledYes')} />
          ) : (
            <StatusTag status="DISABLED" tone="danger" label={t('settings.disabledBadge')} />
          ),
      },
      {
        id: 'defaultEntry',
        header: t('modelManagement.columns.platformDefault'),
        cell: (row) =>
          row.defaultEntry ? (
            <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
              {t('settings.defaultBadgeKey')}
            </span>
          ) : (
            <span className="text-xs text-admin-muted">—</span>
          ),
      },
      {
        id: 'updatedAt',
        header: t('modelManagement.columns.updatedAt'),
        cell: (row) => (
          <span className="whitespace-nowrap text-xs text-admin-muted">{formatEntryUpdatedAt(row.updatedAt)}</span>
        ),
        sortValue: (row) => row.updatedAt ?? '',
        sortable: true,
      },
      {
        id: 'actions',
        header: t('modelManagement.columns.actions'),
        hideable: false,
        className: 'w-12 text-right',
        headerClassName: 'text-right',
        cell: (row) => {
          const actions: Array<TableRowAction<AiModelEntry>> = [
            {
              id: 'edit',
              label: t('modelManagement.actions.edit'),
              onSelect: onEdit,
              disabled: !canUpdate || row.derived,
              disabledReason: row.derived ? t('modelManagement.derivedNote') : undefined,
            },
          ]
          if (canUpdate && row.enabled && !row.defaultEntry && !row.derived) {
            actions.push({
              id: 'setDefault',
              label: t('settings.setDefault'),
              onSelect: onSetDefault,
              disabled: setDefaultPending,
            })
          }
          if (canUpdate && !row.derived) {
            actions.push({
              id: 'delete',
              label: t('settings.remove'),
              onSelect: onDelete,
              danger: true,
              confirmLabel: t('modelManagement.actions.confirmDelete'),
            })
          }
          return (
            <div onClick={(event) => event.stopPropagation()}>
              <DataTableRowActions row={row} actions={actions} />
            </div>
          )
        },
      },
    ],
    [canUpdate, onDelete, onEdit, onSetDefault, setDefaultPending, t],
  )

  const columnVisibility = useColumnVisibility(allColumns, {
    storageKey: 'admin-web.model-entries-key.columns',
  })
  const visibleColumns = columnVisibility.visibleColumns as DataTableColumn<AiModelEntry>[]

  function resetFilters() {
    setQuery('')
    setVendorFilter('')
  }

  return (
    <div className="space-y-3">
      <DataTableToolbar
        value={query}
        onChange={setQuery}
        onReset={resetFilters}
        onRefresh={onRefresh}
        isRefreshing={isRefreshing}
        placeholder={t('modelManagement.searchPlaceholder')}
        filters={
          <select
            className={adminFilterFieldClass}
            aria-label={t('modelManagement.columns.vendor')}
            value={vendorFilter}
            onChange={(event) => setVendorFilter(event.target.value)}
          >
            <option value="">{t('modelManagement.filterAllVendors')}</option>
            {vendors.map((vendor) => (
              <option key={vendor} value={vendor}>
                {keyVendorName(vendor)}
              </option>
            ))}
          </select>
        }
        trailing={
          <DataTableColumnMenu
            columns={allColumns.map((column) => ({
              id: column.id,
              label: String(column.header),
              hideable: column.hideable,
              visible: columnVisibility.isVisible(column.id),
            }))}
            onToggle={columnVisibility.toggle}
            onReset={columnVisibility.reset}
          />
        }
      />
      <p className="text-xs text-admin-muted">{t('modelManagement.localSearchHint')}</p>
      <DataTable
        columns={visibleColumns}
        data={filtered}
        rowKey={(row) => row.id}
        isLoading={isLoading}
        isError={isError}
        errorMessage={errorMessage}
        onRetry={onRefresh}
        emptyMessage={filteredEmpty ? undefined : t('settings.empty')}
        emptyKind={filteredEmpty ? 'filtered' : 'empty'}
        onClearFilters={filteredEmpty ? resetFilters : undefined}
        onRowClick={(row) => {
          if (row.derived || !canUpdate) return
          onEdit(row)
        }}
        sort={{
          columnId: sortColumnId,
          order: sortOrder,
          scope: 'client-page',
          onChange: (next) => {
            setSortColumnId(next.columnId)
            setSortOrder(next.order)
          },
        }}
      />
    </div>
  )
}
