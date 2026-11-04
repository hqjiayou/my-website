import { useQuery } from '@tanstack/react-query'
import { AlertCircle, Inbox } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import { adminGhostButtonClass } from '@/components/admin'
import { cn } from '@/lib/cn'

import { AiRunMetaSummary } from './agentAiRunMeta'

const PAGE_SIZE = 20

function formatListTime(value: string | null) {
  if (!value) return null
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(undefined, { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function formatMessageTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
}

export function AgentAiConversationsPanel({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const { t: tCommon } = useTranslation('common')
  const [pageNum, setPageNum] = useState(1)
  const [selectedId, setSelectedId] = useState<number | null>(null)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', agentId, 'ai', 'conversations', pageNum],
    queryFn: () => aiApi.listConversations(agentId, { pageNum, pageSize: PAGE_SIZE }),
  })

  const { data: messages, isPending: messagesPending } = useQuery({
    queryKey: ['sites', agentId, 'ai', 'conversations', selectedId, 'messages'],
    queryFn: () => aiApi.listConversationMessages(agentId, selectedId!),
    enabled: selectedId != null,
  })

  const records = data?.records ?? []
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className="grid h-full min-h-0 overflow-hidden rounded-xl border border-admin md:grid-cols-[330px_minmax(0,1fr)]">
      {/* 左列：会话列表（分页沿用现有 pageNum / total 状态） */}
      <div className="flex h-full min-h-0 flex-col border-b border-admin md:border-b-0 md:border-r">
        <div className="min-h-0 flex-1 overflow-y-auto">
          {isPending ? (
            <ul>
              {Array.from({ length: 4 }, (_, index) => (
                <li key={index} className="border-b border-admin px-3.5 py-3 last:border-b-0">
                  <div className="admin-skeleton h-3.5 w-3/4" />
                  <div className="admin-skeleton mt-2 h-3 w-1/2" />
                </li>
              ))}
            </ul>
          ) : isError ? (
            <div className="flex flex-col items-center gap-2 px-3.5 py-8 text-center">
              <AlertCircle className="h-5 w-5 text-admin-danger" />
              <p className="text-sm text-admin-danger">
                {error instanceof Error ? error.message : tCommon('table.error')}
              </p>
              <button type="button" onClick={() => void refetch()} className={adminGhostButtonClass}>
                {tCommon('table.retry')}
              </button>
            </div>
          ) : records.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-3.5 py-8 text-center">
              <Inbox className="h-5 w-5 text-admin-muted" />
              <p className="text-sm text-admin-muted">{t('aiWorkspace.conversations.empty')}</p>
            </div>
          ) : (
            <ul>
              {records.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => setSelectedId(item.id)}
                    className={cn(
                      'w-full border-b border-admin px-3.5 py-3 text-left transition last:border-b-0 hover:bg-admin-surface-muted',
                      selectedId === item.id && 'admin-active-inset bg-[rgb(var(--primary)/.1)]',
                    )}
                  >
                    <div className="flex items-center gap-2">
                      <p className="min-w-0 flex-1 truncate text-sm font-medium text-admin">
                        {item.title?.trim() || t('aiWorkspace.conversations.untitled')}
                      </p>
                      <span className="shrink-0 rounded-md bg-admin-surface-muted px-1.5 py-0.5 text-[10px] font-medium text-admin-muted">
                        {item.source === 'ADMIN_PREVIEW'
                          ? t('aiWorkspace.conversations.sourcePreview')
                          : t('aiWorkspace.conversations.sourcePortal')}
                      </span>
                    </div>
                    <p className="mt-1 text-xs tabular-nums text-admin-muted">{formatListTime(item.lastMessageAt) ?? '—'}</p>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
        {total > PAGE_SIZE ? (
          <div className="flex items-center justify-between gap-2 border-t border-admin px-3 py-2">
            <span className="text-xs text-admin-muted">{tCommon('table.paginationTotal', { total })}</span>
            <div className="flex items-center gap-1">
              <button
                type="button"
                disabled={pageNum <= 1}
                onClick={() => setPageNum((page) => page - 1)}
                className={cn(adminGhostButtonClass, 'disabled:opacity-40')}
              >
                {tCommon('table.prev')}
              </button>
              <span className="px-1 text-xs tabular-nums text-admin-muted">
                {pageNum} / {totalPages}
              </span>
              <button
                type="button"
                disabled={pageNum >= totalPages}
                onClick={() => setPageNum((page) => page + 1)}
                className={cn(adminGhostButtonClass, 'disabled:opacity-40')}
              >
                {tCommon('table.next')}
              </button>
            </div>
          </div>
        ) : null}
      </div>

      {/* 右列：消息流 */}
      <div className="flex h-full min-h-0 flex-col bg-card-muted/50">
        {selectedId == null ? (
          <p className="m-auto px-4 text-sm text-admin-muted">{t('aiWorkspace.conversations.selectHint')}</p>
        ) : messagesPending ? (
          <p className="m-auto px-4 text-sm text-admin-muted">{t('aiWorkspace.loading')}</p>
        ) : (
          <ul className="min-h-0 flex-1 space-y-3.5 overflow-y-auto p-4">
            {(messages ?? []).map((item) => {
              const isUser = item.role === 'USER'
              return (
                <li
                  key={item.id}
                  className={cn(
                    'flex max-w-[min(36rem,78%)] flex-col',
                    isUser ? 'ml-auto items-end' : 'mr-auto items-start',
                  )}
                >
                  <p
                    className={cn(
                      'mb-1 flex items-center gap-1.5 text-[11px] font-medium text-admin-muted',
                      isUser && 'justify-end',
                    )}
                  >
                    <span>{item.role}</span>
                    <span className="tabular-nums">{formatMessageTime(item.createdAt)}</span>
                  </p>
                  <div
                    className={cn(
                      'w-fit max-w-full rounded-2xl px-3.5 py-2.5 text-sm leading-relaxed',
                      isUser
                        ? 'rounded-br-md bg-admin-primary text-admin-on-primary'
                        : 'rounded-bl-md border border-admin bg-admin-surface',
                    )}
                  >
                    <p className={cn('whitespace-pre-wrap break-words', isUser && 'text-left')}>{item.content}</p>
                  </div>
                  {item.role === 'ASSISTANT' && item.agentMeta ? (
                    <>
                      {item.agentMeta.model ? (
                        <p className="mt-1 font-mono text-[11px] text-admin-muted">{item.agentMeta.model}</p>
                      ) : null}
                      <AiRunMetaSummary meta={item.agentMeta} />
                    </>
                  ) : null}
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </div>
  )
}
