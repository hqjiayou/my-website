import { useQuery } from '@tanstack/react-query'
import { AlertCircle, ArrowLeft, Inbox } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import { adminGhostButtonClass } from '@/components/admin'
import { cn } from '@/lib/cn'
import type { AiConversationListItem } from '@/types/ai'

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

function formatFullTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString(undefined, { year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

/** 预览会话（管理员在后台试聊）与用户端会话来源不同，用色区分，避免混读。 */
function SourceBadge({ source }: { source: AiConversationListItem['source'] }) {
  const { t } = useTranslation('sites')
  const isPreview = source === 'ADMIN_PREVIEW'
  return (
    <span
      className={cn(
        'shrink-0 rounded-md px-1.5 py-0.5 text-xs font-medium',
        isPreview ? 'bg-info-muted text-info-emphasis' : 'bg-admin-surface-muted text-admin-muted',
      )}
    >
      {isPreview ? t('aiWorkspace.conversations.sourcePreview') : t('aiWorkspace.conversations.sourcePortal')}
    </span>
  )
}

/**
 * Runs 面板：真实数据源是 Conversations，非完整 Trace。
 * 宽屏并排；内容窄时（@container 不足 48rem）切列表→详情单页并提供返回，不硬锁 330px 左栏。
 */
export function AgentAiConversationsPanel({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const { t: tCommon } = useTranslation('common')
  const [pageNum, setPageNum] = useState(1)
  const [selectedId, setSelectedId] = useState<number | null>(null)

  // 渲染期调整：切换 Agent 时清掉选中会话与页码，避免带着上一个 Agent 的会话 id 去查消息。
  const [scopeAgentId, setScopeAgentId] = useState(agentId)
  if (scopeAgentId !== agentId) {
    setScopeAgentId(agentId)
    setPageNum(1)
    setSelectedId(null)
  }

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', agentId, 'ai', 'conversations', pageNum],
    queryFn: () => aiApi.listConversations(agentId, { pageNum, pageSize: PAGE_SIZE }),
  })

  const {
    data: messages,
    isPending: messagesPending,
    isError: messagesError,
    error: messagesErrorValue,
    refetch: refetchMessages,
  } = useQuery({
    queryKey: ['sites', agentId, 'ai', 'conversations', selectedId, 'messages'],
    queryFn: () => aiApi.listConversationMessages(agentId, selectedId!),
    enabled: selectedId != null,
  })

  const records = data?.records ?? []
  const selected = records.find((item) => item.id === selectedId) ?? null
  const total = data?.total ?? 0
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))
  const showDetail = selectedId != null

  return (
    <div className="@container/runs flex h-full min-h-0 flex-col gap-3">
      <p className="shrink-0 text-xs leading-5 text-admin-muted">{t('aiWorkspace.conversations.sourceBanner')}</p>
      <div
        className={cn(
          'grid min-h-0 flex-1 overflow-hidden rounded-xl border border-admin',
          'grid-cols-1',
          '@[48rem]:grid-cols-[minmax(14rem,18rem)_minmax(0,1fr)]',
        )}
      >
        {/* 左列：会话列表；窄屏选中后隐藏 */}
        <div
          className={cn(
            'flex h-full min-h-0 flex-col border-admin',
            '@[48rem]:border-r',
            showDetail && 'hidden @[48rem]:flex',
          )}
        >
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
              <div className="flex flex-col items-center gap-2 px-3.5 py-8 text-center" role="alert">
                <AlertCircle className="h-5 w-5 text-admin-danger" aria-hidden />
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
                        'admin-focus-ring w-full border-b border-admin px-3.5 py-3 text-left transition last:border-b-0 hover:bg-admin-surface-muted',
                        selectedId === item.id && 'admin-active-inset bg-primary/10',
                      )}
                    >
                      <div className="flex items-center gap-2">
                        <p className="min-w-0 flex-1 truncate text-sm font-medium text-admin">
                          {item.title?.trim() || t('aiWorkspace.conversations.untitled')}
                        </p>
                        <SourceBadge source={item.source} />
                      </div>
                      <p className="mt-1 flex items-center gap-2 text-xs tabular-nums text-admin-muted">
                        <span>{formatListTime(item.lastMessageAt ?? item.createdAt) ?? '—'}</span>
                        <span className="font-mono">#{item.id}</span>
                      </p>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
          {total > PAGE_SIZE ? (
            <div className="flex shrink-0 items-center justify-between gap-2 border-t border-admin px-3 py-2">
              <span className="text-xs text-admin-muted">{tCommon('table.paginationTotal', { total })}</span>
              <div className="flex items-center gap-1">
                <button
                  type="button"
                  disabled={pageNum <= 1}
                  onClick={() => {
                    setPageNum((page) => page - 1)
                    setSelectedId(null)
                  }}
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
                  onClick={() => {
                    setPageNum((page) => page + 1)
                    setSelectedId(null)
                  }}
                  className={cn(adminGhostButtonClass, 'disabled:opacity-40')}
                >
                  {tCommon('table.next')}
                </button>
              </div>
            </div>
          ) : null}
        </div>

        {/* 右列：消息流；窄屏未选中时隐藏 */}
        <div
          className={cn(
            'flex h-full min-h-0 flex-col bg-card-muted/50',
            !showDetail && 'hidden @[48rem]:flex',
          )}
        >
          {!showDetail ? (
            <p className="m-auto px-4 text-sm text-admin-muted">{t('aiWorkspace.conversations.selectHint')}</p>
          ) : (
            <>
              <div className="shrink-0 space-y-2 border-b border-admin px-4 py-3">
                <button
                  type="button"
                  className={cn(adminGhostButtonClass, 'gap-1.5 @[48rem]:hidden')}
                  onClick={() => setSelectedId(null)}
                >
                  <ArrowLeft className="h-3.5 w-3.5" aria-hidden />
                  {t('aiWorkspace.conversations.backToList')}
                </button>
                {selected ? (
                  <div className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-admin-muted">
                    <SourceBadge source={selected.source} />
                    <span className="font-mono">#{selected.id}</span>
                    <span>
                      {selected.agentUserId != null
                        ? t('aiWorkspace.conversations.userLabel', { id: selected.agentUserId })
                        : t('aiWorkspace.conversations.previewNoUser')}
                    </span>
                    <span className="tabular-nums">
                      {t('aiWorkspace.conversations.createdAt', { time: formatFullTime(selected.createdAt) })}
                    </span>
                    {selected.lastMessageAt ? (
                      <span className="tabular-nums">
                        {t('aiWorkspace.conversations.lastMessageAt', {
                          time: formatFullTime(selected.lastMessageAt),
                        })}
                      </span>
                    ) : null}
                  </div>
                ) : null}
                <p className="text-xs leading-5 text-admin-muted">{t('aiWorkspace.conversations.noTraceHint')}</p>
              </div>
              {messagesPending ? (
                <p className="m-auto px-4 text-sm text-admin-muted">{t('aiWorkspace.loading')}</p>
              ) : messagesError ? (
                <div className="m-auto flex flex-col items-center gap-2 px-4 py-8 text-center" role="alert">
                  <AlertCircle className="h-5 w-5 text-admin-danger" aria-hidden />
                  <p className="text-sm text-admin-danger">{t('aiWorkspace.conversations.messagesError')}</p>
                  {messagesErrorValue instanceof Error ? (
                    <p className="max-w-sm break-words text-xs text-admin-muted">{messagesErrorValue.message}</p>
                  ) : null}
                  <button type="button" onClick={() => void refetchMessages()} className={adminGhostButtonClass}>
                    {tCommon('table.retry')}
                  </button>
                </div>
              ) : (messages ?? []).length === 0 ? (
                <div className="m-auto flex flex-col items-center gap-2 px-4 py-8 text-center">
                  <Inbox className="h-5 w-5 text-admin-muted" />
                  <p className="text-sm text-admin-muted">{t('aiWorkspace.conversations.messagesEmpty')}</p>
                </div>
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
                            'mb-1 flex items-center gap-1.5 text-xs font-medium text-admin-muted',
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
                        {item.role === 'ASSISTANT' ? (
                          item.agentMeta ? (
                            <AiRunMetaSummary meta={item.agentMeta} />
                          ) : (
                            <p className="mt-1 text-xs leading-4 text-admin-muted">
                              {t('aiWorkspace.conversations.noRunMeta')}
                            </p>
                          )
                        ) : null}
                      </li>
                    )
                  })}
                </ul>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  )
}
