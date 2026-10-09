import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import { DataTable, type DataTableColumn } from '@/components/admin'
import type { AiConversationListItem } from '@/types/ai'

import { AiRunMetaSummary } from './siteAiRunMeta'

export function SiteAiConversationsPanel({ siteId }: { siteId: number }) {
  const { t } = useTranslation('sites')
  const [pageNum, setPageNum] = useState(1)
  const [selectedId, setSelectedId] = useState<number | null>(null)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', siteId, 'ai', 'conversations', pageNum],
    queryFn: () => aiApi.listConversations(siteId, { pageNum, pageSize: 20 }),
  })

  const { data: messages, isPending: messagesPending } = useQuery({
    queryKey: ['sites', siteId, 'ai', 'conversations', selectedId, 'messages'],
    queryFn: () => aiApi.listConversationMessages(siteId, selectedId!),
    enabled: selectedId != null,
  })

  const columns = useMemo<DataTableColumn<AiConversationListItem>[]>(
    () => [
      {
        id: 'title',
        header: t('aiWorkspace.conversations.columns.title'),
        cell: (item) => item.title || t('aiWorkspace.conversations.untitled'),
      },
      {
        id: 'siteUserId',
        header: t('aiWorkspace.conversations.columns.siteUserId'),
        cell: (item) => String(item.siteUserId),
      },
      {
        id: 'lastMessageAt',
        header: t('aiWorkspace.conversations.columns.lastMessageAt'),
        cell: (item) => item.lastMessageAt ?? '—',
      },
      {
        id: 'actions',
        header: t('aiWorkspace.conversations.columns.actions'),
        cell: (item) => (
          <button type="button" className="text-sm underline" onClick={() => setSelectedId(item.id)}>
            {t('aiWorkspace.conversations.view')}
          </button>
        ),
      },
    ],
    [t],
  )

  const records = data?.records ?? []

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <div>
        <DataTable
          columns={columns}
          data={records}
          rowKey={(item) => item.id}
          isLoading={isPending}
          isError={isError}
          errorMessage={error instanceof Error ? error.message : undefined}
          emptyMessage={t('aiWorkspace.conversations.empty')}
          onRetry={() => void refetch()}
          pagination={{
            page: pageNum,
            pageSize: 20,
            total: data?.total ?? 0,
            onPageChange: setPageNum,
          }}
        />
      </div>
      <div className="rounded-xl border border-slate-200 bg-white p-4">
        {selectedId == null ? (
          <p className="text-sm text-slate-500">{t('aiWorkspace.conversations.selectHint')}</p>
        ) : messagesPending ? (
          <p className="text-sm text-slate-500">{t('aiWorkspace.loading')}</p>
        ) : (
          <ul className="space-y-3">
            {(messages ?? []).map((item) => (
              <li key={item.id} className="rounded-lg bg-slate-50 px-3 py-2 text-sm">
                <p className="text-xs font-medium text-slate-500">{item.role}</p>
                <p className="mt-1 whitespace-pre-wrap text-slate-800">{item.content}</p>
                {item.agentMeta ? <AiRunMetaSummary meta={item.agentMeta} /> : null}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
