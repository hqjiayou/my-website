import { AlertCircle } from 'lucide-react'
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
import {
  adminGhostButtonClass,
  adminSecondaryButtonSmClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import type {
  AiModelEntry,
  ProxyLoginProvider,
  ProxyServicesAccount,
  ProxyServicesPublishedAccount,
} from '@/types/ai'
import { modelIdFromSpec } from '@/utils/modelSpec'

import { ProviderIcon } from './providerIcon'
import {
  formatEntryUpdatedAt,
  inferredCliProvider,
  matchesLocalQuery,
} from './modelEntriesShared'
import {
  accountStatus,
  accountTitle,
  isLoginProvider,
  providerLabel,
  providerTileClass,
  shownModelIds,
} from './proxyServicesShared'

export type CliTableRow = {
  key: string
  entry: AiModelEntry
  account: ProxyServicesAccount | null
  provider: string
  usedPercent: number | null
  published: boolean
}

type CliPaneProps = {
  entries: AiModelEntry[]
  accounts: ProxyServicesAccount[]
  publishedAccounts: ProxyServicesPublishedAccount[]
  statusPending: boolean
  statusError: boolean
  usagePending: boolean
  usageError: boolean
  isLoading: boolean
  isError: boolean
  errorMessage?: string
  isRefreshing: boolean
  canUpdate: boolean
  setDefaultPending: boolean
  onRefresh: () => void
  onRetryStatus: () => void
  onRetryUsage: () => void
  onOpenEntry: (entry: AiModelEntry) => void
  onSetDefault: (entry: AiModelEntry) => void
  onRelogin: (provider: ProxyLoginProvider | null) => void
}

function buildCliRows(
  entries: AiModelEntry[],
  accounts: ProxyServicesAccount[],
  publishedAccounts: ProxyServicesPublishedAccount[],
  usagePending: boolean,
): CliTableRow[] {
  const publishedByName = new Map(publishedAccounts.map((account) => [account.name, account]))
  const matchedIds = new Set<number>()
  const rows: CliTableRow[] = []

  for (const account of accounts) {
    const ids = usagePending
      ? []
      : (shownModelIds(account.provider, publishedByName.get(account.name)?.modelIds ?? []) ?? [])
    for (const entry of entries) {
      const modelId = modelIdFromSpec(entry.modelSpec)
      if (!ids.includes(modelId)) continue
      matchedIds.add(entry.id)
      rows.push({
        key: `${account.name}:${entry.id}`,
        entry,
        account,
        provider: account.provider,
        usedPercent: account.modelUsedPercent[modelId] ?? null,
        published: true,
      })
    }
  }

  if (!usagePending) {
    for (const entry of entries) {
      if (matchedIds.has(entry.id)) continue
      rows.push({
        key: `detached:${entry.id}`,
        entry,
        account: null,
        provider: inferredCliProvider(entry),
        usedPercent: null,
        published: false,
      })
    }
  }

  return rows
}

/**
 * CLI 模式：账号状态条 + 单表 DataTable（账号列体现从属）；派生只读；用量仅真实字段。
 */
export function ModelEntriesCliPane({
  entries,
  accounts,
  publishedAccounts,
  statusPending,
  statusError,
  usagePending,
  usageError,
  isLoading,
  isError,
  errorMessage,
  isRefreshing,
  canUpdate,
  setDefaultPending,
  onRefresh,
  onRetryStatus,
  onRetryUsage,
  onOpenEntry,
  onSetDefault,
  onRelogin,
}: CliPaneProps) {
  const { t } = useTranslation('aiAdmin')
  const [query, setQuery] = useState('')
  const [sortColumnId, setSortColumnId] = useState<string | null>('account')
  const [sortOrder, setSortOrder] = useState<TableSortOrder>('asc')

  const allRows = useMemo(
    () => buildCliRows(entries, accounts, publishedAccounts, usagePending),
    [accounts, entries, publishedAccounts, usagePending],
  )

  const filtered = useMemo(
    () =>
      allRows.filter((row) =>
        matchesLocalQuery(
          [
            row.account ? accountTitle(row.account) : t('modelManagement.unmatchedBlock'),
            providerLabel(row.provider, t),
            row.entry.displayName,
            row.entry.modelSpec,
            modelIdFromSpec(row.entry.modelSpec),
          ],
          query,
        ),
      ),
    [allRows, query, t],
  )

  const filteredEmpty = !isLoading && !isError && filtered.length === 0 && allRows.length > 0
  const unhealthyAccounts = accounts.filter((account) => accountStatus(account, t).tone !== 'active')

  const allColumns = useMemo<DataTableColumn<CliTableRow>[]>(
    () => [
      {
        id: 'provider',
        header: t('modelManagement.columns.vendor'),
        cell: (row) => (
          <span className="inline-flex items-center gap-2">
            <span
              className={cn(
                'flex h-7 w-7 shrink-0 items-center justify-center rounded-admin-md',
                providerTileClass(row.provider),
              )}
            >
              {isLoginProvider(row.provider) ? (
                <ProviderIcon id={row.provider} className="h-4 w-4" />
              ) : (
                <span className="text-xs font-bold">{row.provider.slice(0, 1).toUpperCase()}</span>
              )}
            </span>
            <span className="text-sm text-admin">{providerLabel(row.provider, t)}</span>
          </span>
        ),
        sortValue: (row) => row.provider,
        sortable: true,
      },
      {
        id: 'account',
        header: t('modelManagement.columns.account'),
        hideable: false,
        cell: (row) =>
          row.account ? (
            <div className="min-w-0">
              <div className="truncate text-sm font-medium text-admin">{accountTitle(row.account)}</div>
              <div className="truncate font-mono text-xs text-admin-muted">{row.account.name}</div>
            </div>
          ) : (
            <span className="text-xs text-admin-muted">{t('modelManagement.unmatchedBlock')}</span>
          ),
        sortValue: (row) => (row.account ? accountTitle(row.account) : ''),
        sortable: true,
      },
      {
        id: 'auth',
        header: t('modelManagement.columns.authStatus'),
        cell: (row) => {
          if (!row.account) {
            return <StatusTag status="warning" tone="warning" label={t('modelManagement.accountDetached')} />
          }
          const status = accountStatus(row.account, t)
          return <StatusTag status={status.tone} tone={status.tone} label={status.label} />
        },
      },
      {
        id: 'model',
        header: t('modelManagement.columns.model'),
        hideable: false,
        cell: (row) => {
          const modelId = modelIdFromSpec(row.entry.modelSpec)
          const title =
            row.entry.displayName?.trim() && row.entry.displayName !== modelId
              ? row.entry.displayName
              : modelId
          return (
            <div className="min-w-0">
              <div className="truncate font-semibold text-admin">{title || modelId || '—'}</div>
              <div className="truncate font-mono text-xs text-admin-muted">{modelId || row.entry.modelSpec}</div>
            </div>
          )
        },
        sortValue: (row) => modelIdFromSpec(row.entry.modelSpec),
        sortable: true,
      },
      {
        id: 'usage',
        header: t('modelManagement.columns.usage'),
        cell: (row) =>
          row.usedPercent != null ? (
            <span className="font-mono text-xs tabular-nums text-admin-muted" title={t('proxyServices.statQuota')}>
              {row.usedPercent}%
            </span>
          ) : (
            <span className="text-xs text-admin-muted">{t('proxyServices.quotaUnknown')}</span>
          ),
      },
      {
        id: 'entryStatus',
        header: t('modelManagement.columns.entryStatus'),
        cell: (row) => (
          <span className="flex flex-wrap items-center gap-1.5">
            {row.entry.derived ? (
              <StatusTag status="neutral" tone="neutral" label={t('modelManagement.derivedBadge')} />
            ) : null}
            {!row.entry.enabled ? (
              <StatusTag status="DISABLED" tone="danger" label={t('settings.disabledBadge')} />
            ) : null}
            {row.entry.defaultEntry ? (
              <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                {t('settings.defaultBadgeCli')}
              </span>
            ) : null}
            {!row.published ? (
              <StatusTag status="warning" tone="warning" label={t('modelManagement.notPublished')} />
            ) : null}
          </span>
        ),
      },
      {
        id: 'updatedAt',
        header: t('modelManagement.columns.updatedAt'),
        defaultVisible: false,
        cell: (row) => (
          <span className="whitespace-nowrap text-xs text-admin-muted">
            {formatEntryUpdatedAt(row.entry.updatedAt)}
          </span>
        ),
        sortValue: (row) => row.entry.updatedAt ?? '',
        sortable: true,
      },
      {
        id: 'actions',
        header: t('modelManagement.columns.actions'),
        hideable: false,
        className: 'w-12 text-right',
        headerClassName: 'text-right',
        cell: (row) => {
          const actions: Array<TableRowAction<CliTableRow>> = [
            {
              id: 'open',
              label: row.entry.derived
                ? t('modelManagement.actions.view')
                : t('modelManagement.actions.edit'),
              onSelect: (item) => onOpenEntry(item.entry),
            },
          ]
          if (canUpdate && row.entry.enabled && !row.entry.defaultEntry && !row.entry.derived) {
            actions.push({
              id: 'setDefault',
              label: t('settings.setDefault'),
              onSelect: (item) => onSetDefault(item.entry),
              disabled: setDefaultPending,
            })
          }
          if (canUpdate && row.account && accountStatus(row.account, t).tone !== 'active') {
            actions.push({
              id: 'relogin',
              label: t('modelManagement.relogin'),
              onSelect: (item) =>
                onRelogin(item.account && isLoginProvider(item.account.provider) ? item.account.provider : null),
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
    [canUpdate, onOpenEntry, onRelogin, onSetDefault, setDefaultPending, t],
  )

  const columnVisibility = useColumnVisibility(allColumns, {
    storageKey: 'admin-web.model-entries-cli.columns',
  })
  const visibleColumns = columnVisibility.visibleColumns as DataTableColumn<CliTableRow>[]

  function resetFilters() {
    setQuery('')
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

      {statusError ? (
        <div className="flex items-start gap-3 rounded-xl border border-danger/35 bg-danger-muted px-4 py-3">
          <AlertCircle className="mt-0.5 h-[18px] w-[18px] shrink-0 text-danger-emphasis" aria-hidden />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-danger-emphasis">{t('modelManagement.proxyStatusError')}</p>
            <button type="button" className={cn(adminSecondaryButtonSmClass, 'mt-2')} onClick={onRetryStatus}>
              {t('retry')}
            </button>
          </div>
        </div>
      ) : null}

      {usageError ? (
        <div className="flex items-start gap-3 rounded-xl border border-danger/35 bg-danger-muted px-4 py-3">
          <AlertCircle className="mt-0.5 h-[18px] w-[18px] shrink-0 text-danger-emphasis" aria-hidden />
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium text-danger-emphasis">{t('modelManagement.proxyUsageError')}</p>
            <button type="button" className={cn(adminSecondaryButtonSmClass, 'mt-2')} onClick={onRetryUsage}>
              {t('retry')}
            </button>
          </div>
        </div>
      ) : null}

      {!statusPending && unhealthyAccounts.length > 0 ? (
        <div className="space-y-2">
          {unhealthyAccounts.map((account) => {
            const status = accountStatus(account, t)
            return (
              <div
                key={account.name}
                className="flex flex-wrap items-center gap-3 rounded-xl border border-warning/35 bg-warning-muted px-4 py-3"
              >
                <StatusTag status={status.tone} tone={status.tone} label={status.label} />
                <span className="text-sm text-warning-emphasis">
                  {accountTitle(account)} · {providerLabel(account.provider, t)}
                </span>
                <span className="flex-1" />
                {canUpdate ? (
                  <button
                    type="button"
                    className={cn(adminGhostButtonClass, 'shrink-0')}
                    onClick={() => onRelogin(isLoginProvider(account.provider) ? account.provider : null)}
                  >
                    {t('modelManagement.relogin')}
                  </button>
                ) : null}
              </div>
            )
          })}
        </div>
      ) : null}

      <DataTable
        columns={visibleColumns}
        data={filtered}
        rowKey={(row) => row.key}
        isLoading={isLoading || statusPending || usagePending}
        isError={isError}
        errorMessage={errorMessage}
        onRetry={onRefresh}
        emptyMessage={
          filteredEmpty
            ? undefined
            : accounts.length === 0
              ? t('proxyServices.emptyAccounts')
              : t('modelManagement.noCliModels')
        }
        emptyKind={filteredEmpty ? 'filtered' : 'empty'}
        emptyDescription={accounts.length === 0 ? t('modelManagement.addCliDesc') : undefined}
        onClearFilters={filteredEmpty ? resetFilters : undefined}
        onRowClick={(row) => onOpenEntry(row.entry)}
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
