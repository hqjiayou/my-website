import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { agentApi } from '@/api'
import { aiApi } from '@/api/apps/aiApi'
import {
  AdminPageHeader,
  DataTable,
  DataTableColumnMenu,
  DataTableRowActions,
  DataTableToolbar,
  FormDialog,
  StatusTag,
  useColumnVisibility,
  type DataTableColumn,
  type TableRowAction,
} from '@/components/admin'
import {
  adminFilterFieldClass,
  adminPrimaryButtonClass,
} from '@/components/admin/controls'
import { ROUTES } from '@/router/config/routePaths'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'
import { AGENT_KIND_VALUES, SITE_STATUS_VALUES, SITE_TYPE_PRESETS, type AgentKind, type AgentListItem } from '@/types/site'

import { AgentCreateDialog } from './agentCreateDialog'

const PAGE_SIZE_OPTIONS = [10, 20, 50]

const inlineSelectClass =
  'max-w-44 rounded-lg border border-admin bg-admin-surface px-2 py-1 text-xs text-admin disabled:opacity-50'

/**
 * 智能体列表 V2：复用 PR-1 表格底座；筛选仅对接既有 page/status/agentType，
 * 不把本地搜索/排序伪装成服务端全局能力。
 */
export function AgentsPage() {
  const { t } = useTranslation('sites')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.agentUpdate)
  const canPrompt = useHasPermission(PERMISSION_CODES.aiPromptRead)
  const [createOpen, setCreateOpen] = useState(false)
  const [pageNum, setPageNum] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [statusFilter, setStatusFilter] = useState('')
  const [agentTypeFilter, setAgentTypeFilter] = useState('')
  const [subscriptionAgent, setSubscriptionAgent] = useState<AgentListItem | null>(null)

  const sitesQuery = useQuery({
    queryKey: ['sites', { pageNum, pageSize, status: statusFilter, agentType: agentTypeFilter }],
    queryFn: () =>
      agentApi.listSites({
        pageNum,
        pageSize,
        status: statusFilter || undefined,
        agentType: agentTypeFilter || undefined,
      }),
  })
  const invalidateSites = () => {
    void queryClient.invalidateQueries({ queryKey: ['sites'] })
  }

  const kindMutation = useMutation({
    mutationFn: (input: { id: number; kind: AgentKind; version: number }) =>
      agentApi.updateAgentKind(input.id, { kind: input.kind, version: input.version }),
    onSuccess: () => {
      toast.success(t('list.messages.kindUpdated'))
      invalidateSites()
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : t('list.messages.kindUpdated'))
      invalidateSites()
    },
  })

  const records = sitesQuery.data?.records ?? []
  const total = sitesQuery.data?.total ?? 0
  const filteredEmpty = !sitesQuery.isPending && !sitesQuery.isError && records.length === 0 && Boolean(statusFilter || agentTypeFilter)

  const allColumns = useMemo<DataTableColumn<AgentListItem>[]>(
    () => [
      {
        id: 'name',
        header: t('list.columns.agentName'),
        hideable: false,
        cell: (row) => (
          <div className="min-w-0">
            <div className="truncate font-semibold text-admin">{row.agentName}</div>
            <div className="truncate font-mono text-xs text-admin-muted">{row.agentCode}</div>
            {row.defaultDomain ? (
              <div className="truncate text-xs text-admin-muted">{row.defaultDomain}</div>
            ) : null}
          </div>
        ),
      },
      {
        id: 'kind',
        header: t('list.columns.agentKind'),
        cell: (row) => (
          <div onClick={(event) => event.stopPropagation()}>
            <select
              className={`${inlineSelectClass} font-medium`}
              aria-label={t('list.columns.agentKind')}
              value={row.agentKind}
              disabled={!canUpdate || (kindMutation.isPending && kindMutation.variables?.id === row.id)}
              onChange={(event) =>
                kindMutation.mutate({ id: row.id, kind: event.target.value as AgentKind, version: row.version })
              }
            >
              {AGENT_KIND_VALUES.map((kind) => (
                <option key={kind} value={kind}>
                  {t(`list.kind.${kind}`)}
                </option>
              ))}
            </select>
            <div className="mt-1 text-xs text-admin-muted">
              {t(`agentType.${row.agentType}`, { defaultValue: row.agentType })}
            </div>
          </div>
        ),
      },
      {
        id: 'status',
        header: t('list.columns.status'),
        hideable: false,
        cell: (row) => (
          <StatusTag status={row.status} label={t(`status.${row.status}`, { defaultValue: row.status })} />
        ),
      },
      {
        id: 'chain',
        header: t('list.associations.chainPreset'),
        cell: (row) => (
          <span className="inline-flex items-center rounded bg-admin-surface-muted px-1.5 py-0.5 font-mono text-xs text-admin">
            {row.chainKeyCount === 0 && row.chainCliCount === 0
              ? t('list.associations.chainNone')
              : `KEY ${row.chainKeyCount} / CLI ${row.chainCliCount}`}
          </span>
        ),
      },
      {
        id: 'prompt',
        header: t('list.associations.prompt'),
        cell: (row) => (
          <div className="flex flex-wrap items-center gap-1.5">
            <span className="rounded bg-admin-surface-muted px-1.5 py-0.5 text-xs text-admin">
              {row.promptTemplateName ??
                (row.promptConfigured ? t('list.associations.promptLocal') : t('list.associations.none'))}
            </span>
            {row.defaultSkillCount > 0 ? (
              <span className="rounded bg-admin-surface-muted px-1.5 py-0.5 text-xs text-admin">
                {t('list.associations.promptSkills', { count: row.defaultSkillCount })}
              </span>
            ) : null}
          </div>
        ),
      },
      {
        id: 'subscriptions',
        header: t('list.associations.subscriptions'),
        defaultVisible: true,
        cell: (row) => (
          <div className="flex items-center gap-2" onClick={(event) => event.stopPropagation()}>
            <span className="inline-flex items-center gap-1 rounded bg-admin-surface-muted px-1.5 py-0.5 text-xs text-admin">
              <BookOpen className="h-3.5 w-3.5" aria-hidden />
              {row.subscriptionCount}
            </span>
            <button
              type="button"
              className="text-xs font-medium text-primary transition hover:underline"
              onClick={() => setSubscriptionAgent(row)}
            >
              {t('list.associations.manageSubscriptions')}
            </button>
          </div>
        ),
      },
      {
        id: 'updatedAt',
        header: t('list.columns.updatedAt'),
        cell: (row) => (
          <span className="whitespace-nowrap text-xs text-admin-muted">
            {new Date(row.updatedAt).toLocaleString()}
          </span>
        ),
      },
      {
        id: 'actions',
        header: t('list.columns.actions'),
        hideable: false,
        className: 'w-12 text-right',
        headerClassName: 'text-right',
        cell: (row) => {
          const actions: Array<TableRowAction<AgentListItem>> = [
            {
              id: 'open',
              label: t('list.actions.openWorkspace'),
              onSelect: (item) => navigate(ROUTES.apps.agent(item.id)),
            },
          ]
          if (canPrompt) {
            actions.push({
              id: 'debug',
              label: t('workspace.actions.debug'),
              onSelect: (item) => navigate(ROUTES.apps.agentDebug(item.id)),
            })
          }
          actions.push({
            id: 'subscriptions',
            label: t('list.associations.manageSubscriptions'),
            onSelect: (item) => setSubscriptionAgent(item),
          })
          return (
            <div onClick={(event) => event.stopPropagation()}>
              <DataTableRowActions row={row} actions={actions} />
            </div>
          )
        },
      },
    ],
    [canPrompt, canUpdate, kindMutation, navigate, t],
  )

  const columnVisibility = useColumnVisibility(allColumns, {
    storageKey: 'admin-web.agents-table.columns',
  })
  const visibleColumns = columnVisibility.visibleColumns as DataTableColumn<AgentListItem>[]

  function resetFilters() {
    setStatusFilter('')
    setAgentTypeFilter('')
    setPageNum(1)
  }

  return (
    <section className="admin-content-data">
      <AdminPageHeader
        title={t('list.title')}
        description={t('list.description')}
        action={
          <PermissionGate code={PERMISSION_CODES.agentCreate}>
            <button type="button" onClick={() => setCreateOpen(true)} className={adminPrimaryButtonClass}>
              <Plus className="h-4 w-4" />
              {t('list.createSite')}
            </button>
          </PermissionGate>
        }
      />

      <div className="mb-3 space-y-3">
        <DataTableToolbar
          value=""
          onChange={() => undefined}
          onReset={resetFilters}
          onRefresh={() => void sitesQuery.refetch()}
          isRefreshing={sitesQuery.isFetching}
          showSearch={false}
          filters={
            <>
              <select
                className={adminFilterFieldClass}
                aria-label={t('list.columns.status')}
                value={statusFilter}
                onChange={(event) => {
                  setStatusFilter(event.target.value)
                  setPageNum(1)
                }}
              >
                <option value="">{t('list.filters.allStatus')}</option>
                {SITE_STATUS_VALUES.map((status) => (
                  <option key={status} value={status}>
                    {t(`status.${status}`)}
                  </option>
                ))}
              </select>
              <select
                className={adminFilterFieldClass}
                aria-label={t('list.columns.agentType')}
                value={agentTypeFilter}
                onChange={(event) => {
                  setAgentTypeFilter(event.target.value)
                  setPageNum(1)
                }}
              >
                <option value="">{t('list.filters.allAgentType')}</option>
                {SITE_TYPE_PRESETS.map((preset) => (
                  <option key={preset} value={preset}>
                    {t(`agentType.${preset}`, { defaultValue: preset })}
                  </option>
                ))}
              </select>
            </>
          }
          trailing={
            <DataTableColumnMenu
              columns={allColumns.map((column) => ({
                id: column.id,
                label: column.header,
                hideable: column.hideable,
                visible: columnVisibility.isVisible(column.id),
              }))}
              onToggle={columnVisibility.toggle}
              onReset={columnVisibility.reset}
            />
          }
        />
        <p className="text-xs text-admin-muted">{t('list.filters.serverOnlyHint')}</p>
      </div>

      <DataTable
        columns={visibleColumns}
        data={records}
        rowKey={(row) => row.id}
        isLoading={sitesQuery.isPending}
        isError={sitesQuery.isError}
        errorMessage={sitesQuery.error instanceof Error ? sitesQuery.error.message : undefined}
        onRetry={() => void sitesQuery.refetch()}
        emptyMessage={filteredEmpty ? undefined : t('list.empty')}
        emptyKind={filteredEmpty ? 'filtered' : 'empty'}
        onClearFilters={filteredEmpty ? resetFilters : undefined}
        onRowClick={(row) => navigate(ROUTES.apps.agent(row.id))}
        pagination={{
          page: pageNum,
          pageSize,
          total,
          onPageChange: setPageNum,
          pageSizeOptions: PAGE_SIZE_OPTIONS,
          onPageSizeChange: (next) => {
            setPageSize(next)
            setPageNum(1)
          },
        }}
      />

      {subscriptionAgent ? (
        <SubscriptionDialog agent={subscriptionAgent} onClose={() => setSubscriptionAgent(null)} />
      ) : null}
      {createOpen ? <AgentCreateDialog onClose={() => setCreateOpen(false)} /> : null}
    </section>
  )
}

function SubscriptionDialog({ agent, onClose }: { agent: AgentListItem; onClose: () => void }) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const canManageKb = useHasPermission(PERMISSION_CODES.aiKbUpdate)

  const basesQuery = useQuery({
    queryKey: ['ai-knowledge-bases-all'],
    queryFn: () => aiApi.listAllKnowledgeBases(),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['ai-knowledge-bases-all'] })
    void queryClient.invalidateQueries({ queryKey: ['ai-knowledge-overview'] })
    void queryClient.invalidateQueries({ queryKey: ['sites'] })
  }

  const linkMutation = useMutation({
    mutationFn: (baseId: number) => aiApi.linkKnowledgeBase(baseId, agent.id),
    onSuccess: () => {
      toast.success(t('list.messages.subscribed'))
      invalidate()
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : t('list.messages.subscribed')),
  })
  const unlinkMutation = useMutation({
    mutationFn: (baseId: number) => aiApi.unlinkKnowledgeBase(baseId, agent.id),
    onSuccess: () => {
      toast.success(t('list.messages.unsubscribed'))
      invalidate()
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : t('list.messages.unsubscribed')),
  })

  const bases = [...(basesQuery.data ?? [])].sort((a, b) => a.name.localeCompare(b.name))
  const pending = linkMutation.isPending || unlinkMutation.isPending

  return (
    <FormDialog
      title={`${t('list.subscriptions.title')} · ${agent.agentName}`}
      closeLabel={t('create.close')}
      onClose={onClose}
    >
      <p className="text-sm text-admin-muted">{t('list.subscriptions.hint')}</p>
      {basesQuery.isPending ? (
        <p className="mt-4 text-sm text-admin-muted">{t('workspace.loading')}</p>
      ) : basesQuery.isError ? (
        <p className="mt-4 text-sm text-danger-emphasis">
          {basesQuery.error instanceof Error ? basesQuery.error.message : t('list.subscriptions.empty')}
        </p>
      ) : bases.length === 0 ? (
        <p className="mt-4 text-sm text-admin-muted">{t('list.subscriptions.empty')}</p>
      ) : (
        <ul className="mt-4 max-h-80 space-y-1 overflow-y-auto">
          {bases.map((base) => {
            const owned = base.agentId === agent.id
            const subscribed = owned || base.linkedAgentIds.includes(agent.id)
            return (
              <li key={base.id}>
                <label
                  className={`flex items-start gap-2 rounded-lg border border-admin px-3 py-2 ${
                    owned ? 'bg-admin-surface-muted' : ''
                  }`}
                >
                  <input
                    type="checkbox"
                    className="admin-check mt-0.5"
                    checked={subscribed}
                    disabled={owned || !canManageKb || pending}
                    onChange={(event) =>
                      event.target.checked ? linkMutation.mutate(base.id) : unlinkMutation.mutate(base.id)
                    }
                  />
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-2 text-sm font-medium text-admin">
                      {base.name}
                      {owned ? (
                        <span className="rounded-full bg-info-muted px-1.5 py-0.5 text-xs text-info-emphasis">
                          {t('list.subscriptions.own')}
                        </span>
                      ) : null}
                      {base.isDefault ? (
                        <span className="rounded-full bg-admin-surface-muted px-1.5 py-0.5 text-xs text-admin-muted">
                          {t('aiWorkspace.knowledge.baseDefault')}
                        </span>
                      ) : null}
                    </span>
                    <span className="block text-xs text-admin-muted">
                      {t('list.subscriptions.owner', { name: base.ownerAgentName ?? `#${base.agentId}` })}
                      {' · '}
                      {t('list.subscriptions.documents', { count: base.documentCount })}
                    </span>
                  </span>
                </label>
              </li>
            )
          })}
        </ul>
      )}
    </FormDialog>
  )
}
