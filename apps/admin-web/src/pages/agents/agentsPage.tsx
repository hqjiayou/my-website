import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BookOpen, Plus } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { agentApi } from '@/api'
import { aiApi } from '@/api/apps/aiApi'
import { AdminPageHeader, DataTable, FormDialog, StatusTag, type DataTableColumn } from '@/components/admin'
import {
  adminFilterFieldClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
} from '@/components/admin/controls'
import { ROUTES } from '@/router/config/routePaths'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'
import { AGENT_KIND_VALUES, SITE_STATUS_VALUES, SITE_TYPE_PRESETS, type AgentKind, type AgentListItem } from '@/types/site'

import { AgentCreateDialog } from './agentCreateDialog'

const PAGE_SIZE = 20

/** 行内小下拉（与知识库页共享行同款式）。 */
const inlineSelectClass =
  'max-w-44 rounded-lg border border-admin bg-admin-surface px-2 py-1 text-xs text-admin disabled:opacity-50'

/**
 * 智能体列表（Issue #213 UoW-B）：由「进站自动跳转」改为表格页——类型（kind）
 * 与三类关联（订阅空间数／链预设／配置预设）一屏看清，且都能行内直接改，
 * 不必进资源本体页：kind 走专属端点（带 version 乐观锁），链预设／配置预设
 * 走各自的引用端点，订阅走知识库 link/unlink（以空间为 base、行 agent 为 target）。
 * 自由文本的用途标签 agentType 只作名称下的小字展示，与 kind 分开、不混。
 */
export function AgentsPage() {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.agentUpdate)
  const [createOpen, setCreateOpen] = useState(false)
  const [pageNum, setPageNum] = useState(1)
  const [statusFilter, setStatusFilter] = useState('')
  const [agentTypeFilter, setAgentTypeFilter] = useState('')
  const [subscriptionAgent, setSubscriptionAgent] = useState<AgentListItem | null>(null)

  const sitesQuery = useQuery({
    queryKey: ['sites', { pageNum, pageSize: PAGE_SIZE, status: statusFilter, agentType: agentTypeFilter }],
    queryFn: () =>
      agentApi.listSites({
        pageNum,
        pageSize: PAGE_SIZE,
        status: statusFilter || undefined,
        agentType: agentTypeFilter || undefined,
      }),
  })
  const chainPresetsQuery = useQuery({
    queryKey: ['ai', 'chain-presets'],
    queryFn: () => aiApi.listChainPresets(),
  })
  const configPresetsQuery = useQuery({
    queryKey: ['ai', 'config-presets'],
    queryFn: () => aiApi.listConfigPresets(),
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
      // 冲突（含 version 不符）时后端消息原样提示，并刷新列表回同步最新 version。
      toast.error(error instanceof Error ? error.message : t('list.messages.kindUpdated'))
      invalidateSites()
    },
  })

  const chainPresetMutation = useMutation({
    mutationFn: (input: { agentId: number; chainPresetId: number | null }) =>
      aiApi.updateAgentChainPreset(input.agentId, input.chainPresetId),
    onSuccess: () => {
      toast.success(t('list.messages.chainPresetUpdated'))
      invalidateSites()
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : t('list.messages.chainPresetUpdated'))
      invalidateSites()
    },
  })

  const configPresetMutation = useMutation({
    mutationFn: (input: { id: number; configPresetId: number | null }) =>
      agentApi.updateAgentConfigPreset(input.id, { configPresetId: input.configPresetId }),
    onSuccess: () => {
      toast.success(t('list.messages.configPresetUpdated'))
      invalidateSites()
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : t('list.messages.configPresetUpdated'))
      invalidateSites()
    },
  })

  const chainPresets = chainPresetsQuery.data ?? []
  const configPresets = configPresetsQuery.data ?? []
  const records = sitesQuery.data?.records ?? []
  const total = sitesQuery.data?.total ?? 0

  /** 预设下拉选项：当前引用若已不在列表里（预设被删），用列表项自带的 id＋名补一条，保证显示不丢。 */
  function presetOptions(
    currentId: number | null,
    currentName: string | null,
    presets: { id: number; name: string }[],
  ) {
    const options = presets.map((preset) => ({ id: preset.id, name: preset.name }))
    if (currentId != null && !options.some((option) => option.id === currentId)) {
      options.push({ id: currentId, name: currentName ?? `#${currentId}` })
    }
    return options
  }

  const columns: DataTableColumn<AgentListItem>[] = [
    {
      id: 'name',
      header: t('list.columns.agentName'),
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
        <div>
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
          {/* 用途标签（自由文本）与 kind 分开：只作小字展示，不参与类型切换。 */}
          <div className="mt-1 text-xs text-admin-muted">
            {t(`agentType.${row.agentType}`, { defaultValue: row.agentType })}
          </div>
        </div>
      ),
    },
    {
      id: 'status',
      header: t('list.columns.status'),
      cell: (row) => (
        <StatusTag status={row.status} label={t(`status.${row.status}`, { defaultValue: row.status })} />
      ),
    },
    {
      id: 'associations',
      header: t('list.columns.associations'),
      className: 'min-w-64',
      cell: (row) => (
        <div className="flex flex-col gap-1.5">
          <div className="flex items-center gap-2">
            <span className="w-16 shrink-0 text-xs text-admin-muted">{t('list.associations.subscriptions')}</span>
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
          <div className="flex items-center gap-2">
            <span className="w-16 shrink-0 text-xs text-admin-muted">{t('list.associations.chainPreset')}</span>
            <select
              className={inlineSelectClass}
              aria-label={t('list.associations.chainPreset')}
              value={row.chainPresetId != null ? String(row.chainPresetId) : ''}
              disabled={
                !canUpdate || (chainPresetMutation.isPending && chainPresetMutation.variables?.agentId === row.id)
              }
              onChange={(event) =>
                chainPresetMutation.mutate({
                  agentId: row.id,
                  chainPresetId: event.target.value === '' ? null : Number(event.target.value),
                })
              }
            >
              <option value="">{t('list.associations.none')}</option>
              {presetOptions(row.chainPresetId, row.chainPresetName, chainPresets).map((option) => (
                <option key={option.id} value={String(option.id)}>
                  {option.name}
                </option>
              ))}
            </select>
          </div>
          <div className="flex items-center gap-2">
            <span className="w-16 shrink-0 text-xs text-admin-muted">{t('list.associations.configPreset')}</span>
            <select
              className={inlineSelectClass}
              aria-label={t('list.associations.configPreset')}
              value={row.configPresetId != null ? String(row.configPresetId) : ''}
              disabled={
                !canUpdate ||
                (configPresetMutation.isPending && configPresetMutation.variables?.id === row.id)
              }
              onChange={(event) =>
                configPresetMutation.mutate({
                  id: row.id,
                  configPresetId: event.target.value === '' ? null : Number(event.target.value),
                })
              }
            >
              <option value="">{t('list.associations.none')}</option>
              {presetOptions(row.configPresetId, row.configPresetName, configPresets).map((option) => (
                <option key={option.id} value={String(option.id)}>
                  {option.name}
                </option>
              ))}
            </select>
          </div>
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
      cell: (row) => (
        <Link to={ROUTES.apps.agent(row.id)} className={adminGhostButtonClass}>
          {t('list.actions.openWorkspace')}
        </Link>
      ),
    },
  ]

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

      <div className="mb-4 flex flex-wrap items-center gap-2">
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
        {statusFilter || agentTypeFilter ? (
          <button
            type="button"
            className={adminGhostButtonClass}
            onClick={() => {
              setStatusFilter('')
              setAgentTypeFilter('')
              setPageNum(1)
            }}
          >
            {t('list.filters.reset')}
          </button>
        ) : null}
      </div>

      <DataTable
        columns={columns}
        data={records}
        rowKey={(row) => row.id}
        isLoading={sitesQuery.isPending}
        isError={sitesQuery.isError}
        errorMessage={sitesQuery.error instanceof Error ? sitesQuery.error.message : undefined}
        onRetry={() => void sitesQuery.refetch()}
        emptyMessage={t('list.empty')}
        pagination={{ page: pageNum, pageSize: PAGE_SIZE, total, onPageChange: setPageNum }}
      />

      {subscriptionAgent ? (
        <SubscriptionDialog agent={subscriptionAgent} onClose={() => setSubscriptionAgent(null)} />
      ) : null}
      {createOpen ? <AgentCreateDialog onClose={() => setCreateOpen(false)} /> : null}
    </section>
  )
}

/**
 * 订阅管理弹层：列全部知识空间，勾选状态由空间侧数据推导——空间的创建者
 * （base.agentId＝本 agent）为「自有」行、恒勾选且不可取消；linkedAgentIds
 * 含本 agent 即已订阅。勾选变化即调 link/unlink（baseId＋targetAgentId）。
 */
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
