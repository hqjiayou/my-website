import { useRef, useState } from 'react'

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
    <div className="fixed inset-0 z-20 flex flex-col bg-background">
      <header className="safe-top safe-x flex items-center justify-between border-b border-border px-4 py-3">
        <h2 className="text-base font-semibold">對話歷史</h2>
        <button type="button" onClick={onClose} className="h-10 px-2 text-sm text-primary">
          關閉
        </button>
      </header>
      <div className="safe-x flex gap-2 px-4 py-3">
        <button type="button" onClick={onCreate} className="h-11 flex-1 rounded-control bg-primary text-sm font-medium text-primary-foreground">
          新對話
        </button>
        <button type="button" onClick={onRefresh} className="h-11 rounded-control border border-border bg-surface px-4 text-sm">
          重新整理
        </button>
      </div>
      <div className="safe-x safe-bottom min-h-0 flex-1 overflow-y-auto px-4">
        {loading ? <p className="py-6 text-sm text-muted-foreground">正在載入對話…</p> : null}
        {error ? (
          <div className="py-6">
            <p className="text-sm text-danger">{error}</p>
            <button type="button" onClick={onRefresh} className="mt-3 h-10 rounded-control border border-border bg-surface px-4 text-sm">
              重試
            </button>
          </div>
        ) : null}
        {deleteError ? <p className="py-2 text-sm text-danger">{deleteError}</p> : null}
        {!loading && !error && items.length === 0 ? (
          <p className="py-6 text-sm leading-6 text-muted-foreground">還沒有對話。送出第一則訊息後會出現在這裡。</p>
        ) : null}
        <ul className="flex flex-col">
          {items.map((item) => {
            const id = String(item.id)
            const selected = id === activeId
            const deleteLocked = deleteLockedId != null && id === deleteLockedId
            const title = item.title?.trim() || '未命名對話'
            if (editingId === id) {
              return (
                <li key={id} className="border-b border-border py-3">
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
                    className="h-11 w-full rounded-control border border-border bg-surface px-3 text-base outline-none focus:border-primary"
                  />
                  {hint ? <p className="mt-1 text-sm text-danger">{hint}</p> : null}
                  <div className="mt-2 flex gap-2">
                    <button
                      type="button"
                      disabled={pending}
                      onClick={() => submitRename(id)}
                      className="h-10 rounded-control bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60"
                    >
                      確認
                    </button>
                    <button
                      type="button"
                      disabled={pending}
                      onClick={cancelRename}
                      className="h-10 rounded-control border border-border bg-surface px-4 text-sm disabled:opacity-60"
                    >
                      取消
                    </button>
                  </div>
                </li>
              )
            }
            return (
              <li key={id} className="flex items-center gap-1 border-b border-border">
                <button
                  type="button"
                  onClick={() => onOpen(id)}
                  disabled={pending}
                  className="flex min-w-0 flex-1 flex-col items-start py-3 text-left disabled:opacity-60"
                >
                  <span className={selected ? 'w-full truncate font-semibold text-primary' : 'w-full truncate font-medium'}>
                    {title}
                  </span>
                  <span className="mt-1 text-xs text-muted-foreground">{formatTime(item.lastMessageAt)}</span>
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
                      className="h-10 rounded-control border border-danger-line px-3 text-sm text-danger disabled:opacity-60"
                    >
                        刪除
                      </button>
                      <button
                        type="button"
                        disabled={pending}
                        onClick={() => setConfirmId(null)}
                        className="h-10 px-2 text-sm text-primary disabled:opacity-60"
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
                      className="h-11 px-2 text-sm text-primary disabled:opacity-60"
                    >
                      重命名
                    </button>
                    <button
                      type="button"
                      disabled={pending || deleteLocked}
                      title={deleteLocked ? '正在產生回覆，請稍後再刪除' : undefined}
                      onClick={() => {
                        setDeleteError(null)
                        setConfirmId(id)
                      }}
                      className="h-11 px-2 text-sm text-danger disabled:opacity-60"
                    >
                      刪除
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

function formatTime(value: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-TW', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
