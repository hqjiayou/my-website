import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react'

import { siteAiApi, sseErrorText } from '@/api/siteAiApi'
import { BottomNav } from '@/components/bottomNav'
import { resolvePortalSiteId } from '@/config/env'
import { useAuthStore } from '@/stores/authStore'
import type { SiteAiCitation, SiteAiConversation } from '@/types/api'
import { formatSimilarityPercent } from '@/utils/formatSimilarity'
import { errorMessage } from '@/utils/http/apiError'

import { ConversationSheet } from './conversationSheet'
import { useKeyboardInset } from './useKeyboardInset'

const MAX_MESSAGE_CHARS = 16_000

interface ChatTurn {
  id: string
  role: string
  content: string
  pending?: boolean
  providerStatus?: string
  citations?: SiteAiCitation[]
}

interface SendFailure {
  message: string
  retryText: string
}

export function ChatPage() {
  const site = useMemo(() => resolvePortalSiteId(useAuthStore.getState().site?.siteId), [])
  useKeyboardInset()

  const [conversations, setConversations] = useState<SiteAiConversation[]>([])
  const [listLoading, setListLoading] = useState(site.ok)
  const [listError, setListError] = useState<string | null>(null)
  const [sheetOpen, setSheetOpen] = useState(false)
  const [activeId, setActiveId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatTurn[]>([])
  const [historyLoading, setHistoryLoading] = useState(false)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [failure, setFailure] = useState<SendFailure | null>(null)

  const bottomRef = useRef<HTMLDivElement | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const generation = useRef(0)

  const loadConversations = useCallback(async (siteId: string, isCurrent: () => boolean = () => true) => {
    setListLoading(true)
    setListError(null)
    try {
      const items = await siteAiApi.listConversations(siteId)
      if (!isCurrent()) return
      setConversations(items)
    } catch (error) {
      if (!isCurrent()) return
      setListError(errorMessage(error))
    } finally {
      if (isCurrent()) setListLoading(false)
    }
  }, [])

  useEffect(() => {
    if (!site.ok) return
    let current = true
    siteAiApi.listConversations(site.siteId).then(
      (items) => {
        if (!current) return
        setConversations(items)
        setListLoading(false)
      },
      (error: unknown) => {
        if (!current) return
        setListError(errorMessage(error))
        setListLoading(false)
      },
    )
    return () => {
      current = false
    }
  }, [site])

  useEffect(() => {
    const activeGeneration = generation
    const activeAbort = abortRef
    return () => {
      activeGeneration.current += 1
      activeAbort.current?.abort()
    }
  }, [])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages, sending, failure])

  function startNewChat() {
    generation.current += 1
    abortRef.current?.abort()
    abortRef.current = null
    setActiveId(null)
    setMessages([])
    setHistoryError(null)
    setHistoryLoading(false)
    setFailure(null)
    setSending(false)
    setSheetOpen(false)
  }

  async function openConversation(conversationId: string) {
    if (!site.ok) return
    const gen = generation.current + 1
    generation.current = gen
    abortRef.current?.abort()
    abortRef.current = null
    setSending(false)
    setActiveId(conversationId)
    setSheetOpen(false)
    setFailure(null)
    setHistoryError(null)
    setHistoryLoading(true)
    setMessages([])
    try {
      const items = await siteAiApi.listMessages(site.siteId, conversationId)
      if (generation.current !== gen) return
      setMessages(
        items.map((item) => ({
          id: String(item.id),
          role: item.role,
          content: item.content,
        })),
      )
    } catch (error) {
      if (generation.current !== gen) return
      setHistoryError(errorMessage(error))
    } finally {
      if (generation.current === gen) setHistoryLoading(false)
    }
  }

  async function send(text: string) {
    if (!site.ok || sending) return
    const trimmed = text.trim()
    if (!trimmed) return
    if (trimmed.length > MAX_MESSAGE_CHARS) {
      setFailure({ message: `訊息超過 ${MAX_MESSAGE_CHARS} 字`, retryText: trimmed })
      return
    }

    const gen = generation.current
    const conversationId = activeId
    const assistantId = `local-assistant-${crypto.randomUUID()}`
    const controller = new AbortController()
    abortRef.current = controller
    setDraft('')
    setFailure(null)
    setSending(true)
    setMessages((current) => [
      ...current,
      { id: `local-user-${crypto.randomUUID()}`, role: 'USER', content: trimmed },
      { id: assistantId, role: 'ASSISTANT', content: '', pending: true },
    ])

    let finished = false
    try {
      await siteAiApi.streamChat(
        site.siteId,
        conversationId ? { message: trimmed, conversationId } : { message: trimmed },
        (event) => {
          if (generation.current !== gen) return
          if (event.event === 'TEXT_DELTA') {
            setMessages((current) =>
              current.map((item) =>
                item.id === assistantId ? { ...item, content: item.content + event.delta, pending: true } : item,
              ),
            )
          } else if (event.event === 'DONE') {
            finished = true
            setMessages((current) =>
              current.map((item) =>
                item.id === assistantId
                  ? {
                      ...item,
                      content: event.reply || item.content,
                      pending: false,
                      providerStatus: event.providerStatus,
                      citations: event.citations,
                    }
                  : item,
              ),
            )
          } else if (event.event === 'CONVERSATION') {
            if (event.conversationId) setActiveId(event.conversationId)
          } else if (event.event === 'ERROR') {
            finished = true
            setFailure({ message: sseErrorText(event.errorCode), retryText: trimmed })
            setMessages((current) =>
              current.map((item) => (item.id === assistantId ? { ...item, pending: false } : item)),
            )
          }
        },
        controller.signal,
      )
      if (generation.current !== gen) return
      if (!finished && !controller.signal.aborted) {
        setFailure({ message: '連線已結束，但沒有收到完成事件', retryText: trimmed })
        setMessages((current) => current.map((item) => (item.id === assistantId ? { ...item, pending: false } : item)))
      }
    } catch (error) {
      if (generation.current !== gen || controller.signal.aborted) return
      setFailure({ message: errorMessage(error), retryText: trimmed })
      setMessages((current) => current.map((item) => (item.id === assistantId ? { ...item, pending: false } : item)))
    } finally {
      if (generation.current === gen) {
        setSending(false)
        void loadConversations(site.siteId)
      }
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void send(draft)
  }

  const activeTitle =
    conversations.find((item) => String(item.id) === activeId)?.title?.trim() || (activeId ? '對話' : '新對話')

  if (!site.ok) {
    return (
      <div className="safe-top safe-x with-bottom-nav mx-auto flex min-h-dvh w-full max-w-lg flex-col bg-background px-page py-6 text-foreground">
        <p className="mt-6 text-sm leading-6 text-danger">{site.message}</p>
        <BottomNav />
      </div>
    )
  }

  return (
    <div
      className="mx-auto flex w-full max-w-lg flex-col bg-background text-foreground"
      style={{ height: 'calc(100dvh - var(--keyboard-inset, 0px))' }}
    >
      <header className="safe-top safe-x flex shrink-0 items-center gap-2 border-b border-border px-3 py-2">
        <h1 className="min-w-0 flex-1 truncate px-2 text-base font-semibold">{activeTitle}</h1>
        <button type="button" onClick={() => setSheetOpen(true)} className="h-11 shrink-0 px-2 text-sm font-medium text-primary">
          歷史
        </button>
      </header>

      <div className="safe-x min-h-0 flex-1 overflow-y-auto px-4 py-4" aria-live="polite">
        {historyLoading ? <p className="text-sm text-muted-foreground">正在載入訊息…</p> : null}
        {messages.length === 0 && !historyError && !historyLoading ? (
          <p className="text-sm leading-6 text-muted-foreground">輸入訊息開始對話。新對話會在第一次成功回覆後出現在歷史裡。</p>
        ) : null}
        {historyError ? (
          <div>
            <p className="text-sm text-danger">{historyError}</p>
            {activeId ? (
              <button
                type="button"
                onClick={() => void openConversation(activeId)}
                className="mt-3 h-10 rounded-control border border-border bg-surface px-4 text-sm"
              >
                重試
              </button>
            ) : null}
          </div>
        ) : null}
        <ul className="flex flex-col gap-3">
          {messages.map((message) => {
            const hasCitations = !message.pending && message.role === 'ASSISTANT' && (message.citations?.length ?? 0) > 0
            const visible =
              Boolean(message.content) || message.pending || message.providerStatus !== undefined || hasCitations
            if (!visible) return null
            return (
            <li
              key={message.id}
              className={
                message.role === 'USER'
                  ? 'ml-10 rounded-panel bg-primary px-3 py-2 text-sm leading-6 text-primary-foreground'
                  : 'mr-6 rounded-panel border border-border bg-surface px-3 py-2 text-sm leading-6'
              }
            >
              <p className="mb-1 text-xs opacity-70">{roleLabel(message.role)}</p>
              <p className="whitespace-pre-wrap break-words">
                {message.content || (message.pending ? '正在產生…' : '')}
              </p>
              {message.pending && message.content ? <p className="mt-1 text-xs text-muted-foreground">正在產生…</p> : null}
              {hasCitations ? (
                <div className="mt-2">
                  <p className="text-xs text-muted-foreground">參考來源</p>
                  <ul className="mt-1 flex flex-wrap gap-1">
                    {message.citations?.map((citation, index) => (
                      <li
                        key={`${citation.sourceId}-${citation.chunkIndex}-${index}`}
                        className="max-w-full break-all rounded-pill bg-primary-soft px-2 py-0.5 text-xs text-primary"
                      >
                        {citation.sourceId} {formatSimilarityPercent(citation.score)}
                      </li>
                    ))}
                  </ul>
                </div>
              ) : null}
              {message.providerStatus !== undefined && message.providerStatus !== 'LIVE' ? (
                <p className="mt-2 text-xs text-danger">
                  {message.providerStatus ? `AI 服務狀態：${message.providerStatus}` : '尚未回傳 AI 服務狀態'}
                </p>
              ) : null}
            </li>
            )
          })}
        </ul>
        {failure ? (
          <div className="mt-3 rounded-control bg-danger-soft px-3 py-2" role="alert">
            <p className="text-sm text-danger">{failure.message}</p>
            <button
              type="button"
              onClick={() => void send(failure.retryText)}
              disabled={sending}
              className="mt-2 h-10 rounded-control border border-danger-line px-4 text-sm text-danger disabled:opacity-60"
            >
              重試
            </button>
          </div>
        ) : null}
        <div ref={bottomRef} />
      </div>

      <form onSubmit={onSubmit} className="safe-x shrink-0 border-t border-border bg-surface px-3 py-3">
        <div className="flex items-end gap-2">
          <label className="sr-only" htmlFor="chat-draft">
            訊息
          </label>
          <textarea
            id="chat-draft"
            rows={1}
            value={draft}
            enterKeyHint="send"
            placeholder={sending ? '正在等待回覆…' : '寫一則訊息'}
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault()
                void send(draft)
              }
            }}
            className="max-h-32 min-h-12 flex-1 resize-none rounded-control border border-border bg-background px-3 py-3 text-base outline-none focus:border-primary"
          />
          <button
            type="submit"
            disabled={sending || !draft.trim()}
            className="h-12 shrink-0 rounded-control bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60"
          >
            {sending ? '傳送中' : '傳送'}
          </button>
        </div>
      </form>
      <BottomNav pinned={false} />

      <ConversationSheet
        open={sheetOpen}
        items={conversations}
        loading={listLoading}
        error={listError}
        activeId={activeId}
        onClose={() => setSheetOpen(false)}
        onRefresh={() => void loadConversations(site.siteId)}
        onCreate={startNewChat}
        onOpen={(id) => void openConversation(id)}
      />
    </div>
  )
}

function roleLabel(role: string): string {
  if (role === 'USER') return '我'
  if (role === 'ASSISTANT') return '助理'
  return role
}
