import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { siteAiApi, sseErrorText } from '@/api/siteAiApi'
import { resolvePortalSiteId } from '@/config/env'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import type { SiteAiCitation, SiteAiConversation, SiteAiMessage } from '@/types/api'
import { formatSimilarityPercent } from '@/utils/formatSimilarity'
import { errorMessage } from '@/utils/http/apiError'

import { ConversationSheet } from './conversationSheet'
import { useKeyboardInset } from './useKeyboardInset'

const MAX_MESSAGE_CHARS = 16_000

const SUGGESTIONS = ['這個站點提供哪些服務？', '幫我整理預約需要的資訊', '知識庫裡有什麼相關內容？']

interface ChatTurn {
  id: string
  role: string
  content: string
  pending?: boolean
  providerStatus?: string
  citations?: SiteAiCitation[]
}

interface StoredLoadOptions {
  silent?: boolean
  citations?: SiteAiCitation[]
  providerStatus?: string
}

function conversationPath(id: string) {
  return `${ROUTES.chat}/${id}`
}

/** 路徑上的會話 ID 必須是伺服器的正整數主鍵。 */
function readRouteConversationId(value: string | undefined): string | null {
  if (!value || !/^[1-9]\d*$/.test(value)) return null
  return value
}

function toStoredTurns(items: SiteAiMessage[], extra?: Pick<StoredLoadOptions, 'citations' | 'providerStatus'>): ChatTurn[] {
  let lastAssistant = -1
  const turns: ChatTurn[] = items.map((item, index) => {
    if (item.role === 'ASSISTANT') lastAssistant = index
    return { id: String(item.id), role: item.role, content: item.content }
  })
  if (lastAssistant >= 0 && extra && (extra.citations || extra.providerStatus !== undefined)) {
    turns[lastAssistant] = {
      ...turns[lastAssistant],
      citations: extra.citations,
      providerStatus: extra.providerStatus,
    }
  }
  return turns
}

interface SendFailure {
  message: string
  retryText: string
  userId: string
  assistantId: string
}

export function ChatPage() {
  const navigate = useNavigate()
  const routeConversationId = useParams().conversationId
  const routeId = readRouteConversationId(routeConversationId)
  const site = useMemo(() => resolvePortalSiteId(useAuthStore.getState().site?.siteId), [])
  const displayName = useAuthStore((state) => state.user?.displayName || state.user?.username || '')
  const logout = useAuthStore((state) => state.logout)
  useKeyboardInset()

  const [conversations, setConversations] = useState<SiteAiConversation[]>([])
  const [listLoading, setListLoading] = useState(site.ok)
  const [listError, setListError] = useState<string | null>(null)
  const [navOpen, setNavOpen] = useState(false)
  const [activeId, setActiveId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatTurn[]>([])
  const [historyLoading, setHistoryLoading] = useState(routeId != null)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [sending, setSending] = useState(false)
  const [failure, setFailure] = useState<SendFailure | null>(null)
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const [copyErrorId, setCopyErrorId] = useState<string | null>(null)
  const [loggingOut, setLoggingOut] = useState(false)
  const [logoutError, setLogoutError] = useState<string | null>(null)

  const bottomRef = useRef<HTMLDivElement | null>(null)
  const draftRef = useRef<HTMLTextAreaElement | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const generation = useRef(0)
  const sendingRef = useRef(false)
  const conversationRef = useRef<string | null>(null)
  const loadToken = useRef(0)

  useLayoutEffect(() => {
    if (sendingRef.current) return
    conversationRef.current = routeId
    if (!routeId) loadToken.current += 1
  }, [routeId])

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

  const loadStoredMessages = useCallback(
    (id: string, options?: StoredLoadOptions) => {
      if (!site.ok) return Promise.resolve()
      const token = ++loadToken.current
      conversationRef.current = id
      setActiveId(id)
      if (!options?.silent) {
        setMessages([])
        setHistoryLoading(true)
        setHistoryError(null)
      }
      return siteAiApi
        .listMessages(site.siteId, id)
        .then((items) => {
          if (loadToken.current !== token) return
          if (items.length === 0 && options?.silent) return
          setMessages(toStoredTurns(items, options))
          setHistoryError(null)
        })
        .catch((error: unknown) => {
          if (loadToken.current !== token || options?.silent) return
          setHistoryError(errorMessage(error))
          setMessages([])
        })
        .finally(() => {
          if (loadToken.current !== token || options?.silent) return
          setHistoryLoading(false)
        })
    },
    [site],
  )

  useEffect(() => {
    if (!routeConversationId || routeId) return
    navigate(ROUTES.chat, { replace: true })
  }, [navigate, routeConversationId, routeId])

  useEffect(() => {
    if (!site.ok || !routeId || sendingRef.current) return
    void loadStoredMessages(routeId)
  }, [loadStoredMessages, routeId, site])

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

  useLayoutEffect(() => {
    const node = draftRef.current
    if (!node) return
    node.style.height = '0px'
    node.style.height = `${Math.min(node.scrollHeight, 160)}px`
  }, [draft])

  async function copyReply(id: string, content: string) {
    try {
      await navigator.clipboard.writeText(content)
      setCopyErrorId(null)
      setCopiedId(id)
      window.setTimeout(() => {
        setCopiedId((current) => (current === id ? null : current))
      }, 1500)
    } catch {
      setCopiedId(null)
      setCopyErrorId(id)
    }
  }

  async function onLogout() {
    setLoggingOut(true)
    setLogoutError(null)
    try {
      await logout()
      navigate(ROUTES.login, { replace: true })
    } catch (error) {
      setLogoutError(errorMessage(error))
      setLoggingOut(false)
    }
  }

  function stopGenerating() {
    abortRef.current?.abort()
  }

  function leaveStreaming() {
    generation.current += 1
    sendingRef.current = false
    abortRef.current?.abort()
    abortRef.current = null
    setSending(false)
  }

  function startNewChat() {
    leaveStreaming()
    loadToken.current += 1
    conversationRef.current = null
    setActiveId(null)
    setMessages([])
    setHistoryError(null)
    setHistoryLoading(false)
    setFailure(null)
    setNavOpen(false)
    if (routeId) navigate(ROUTES.chat)
  }

  function openConversation(conversationId: string) {
    if (!site.ok) return
    setNavOpen(false)
    if (conversationId === routeId) return
    leaveStreaming()
    setFailure(null)
    setHistoryLoading(true)
    navigate(conversationPath(conversationId))
  }

  async function send(text: string, replace?: { userId: string; assistantId: string }) {
    if (!site.ok || sendingRef.current || historyLoading) return
    const trimmed = text.trim()
    if (!trimmed) return
    if (trimmed.length > MAX_MESSAGE_CHARS) {
      setFailure({
        message: `訊息超過 ${MAX_MESSAGE_CHARS} 字`,
        retryText: trimmed,
        userId: replace?.userId ?? '',
        assistantId: replace?.assistantId ?? '',
      })
      return
    }

    const gen = generation.current
    const conversationId = conversationRef.current
    const userId = `local-user-${crypto.randomUUID()}`
    const assistantId = `local-assistant-${crypto.randomUUID()}`
    const controller = new AbortController()
    abortRef.current = controller
    sendingRef.current = true
    setDraft('')
    setFailure(null)
    setSending(true)
    if (!conversationId) setActiveId(null)
    setMessages((current) => {
      const sameThread = current
      const base = conversationId
        ? replace
          ? sameThread.filter((item) => item.id !== replace.userId && item.id !== replace.assistantId)
          : sameThread
        : []
      return [
        ...base,
        { id: userId, role: 'USER', content: trimmed },
        { id: assistantId, role: 'ASSISTANT', content: '', pending: true },
      ]
    })

    const markStopped = () => {
      setMessages((current) =>
        current.flatMap((item) => {
          if (item.id !== assistantId) return [item]
          if (!item.content) return []
          return [{ ...item, pending: false }]
        }),
      )
    }

    const markFailed = (message: string) => {
      setFailure({ message, retryText: trimmed, userId, assistantId })
      setMessages((current) => current.map((item) => (item.id === assistantId ? { ...item, pending: false } : item)))
    }

    let finished = false
    let liveCitations: SiteAiCitation[] | undefined
    let liveProviderStatus: string | undefined
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
            liveCitations = event.citations
            liveProviderStatus = event.providerStatus
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
            if (!event.conversationId) return
            conversationRef.current = event.conversationId
            setActiveId(event.conversationId)
            navigate(conversationPath(event.conversationId), { replace: true })
          } else if (event.event === 'ERROR') {
            finished = true
            markFailed(sseErrorText(event.errorCode))
          }
        },
        controller.signal,
      )
      if (generation.current !== gen) return
      if (controller.signal.aborted) {
        markStopped()
        return
      }
      if (!finished) {
        markFailed('連線已結束，但沒有收到完成事件')
      } else if (conversationRef.current) {
        await loadStoredMessages(conversationRef.current, {
          silent: true,
          citations: liveCitations,
          providerStatus: liveProviderStatus,
        })
      }
    } catch (error) {
      if (generation.current !== gen) return
      if (controller.signal.aborted) {
        markStopped()
        return
      }
      markFailed(errorMessage(error))
    } finally {
      if (generation.current === gen) {
        sendingRef.current = false
        setSending(false)
        void loadConversations(site.siteId)
      }
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (sending) {
      stopGenerating()
      return
    }
    void send(draft)
  }

  async function renameConversation(id: string, title: string) {
    if (!site.ok) return
    const updated = await siteAiApi.renameConversation(site.siteId, id, title)
    setConversations((current) =>
      current.map((item) => (String(item.id) === id ? { ...item, title: updated.title } : item)),
    )
  }

  async function removeConversation(id: string) {
    if (!site.ok) return
    if (sending && id === selectedId) {
      throw new Error('正在產生回覆，請稍後再刪除')
    }
    await siteAiApi.deleteConversation(site.siteId, id)
    setConversations((current) => current.filter((item) => String(item.id) !== id))
    if (id === selectedId) startNewChat()
  }

  const freshChat = !routeId && !sending
  const visibleMessages = freshChat ? [] : messages
  const visibleHistoryError = freshChat ? null : historyError
  const selectedId = freshChat ? null : (routeId ?? activeId)
  const activeTitle =
    conversations.find((item) => String(item.id) === selectedId)?.title?.trim() || (selectedId ? '對話' : '新對話')

  if (!site.ok) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-gpt-canvas px-6 text-gpt-ink">
        <p className="max-w-sm text-sm leading-6 text-danger">{site.message}</p>
      </div>
    )
  }

  const showEmpty = visibleMessages.length === 0 && !visibleHistoryError && !(historyLoading && !freshChat)

  return (
    <div
      className="flex w-full bg-gpt-canvas text-gpt-ink"
      style={{ height: 'calc(100dvh - var(--keyboard-inset, 0px))' }}
    >
      {navOpen ? (
        <button
          type="button"
          aria-label="關閉選單"
          className="fixed inset-0 z-20 bg-gpt-ink/30 md:hidden"
          onClick={() => setNavOpen(false)}
        />
      ) : null}

      <aside
        className={
          navOpen
            ? 'fixed inset-y-0 left-0 z-30 flex h-full w-[260px] shrink-0 border-r border-gpt-line md:static'
            : 'hidden h-full w-[260px] shrink-0 border-r border-gpt-line md:flex'
        }
      >
        <div className="flex h-full min-h-0 w-full flex-col">
          <div className="min-h-0 flex-1">
            <ConversationSheet
              open
              items={conversations}
              loading={listLoading}
              error={listError}
              activeId={selectedId}
              deleteLockedId={sending ? selectedId : null}
              onClose={() => setNavOpen(false)}
              onRefresh={() => void loadConversations(site.siteId)}
              onCreate={startNewChat}
              onOpen={(id) => void openConversation(id)}
              onRename={renameConversation}
              onDelete={removeConversation}
            />
          </div>
          <footer className="safe-bottom border-t border-gpt-line px-3 py-3">
            <p className="truncate px-2 text-sm">{displayName || '已登入'}</p>
            {logoutError ? <p className="px-2 pt-1 text-xs text-danger">{logoutError}</p> : null}
            <div className="mt-2 flex gap-1">
              <button
                type="button"
                onClick={() => void onLogout()}
                disabled={loggingOut}
                className="rounded-lg px-2 py-1.5 text-xs text-gpt-muted hover:bg-gpt-hover disabled:opacity-60"
              >
                {loggingOut ? '正在登出…' : '登出'}
              </button>
              <Link to={ROUTES.home} className="rounded-lg px-2 py-1.5 text-xs text-gpt-muted hover:bg-gpt-hover">
                首頁
              </Link>
              <Link to={ROUTES.knowledge} className="rounded-lg px-2 py-1.5 text-xs text-gpt-muted hover:bg-gpt-hover">
                知識搜尋
              </Link>
            </div>
          </footer>
        </div>
      </aside>

      <section className="flex min-w-0 flex-1 flex-col">
        <header className="safe-top flex h-12 shrink-0 items-center gap-2 border-b border-gpt-line px-2">
          <button
            type="button"
            onClick={() => setNavOpen(true)}
            className="h-10 rounded-lg px-3 text-sm hover:bg-gpt-hover md:hidden"
          >
            選單
          </button>
          <h1 className="min-w-0 flex-1 truncate px-2 text-sm font-medium">{activeTitle}</h1>
          {selectedId ? <span className="pr-3 text-xs text-gpt-muted">已儲存</span> : null}
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto" aria-live="polite">
          <div className="mx-auto flex min-h-full w-full max-w-3xl flex-col px-4 py-6">
            {historyLoading && !freshChat ? <p className="text-sm text-gpt-muted">正在載入訊息…</p> : null}
            {showEmpty ? (
              <div className="flex flex-1 flex-col items-center justify-center py-16 text-center">
                <h1 className="text-2xl font-semibold tracking-tight">
                  {displayName ? `${displayName}，有什麼可以幫忙的？` : '有什麼可以幫忙的？'}
                </h1>
                <p className="mt-3 max-w-md text-sm leading-6 text-gpt-muted">
                  回覆成功後，這段對話會存在這個帳號下。從左側打開，或刷新這個頁面，都會從伺服器讀回。
                </p>
                <div className="mt-8 grid w-full gap-2 sm:grid-cols-3">
                  {SUGGESTIONS.map((prompt) => (
                    <button
                      key={prompt}
                      type="button"
                      disabled={sending}
                      onClick={() => void send(prompt)}
                      className="rounded-2xl border border-gpt-line px-3 py-3 text-left text-sm leading-6 hover:bg-gpt-hover disabled:opacity-60"
                    >
                      {prompt}
                    </button>
                  ))}
                </div>
              </div>
            ) : null}
            {visibleHistoryError ? (
              <div>
                <p className="text-sm text-danger">{visibleHistoryError}</p>
                {routeId ? (
                  <button
                    type="button"
                    onClick={() => {
                      if (routeId) void loadStoredMessages(routeId)
                    }}
                    className="mt-3 h-9 rounded-lg border border-gpt-line px-3 text-sm"
                  >
                    重試
                  </button>
                ) : null}
              </div>
            ) : null}
            <ul className="flex flex-col gap-6">
              {visibleMessages.map((message) => {
                const hasCitations =
                  !message.pending && message.role === 'ASSISTANT' && (message.citations?.length ?? 0) > 0
                const visible =
                  Boolean(message.content) || message.pending || message.providerStatus !== undefined || hasCitations
                if (!visible) return null
                const isUser = message.role === 'USER'
                return (
                  <li key={message.id} className={isUser ? 'flex justify-end' : 'flex justify-start'}>
                    <div className={isUser ? 'max-w-[85%] rounded-3xl bg-gpt-user px-4 py-2.5' : 'max-w-full'}>
                      <p className="whitespace-pre-wrap break-words text-[15px] leading-7">
                        {message.content || (message.pending ? '' : '')}
                        {message.pending ? (
                          <span className="ml-0.5 inline-block h-4 w-2 animate-pulse bg-gpt-ink align-middle" />
                        ) : null}
                      </p>
                      {hasCitations ? (
                        <ul className="mt-3 flex flex-wrap gap-1.5">
                          {message.citations?.map((citation, index) => (
                            <li
                              key={`${citation.sourceId}-${citation.chunkIndex}-${index}`}
                              className="max-w-full break-all rounded-full bg-gpt-sidebar px-2.5 py-1 text-xs text-gpt-muted"
                            >
                              {citation.sourceId} {formatSimilarityPercent(citation.score)}
                            </li>
                          ))}
                        </ul>
                      ) : null}
                      {!isUser && !message.pending && message.content ? (
                        <button
                          type="button"
                          onClick={() => void copyReply(message.id, message.content)}
                          className="mt-2 h-8 rounded-lg px-2 text-xs text-gpt-muted hover:bg-gpt-hover"
                        >
                          {copiedId === message.id ? '已複製' : copyErrorId === message.id ? '無法複製' : '複製'}
                        </button>
                      ) : null}
                      {message.providerStatus !== undefined && message.providerStatus !== 'LIVE' ? (
                        <p className="mt-2 text-xs text-danger">
                          {message.providerStatus ? `AI 服務狀態：${message.providerStatus}` : '尚未回傳 AI 服務狀態'}
                        </p>
                      ) : null}
                    </div>
                  </li>
                )
              })}
            </ul>
            {failure ? (
              <div className="mt-4 flex items-center justify-between gap-3 rounded-2xl border border-gpt-line px-3 py-2" role="alert">
                <p className="text-sm text-danger">{failure.message}</p>
                <button
                  type="button"
                  onClick={() => void send(failure.retryText, { userId: failure.userId, assistantId: failure.assistantId })}
                  disabled={sending}
                  className="h-9 shrink-0 rounded-full border border-gpt-line px-3 text-sm disabled:opacity-60"
                >
                  重試
                </button>
              </div>
            ) : null}
            <div ref={bottomRef} />
          </div>
        </div>

        <form onSubmit={onSubmit} className="safe-x shrink-0 px-4 pb-4">
          <div className="mx-auto flex w-full max-w-3xl items-end gap-2 rounded-[28px] border border-gpt-line bg-gpt-canvas px-3 py-2 shadow-composer">
            <label className="sr-only" htmlFor="chat-draft">
              訊息
            </label>
            <textarea
              id="chat-draft"
              ref={draftRef}
              rows={1}
              value={draft}
              enterKeyHint="send"
              placeholder="傳訊息給 Agent Portal"
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  if (!sending) void send(draft)
                }
              }}
              className="max-h-40 min-h-10 flex-1 resize-none bg-transparent px-2 py-2 text-base outline-none"
            />
            <button
              type="submit"
              disabled={(historyLoading && !freshChat) || (!sending && !draft.trim())}
              aria-label={sending ? '停止生成' : '傳送'}
              className="mb-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gpt-ink text-gpt-canvas disabled:opacity-30"
            >
              {sending ? <StopIcon /> : <SendIcon />}
            </button>
          </div>
          <p className="mx-auto mt-2 max-w-3xl text-center text-xs text-gpt-muted">
            回覆成功後寫入這個帳號。內容由 AI 產生，請自行確認。
          </p>
        </form>
      </section>
    </div>
  )
}

function SendIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
      <path d="M8 13V3M8 3L4 7M8 3l4 4" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
    </svg>
  )
}

function StopIcon() {
  return <span className="block h-2.5 w-2.5 rounded-[2px] bg-current" />
}
