import { useQuery } from '@tanstack/react-query'
import { Bot, CalendarDays, Cpu, History } from 'lucide-react'
import { useState, type ComponentType } from 'react'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import { AdminPageHeader, DataTable, FilterTabs, StatusTag, type DataTableColumn } from '@/components/admin'
import type { AiUsageByAgent, AiUsageByModel, AiUsageDaily, AiUsageRecent, AiUsageSource } from '@/types/ai'

type SourceFilter = 'ALL' | AiUsageSource

const DAYS_OPTIONS = ['7', '14', '30', '90'] as const

const numericColumn = 'text-right tabular-nums'
const numericHeader = 'text-right'

function formatCount(value: number): string {
  return value.toLocaleString()
}

/**
 * AI 用量页（Issue #140 UoW-2 前端）：后端一次返回 daily/byAgent/byModel/recent 四段聚合，
 * 本页只做筛选与呈现。tokens 未知的调用单独计数（unknownTokenCalls / 明细 null），不并入 0。
 */
export function AiUsagePage() {
  const { t } = useTranslation('aiAdmin')
  const [source, setSource] = useState<SourceFilter>('ALL')
  const [days, setDays] = useState<string>('14')

  const { data, isPending, isError, refetch } = useQuery({
    queryKey: ['ai', 'usage', source, days],
    queryFn: () =>
      aiApi.getAiUsage({
        source: source === 'ALL' ? undefined : source,
        days: Number(days),
        limit: 50,
      }),
  })

  const sourceLabel = (value: AiUsageSource): string => {
    if (value === 'PORTAL') return t('usage.sourcePortal')
    if (value === 'ADMIN_PREVIEW') return t('usage.sourceAdminPreview')
    return t('usage.sourceGatewayV1')
  }

  const totals = (data?.daily ?? []).reduce(
    (acc, day) => ({
      calls: acc.calls + day.calls,
      promptTokens: acc.promptTokens + day.promptTokens,
      completionTokens: acc.completionTokens + day.completionTokens,
      unknownTokenCalls: acc.unknownTokenCalls + day.unknownTokenCalls,
    }),
    { calls: 0, promptTokens: 0, completionTokens: 0, unknownTokenCalls: 0 },
  )

  const tableState = {
    isLoading: isPending,
    isError,
    errorMessage: t('usage.loadError'),
    onRetry: () => void refetch(),
    emptyMessage: t('usage.empty'),
  }

  const dailyColumns: DataTableColumn<AiUsageDaily>[] = [
    { id: 'date', header: t('usage.colDate'), cell: (row) => row.date },
    { id: 'calls', header: t('usage.colCalls'), headerClassName: numericHeader, className: numericColumn, cell: (row) => formatCount(row.calls) },
    { id: 'prompt', header: t('usage.colPromptTokens'), headerClassName: numericHeader, className: numericColumn, cell: (row) => formatCount(row.promptTokens) },
    { id: 'completion', header: t('usage.colCompletionTokens'), headerClassName: numericHeader, className: numericColumn, cell: (row) => formatCount(row.completionTokens) },
    {
      id: 'total',
      header: t('usage.colTotalTokens'),
      headerClassName: numericHeader,
      className: numericColumn,
      cell: (row) => formatCount(row.promptTokens + row.completionTokens),
    },
    {
      id: 'unknown',
      header: t('usage.colUnknownCalls'),
      headerClassName: numericHeader,
      className: numericColumn,
      cell: (row) => (row.unknownTokenCalls > 0 ? formatCount(row.unknownTokenCalls) : '—'),
    },
  ]

  const byAgentColumns: DataTableColumn<AiUsageByAgent>[] = [
    {
      id: 'agent',
      header: t('usage.colAgent'),
      cell: (row) => (row.agentId == null ? t('usage.unassigned') : t('usage.agentCell', { id: row.agentId })),
    },
    { id: 'calls', header: t('usage.colCalls'), headerClassName: numericHeader, className: numericColumn, cell: (row) => formatCount(row.calls) },
    { id: 'prompt', header: t('usage.colPromptTokens'), headerClassName: numericHeader, className: numericColumn, cell: (row) => formatCount(row.promptTokens) },
    { id: 'completion', header: t('usage.colCompletionTokens'), headerClassName: numericHeader, className: numericColumn, cell: (row) => formatCount(row.completionTokens) },
    {
      id: 'total',
      header: t('usage.colTotalTokens'),
      headerClassName: numericHeader,
      className: numericColumn,
      cell: (row) => formatCount(row.promptTokens + row.completionTokens),
    },
  ]

  const byModelColumns: DataTableColumn<AiUsageByModel>[] = [
    {
      id: 'entry',
      header: t('usage.colEntry'),
      cell: (row) => {
        const base = row.entryId == null ? t('usage.unassigned') : t('usage.entryCell', { id: row.entryId })
        return row.alias ? `${base} · ${row.alias}` : base
      },
    },
    { id: 'calls', header: t('usage.colCalls'), headerClassName: numericHeader, className: numericColumn, cell: (row) => formatCount(row.calls) },
    { id: 'prompt', header: t('usage.colPromptTokens'), headerClassName: numericHeader, className: numericColumn, cell: (row) => formatCount(row.promptTokens) },
    { id: 'completion', header: t('usage.colCompletionTokens'), headerClassName: numericHeader, className: numericColumn, cell: (row) => formatCount(row.completionTokens) },
    {
      id: 'total',
      header: t('usage.colTotalTokens'),
      headerClassName: numericHeader,
      className: numericColumn,
      cell: (row) => formatCount(row.promptTokens + row.completionTokens),
    },
  ]

  const tokensCell = (value: number | null) =>
    value == null ? <span className="text-admin-muted">{t('usage.tokensUnknown')}</span> : formatCount(value)

  const recentColumns: DataTableColumn<AiUsageRecent>[] = [
    { id: 'time', header: t('usage.colTime'), cell: (row) => new Date(row.createdAt).toLocaleString() },
    { id: 'source', header: t('usage.colSource'), cell: (row) => sourceLabel(row.source) },
    {
      id: 'agent',
      header: t('usage.colAgent'),
      cell: (row) => (row.agentId == null ? t('usage.unassigned') : t('usage.agentCell', { id: row.agentId })),
    },
    {
      id: 'entry',
      header: t('usage.colEntry'),
      cell: (row) => {
        const base = row.entryId == null ? t('usage.unassigned') : t('usage.entryCell', { id: row.entryId })
        return row.alias ? `${base} · ${row.alias}` : base
      },
    },
    {
      id: 'mode',
      header: t('usage.colMode'),
      cell: (row) => {
        if (row.mode === 'KEY') return t('settings.modeKey')
        if (row.mode === 'CLI') return t('settings.modeCli')
        return '—'
      },
    },
    { id: 'prompt', header: t('usage.colPromptTokens'), headerClassName: numericHeader, className: numericColumn, cell: (row) => tokensCell(row.promptTokens) },
    { id: 'completion', header: t('usage.colCompletionTokens'), headerClassName: numericHeader, className: numericColumn, cell: (row) => tokensCell(row.completionTokens) },
    {
      id: 'latency',
      header: t('usage.colLatency'),
      headerClassName: numericHeader,
      className: numericColumn,
      cell: (row) => (row.latencyMs == null ? '—' : `${formatCount(row.latencyMs)} ms`),
    },
    {
      id: 'status',
      header: t('usage.colStatus'),
      headerClassName: 'text-center',
      className: 'text-center',
      cell: (row) => (
        <StatusTag
          status={row.status}
          tone={row.status === 'SUCCESS' ? 'active' : 'danger'}
          label={row.status === 'SUCCESS' ? t('usage.statusSuccess') : t('usage.statusUpstreamError')}
        />
      ),
    },
  ]

  return (
    <section className="admin-content-data space-y-4">
      <AdminPageHeader title={t('usage.title')} description={t('usage.description')} />

      <div className="flex flex-wrap items-end gap-x-8 gap-y-3">
        <div className="space-y-1.5">
          <p className="text-xs font-medium uppercase tracking-wide text-admin-muted">{t('usage.filterSource')}</p>
          <FilterTabs
            tabs={[
              { key: 'ALL', label: t('usage.sourceAll') },
              { key: 'PORTAL', label: t('usage.sourcePortal') },
              { key: 'ADMIN_PREVIEW', label: t('usage.sourceAdminPreview') },
              { key: 'GATEWAY_V1', label: t('usage.sourceGatewayV1') },
            ]}
            activeKey={source}
            onChange={(key) => setSource(key as SourceFilter)}
          />
        </div>
        <div className="space-y-1.5">
          <p className="text-xs font-medium uppercase tracking-wide text-admin-muted">{t('usage.filterDays')}</p>
          <FilterTabs
            tabs={DAYS_OPTIONS.map((option) => ({ key: option, label: t('usage.daysValue', { days: option }) }))}
            activeKey={days}
            onChange={setDays}
          />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard label={t('usage.totalCalls')} value={isPending ? '—' : formatCount(totals.calls)} />
        <StatCard label={t('usage.totalPromptTokens')} value={isPending ? '—' : formatCount(totals.promptTokens)} />
        <StatCard label={t('usage.totalCompletionTokens')} value={isPending ? '—' : formatCount(totals.completionTokens)} />
        <StatCard label={t('usage.totalUnknownCalls')} value={isPending ? '—' : formatCount(totals.unknownTokenCalls)} />
      </div>

      <UsageSection icon={CalendarDays} title={t('usage.dailyTitle')} hint={t('usage.dailyHint')}>
        <DataTable columns={dailyColumns} data={data?.daily ?? []} rowKey={(row) => row.date} {...tableState} />
      </UsageSection>

      <UsageSection icon={Bot} title={t('usage.byAgentTitle')}>
        <DataTable columns={byAgentColumns} data={data?.byAgent ?? []} rowKey={(row) => row.agentId ?? 'none'} {...tableState} />
      </UsageSection>

      <UsageSection icon={Cpu} title={t('usage.byModelTitle')}>
        <DataTable
          columns={byModelColumns}
          data={data?.byModel ?? []}
          rowKey={(row) => row.entryId ?? 'none'}
          {...tableState}
        />
      </UsageSection>

      <UsageSection icon={History} title={t('usage.recentTitle')}>
        <DataTable columns={recentColumns} data={data?.recent ?? []} rowKey={(row) => row.id} {...tableState} />
      </UsageSection>
    </section>
  )
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="rounded-xl border border-admin bg-admin-surface p-5">
      <p className="text-xs text-admin-muted">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums text-admin">{value}</p>
    </article>
  )
}

function UsageSection({
  icon: Icon,
  title,
  hint,
  children,
}: {
  icon: ComponentType<{ className?: string }>
  title: string
  hint?: string
  children: React.ReactNode
}) {
  return (
    <section className="space-y-2 pt-2">
      <div className="flex items-center gap-2">
        <Icon className="h-4.5 w-4.5 text-admin-muted" />
        <h2 className="text-base font-semibold text-admin">{title}</h2>
        {hint ? <span className="text-xs text-admin-muted">{hint}</span> : null}
      </div>
      {children}
    </section>
  )
}
