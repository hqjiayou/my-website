import { useEffect, useMemo, useState } from 'react'
import { Trash } from 'lucide-react'

import { agentAiApi } from '@/api/agentAiApi'
import { Screen } from '@/components/screen'
import { resolvePortalAgentId } from '@/config/env'
import { useAuthStore } from '@/stores/authStore'
import type { AgentUserMemory } from '@/types/api'
import { errorMessage } from '@/utils/http/apiError'

export function MemoriesPage() {
  const site = useMemo(() => resolvePortalAgentId(useAuthStore.getState().site?.agentId), [])
  const [items, setItems] = useState<AgentUserMemory[]>([])
  const [loading, setLoading] = useState(site.ok)
  const [error, setError] = useState<string | null>(null)
  const [pending, setPending] = useState(false)
  const [confirmId, setConfirmId] = useState<string | null>(null)
  const [confirmClear, setConfirmClear] = useState(false)

  useEffect(() => {
    if (!site.ok) return
    let current = true
    agentAiApi.listMemories(site.agentId).then(
      (rows) => {
        if (!current) return
        setItems(Array.isArray(rows) ? rows : [])
        setLoading(false)
      },
      (caught: unknown) => {
        if (!current) return
        setError(errorMessage(caught))
        setLoading(false)
      },
    )
    return () => {
      current = false
    }
  }, [site])

  async function removeOne(id: string) {
    if (!site.ok || pending) return
    setPending(true)
    setError(null)
    try {
      await agentAiApi.deleteMemory(site.agentId, id)
      setConfirmId(null)
      setItems((current) => current.filter((item) => String(item.id) !== id))
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setPending(false)
    }
  }

  async function clearAll() {
    if (!site.ok || pending) return
    setPending(true)
    setError(null)
    try {
      await agentAiApi.clearMemories(site.agentId)
      setConfirmClear(false)
      setConfirmId(null)
      setItems([])
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setPending(false)
    }
  }

  if (!site.ok) {
    return (
      <Screen>
        <main className="safe-x flex flex-1 flex-col overflow-y-auto px-page py-6">
          <h1 className="text-2xl font-semibold">我的記憶</h1>
          <p className="mt-6 text-sm leading-6 text-danger">{site.message}</p>
        </main>
      </Screen>
    )
  }

  return (
    <Screen>
      <main className="safe-x flex flex-1 flex-col overflow-y-auto px-page py-6">
        <h1 className="text-2xl font-semibold tracking-tight">我的記憶</h1>
        <p className="mt-2 text-sm leading-6 text-gpt-muted">最多保留 50 條，超出自動移除最舊的。</p>
        <div className="mt-section flex flex-col">
        {items.length > 0 ? (
          <div className="mb-4">
            {confirmClear ? (
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm text-gpt-muted">確定清空全部記憶？</span>
                <button
                  type="button"
                  disabled={pending}
                  onClick={() => void clearAll()}
                  className="h-9 rounded-lg px-3 text-sm text-danger disabled:opacity-60"
                >
                  清空
                </button>
                <button
                  type="button"
                  disabled={pending}
                  onClick={() => setConfirmClear(false)}
                  className="h-9 rounded-lg px-3 text-sm text-gpt-muted disabled:opacity-60"
                >
                  取消
                </button>
              </div>
            ) : (
              <button
                type="button"
                disabled={pending}
                onClick={() => {
                  setConfirmId(null)
                  setConfirmClear(true)
                }}
                className="h-9 rounded-lg px-3 text-sm text-danger hover:bg-gpt-hover disabled:opacity-60"
              >
                清空全部
              </button>
            )}
          </div>
        ) : null}
        {loading ? (
          <div className="flex flex-col gap-stack" aria-label="載入中">
            <span className="h-24 animate-pulse rounded-2xl bg-gpt-hover" />
            <span className="h-24 animate-pulse rounded-2xl bg-gpt-hover" />
          </div>
        ) : null}
        {!loading && error ? (
          <p className="rounded-control bg-danger-soft px-3 py-2 text-sm text-danger" role="alert">
            {error}
          </p>
        ) : null}
        {!loading && !error && items.length === 0 ? (
          <p className="text-sm leading-6 text-gpt-muted">還沒有記住的內容。對話裡提到的長期偏好會出現在這裡。</p>
        ) : null}
        {!loading && items.length > 0 ? (
          <ul className="flex flex-col gap-stack">
            {items.map((item) => {
              const id = String(item.id)
              const confirming = confirmId === id
              return (
                <li key={id} className="rounded-2xl border border-gpt-line p-4">
                  <p className="whitespace-pre-wrap break-words text-sm leading-6">{item.content}</p>
                  <p className="mt-2 text-xs text-gpt-muted">{formatTime(item.createdAt)}</p>
                  {confirming ? (
                    <div className="mt-3 flex flex-wrap items-center gap-2">
                      <span className="text-xs text-gpt-muted">確定刪除此記憶？</span>
                      <button
                        type="button"
                        disabled={pending}
                        onClick={() => void removeOne(id)}
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
                  ) : (
                    <button
                      type="button"
                      disabled={pending}
                      onClick={() => {
                        setConfirmClear(false)
                        setConfirmId(id)
                      }}
                      aria-label="刪除此記憶"
                      title="刪除"
                      className="mt-3 flex h-8 w-8 items-center justify-center rounded-lg text-danger transition hover:bg-gpt-hover disabled:opacity-60"
                    >
                      <Trash size={15} />
                    </button>
                  )}
                </li>
              )
            })}
          </ul>
        ) : null}
        </div>
      </main>
    </Screen>
  )
}

function formatTime(value: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-TW', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
