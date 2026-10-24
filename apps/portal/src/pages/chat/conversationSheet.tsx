import type { SiteAiConversation } from '@/types/api'

interface ConversationSheetProps {
  open: boolean
  items: SiteAiConversation[]
  loading: boolean
  error: string | null
  activeId: string | null
  onClose: () => void
  onRefresh: () => void
  onCreate: () => void
  onOpen: (id: string) => void
}

export function ConversationSheet({
  open,
  items,
  loading,
  error,
  activeId,
  onClose,
  onRefresh,
  onCreate,
  onOpen,
}: ConversationSheetProps) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-20 flex flex-col bg-background">
      <header className="safe-top safe-x flex items-center justify-between border-b border-border px-4 py-3">
        <h2 className="text-base font-semibold">對話歷史</h2>
        <button type="button" onClick={onClose} className="h-10 px-2 text-sm text-primary">
          關閉
        </button>
      </header>
      <div className="safe-x flex gap-2 px-4 py-3">
        <button type="button" onClick={onCreate} className="h-11 flex-1 rounded-xl bg-primary text-sm text-primary-foreground">
          新對話
        </button>
        <button type="button" onClick={onRefresh} className="h-11 rounded-xl border border-border px-4 text-sm">
          重新整理
        </button>
      </div>
      <div className="safe-x safe-bottom min-h-0 flex-1 overflow-y-auto px-4">
        {loading ? <p className="py-6 text-sm text-muted-foreground">正在載入對話…</p> : null}
        {error ? (
          <div className="py-6">
            <p className="text-sm text-danger">{error}</p>
            <button type="button" onClick={onRefresh} className="mt-3 h-10 rounded-xl border border-border px-4 text-sm">
              重試
            </button>
          </div>
        ) : null}
        {!loading && !error && items.length === 0 ? (
          <p className="py-6 text-sm leading-6 text-muted-foreground">還沒有對話。送出第一則訊息後會出現在這裡。</p>
        ) : null}
        <ul className="flex flex-col">
          {items.map((item) => {
            const id = String(item.id)
            const selected = id === activeId
            return (
              <li key={id} className="border-b border-border">
                <button type="button" onClick={() => onOpen(id)} className="flex w-full flex-col items-start py-3 text-left">
                  <span className={selected ? 'font-semibold text-primary' : 'font-medium'}>{item.title?.trim() || '未命名對話'}</span>
                  <span className="mt-1 text-xs text-muted-foreground">{formatTime(item.lastMessageAt)}</span>
                </button>
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
