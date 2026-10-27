import { useRef, useState } from 'react'
import { Pencil, RefreshCw, Search, SquarePen, Trash, X } from 'lucide-react'

import type { SiteAiConversation } from '@/types/api'
import { errorMessage } from '@/utils/http/apiError'

/** 与站点会话标题生成截断、重命名 DTO 上限一致。 */
const TITLE_MAX_LENGTH = 60

interface ConversationSheetProps {
  open: boolean
  items: SiteAiConversation[]
  loading: boolean
  error: string | null
  activeId: string | null
  /** 串流中的当前会话。该行删除按钮停用。 */
  deleteLockedId: string | null
  onClose: () => void
  onRefresh: () => void
  onCreate: () => void
  onOpen: (id: string) => void
  onRename: (id: string, title: string) => Promise<void>
  onDelete: (id: string) => Promise<void>
}

export function ConversationSheet({
  open,
  items,
  loading,
  error,
  activeId,
  deleteLockedId,
  onClose,
  onRefresh,
  onCreate,
  onOpen,
  onRename,
  onDelete,
}: ConversationSheetProps) {
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editDraft, setEditDraft] = useState('')
  const [hint, setHint] = useState<string | null>(null)
  const [confirmId, setConfirmId] = useState<string | null>(null)
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [query, setQuery] = useState('')
  const pendingRef = useRef(false)

  if (!open) return null

  function beginRename(item: SiteAiConversation) {
    if (pendingRef.current) return
    setEditingId(String(item.id))
    setEditDraft(item.title?.trim() || '')
    setHint(null)
    setConfirmId(null)
    setDeleteError(null)
  }

  function cancelRename() {
    if (pendingRef.current) return
    setEditingId(null)
    setEditDraft('')
    setHint(null)
  }

  function submitRename(id: string) {
    if (pendingRef.current) return
    const trimmed = editDraft.trim()
    if (!trimmed) {
      setHint('標題不可為空白')
      return
    }
    if (trimmed.length > TITLE_MAX_LENGTH) {
      setHint(`標題最多 ${TITLE_MAX_LENGTH} 字`)
      return
    }
    pendingRef.current = true
    setPending(true)
    setHint(null)
    void onRename(id, trimmed)
      .then(() => {
        setEditingId(null)
        setEditDraft('')
      })
      .catch((renameError: unknown) => {
        setHint(errorMessage(renameError))
      })
      .finally(() => {
        pendingRef.current = false
        setPending(false)
      })
  }

  const visibleItems = query.trim()
    ? items.filter((item) => (item.title ?? '').toLowerCase().includes(query.trim().toLowerCase()))
    : items

  function submitDelete(id: string) {
    if (pendingRef.current) return
    pendingRef.current = true
    setPending(true)
    setDeleteError(null)
    void onDelete(id)
      .then(() => {
        setConfirmId(null)
      })
      .catch((deleteFailure: unknown) => {
        setDeleteError(errorMessage(deleteFailure))
      })
      .finally(() => {
        pendingRef.current = false
        setPending(false)
      })
  }

  return (
    <div className="flex h-full min-h-0 w-full flex-col bg-gpt-sidebar text-gpt-ink">
      <header className="flex items-center justify-between px-3 pt-1">
        <p className="px-2 text-xs font-medium tracking-wide text-gpt-muted">對話紀錄</p>
        <button
          type="button"
          onClick={onClose}
          aria-label="關閉選單"
          className="flex h-9 w-9 items-center justify-center rounded-lg text-gpt-muted hover:bg-gpt-hover md:hidden"
        >
          <X size={18} />
        </button>
      </header>
      <div className="flex gap-2 px-3 py-2.5">
        <button
          type="button"
          onClick={onCreate}
          className="flex h-10 flex-1 items-center gap-2.5 rounded-lg px-3 text-left text-sm hover:bg-gpt-hover"
        >
          <SquarePen size={17} className="shrink-0 text-gpt-muted" />
          新對話
        </button>
        <button
          type="button"
          onClick={onRefresh}
          aria-label="重新整理"
          title="重新整理"
          className="flex h-10 w-10 items-center justify-center rounded-lg text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink"
        >
          <RefreshCw size={16} />
        </button>
      </div>
      <div className="px-3 pb-2">
        <div className="relative">
          <Search
            size={15}
            className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gpt-muted"
          />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="搜尋對話"
            aria-label="搜尋對話"
            className="w-full rounded-lg border border-gpt-line bg-gpt-canvas py-2 pl-9 pr-8 text-sm outline-none placeholder:text-gpt-muted focus:border-gpt-muted"
          />
          {query ? (
            <button
              type="button"
              aria-label="清除搜尋"
              onClick={() => setQuery('')}
              className="absolute right-1.5 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-full text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink"
            >
              <X size={13} />
            </button>
          ) : null}
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
        {loading ? <p className="px-2 py-6 text-sm text-gpt-muted">正在載入對話…</p> : null}
        {error ? (
          <div className="px-2 py-6">
            <p className="text-sm text-danger">{error}</p>
            <button type="button" onClick={onRefresh} className="mt-3 h-9 rounded-lg px-3 text-sm hover:bg-gpt-hover">
              重試
            </button>
          </div>
        ) : null}
        {deleteError ? <p className="px-2 py-2 text-sm text-danger">{deleteError}</p> : null}
        {!loading && !error && items.length > 0 && visibleItems.length === 0 ? (
          <p className="px-2 py-6 text-sm leading-6 text-gpt-muted">找不到符合「{query.trim()}」的對話。</p>
        ) : null}
        {!loading && !error && items.length === 0 ? (
          <p className="px-2 py-6 text-sm leading-6 text-gpt-muted">還沒有對話。送出第一則訊息後會出現在這裡。</p>
        ) : null}
        <ul className="flex flex-col">
          {visibleItems.map((item) => {
            const id = String(item.id)
            const selected = id === activeId
            const deleteLocked = deleteLockedId != null && id === deleteLockedId
            const title = item.title?.trim() || '未命名對話'
            if (editingId === id) {
              return (
                <li key={id} className="rounded-lg px-2 py-2">
                  <label className="sr-only" htmlFor={`rename-${id}`}>
                    對話標題
                  </label>
                  <input
                    id={`rename-${id}`}
                    value={editDraft}
                    autoFocus
                    maxLength={TITLE_MAX_LENGTH}
                    onChange={(event) => {
                      setEditDraft(event.target.value)
                      setHint(null)
                    }}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') {
                        event.preventDefault()
                        submitRename(id)
                      } else if (event.key === 'Escape') {
                        event.preventDefault()
                        cancelRename()
                      }
                    }}
                    className="h-10 w-full rounded-lg border border-gpt-line bg-gpt-canvas px-3 text-sm outline-none focus:border-gpt-ink"
                  />
                  {hint ? <p className="mt-1 text-sm text-danger">{hint}</p> : null}
                  <div className="mt-2 flex gap-2">
                    <button
                      type="button"
                      disabled={pending}
                      onClick={() => submitRename(id)}
                      className="h-9 rounded-lg bg-gpt-ink px-3 text-sm text-gpt-canvas disabled:opacity-60"
                    >
                      確認
                    </button>
                    <button
                      type="button"
                      disabled={pending}
                      onClick={cancelRename}
                      className="h-9 rounded-lg px-3 text-sm hover:bg-gpt-hover disabled:opacity-60"
                    >
                      取消
                    </button>
                  </div>
                </li>
              )
            }
            return (
              <li key={id} className="group flex items-center gap-1 rounded-lg hover:bg-gpt-hover">
                <button
                  type="button"
                  onClick={() => onOpen(id)}
                  disabled={pending}
                  className={
                    selected
                      ? 'flex min-w-0 flex-1 items-center rounded-lg bg-gpt-active px-2 py-2.5 text-left disabled:opacity-60'
                      : 'flex min-w-0 flex-1 items-center rounded-lg px-2 py-2.5 text-left disabled:opacity-60'
                  }
                >
                  <span className="w-full truncate text-sm">{title}</span>
                </button>
                {confirmId === id ? (
                  <div className="flex shrink-0 flex-col items-end gap-1 py-2">
                    <span className="text-xs text-muted-foreground">確定刪除此對話？</span>
                    <div className="flex gap-1">
                    <button
                      type="button"
                      disabled={pending || deleteLocked}
                      title={deleteLocked ? '正在產生回覆，請稍後再刪除' : undefined}
                      onClick={() => submitDelete(id)}
                      className="h-8 rounded-lg px-2 text-xs text-danger disabled:opacity-60"
                    >
                        刪除
                      </button>
                      <button
                        type="button"
                        disabled={pending}
                        onClick={() => setConfirmId(null)}
                        className="h-8 px-2 text-xs text-gpt-muted disabled:opacity-60"
                      >
                        取消
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="flex shrink-0 gap-1">
                    <button
                      type="button"
                      disabled={pending}
                      onClick={() => beginRename(item)}
                      aria-label="重命名對話"
                      title="重命名"
                      className="flex h-8 w-8 items-center justify-center rounded-lg text-gpt-muted opacity-100 hover:bg-gpt-hover hover:text-gpt-ink disabled:opacity-60 md:opacity-0 md:group-hover:opacity-100 md:group-focus-within:opacity-100"
                    >
                      <Pencil size={14} />
                    </button>
                    <button
                      type="button"
                      disabled={pending || deleteLocked}
                      aria-label="刪除對話"
                      title={deleteLocked ? '正在產生回覆，請稍後再刪除' : '刪除'}
                      onClick={() => {
                        setDeleteError(null)
                        setConfirmId(id)
                      }}
                      className="flex h-8 w-8 items-center justify-center rounded-lg text-gpt-muted opacity-100 hover:bg-gpt-hover hover:text-danger disabled:opacity-60 md:opacity-0 md:group-hover:opacity-100 md:group-focus-within:opacity-100"
                    >
                      <Trash size={14} />
                    </button>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}

