import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { FileText, ImageIcon } from 'lucide-react'

import { agentAiApi } from '@/api/agentAiApi'
import { MarkdownText } from '@/components/markdown'
import type { SharedConversation } from '@/types/api'

/** 公開分享頁：不需要登入，憑 token 讀分享時刻的快照。 */
export function SharePage() {
  const { token } = useParams<{ token: string }>()
  const [shared, setShared] = useState<SharedConversation | null>(null)
  const [failed, setFailed] = useState(() => !token)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    agentAiApi
      .getSharedConversation(token)
      .then((data) => {
        if (!cancelled) setShared(data)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
    return () => {
      cancelled = true
    }
  }, [token])

  return (
    <div className="flex min-h-dvh flex-col bg-gpt-canvas text-gpt-ink">
      <main className="safe-x mx-auto flex w-full max-w-3xl flex-1 flex-col px-4 py-8">
        {failed ? (
          <div className="flex flex-1 flex-col items-center justify-center text-center">
            <p className="text-lg font-semibold">連結已失效</p>
            <p className="mt-2 text-sm text-gpt-muted">這個分享可能已被撤銷，或原對話已被刪除。</p>
          </div>
        ) : shared === null ? (
          <div className="flex flex-col gap-3" aria-label="載入中">
            <span className="h-6 w-1/2 animate-pulse rounded bg-gpt-hover" />
            <span className="h-4 w-3/4 animate-pulse rounded bg-gpt-hover" />
            <span className="h-4 w-2/3 animate-pulse rounded bg-gpt-hover" />
          </div>
        ) : (
          <>
            <h1 className="text-2xl font-semibold tracking-tight">{shared.title?.trim() || '分享的對話'}</h1>
            {shared.sharedAt ? (
              <p className="mt-1.5 text-xs text-gpt-muted">分享於 {formatWhen(shared.sharedAt)}</p>
            ) : null}
            <div className="mt-8 flex flex-col gap-6">
              {shared.messages.map((message, index) =>
                message.role === 'USER' ? (
                  <div key={index} className="flex flex-col items-end gap-2">
                    {message.imageCount > 0 || message.documentNames.length > 0 ? (
                      <div className="flex flex-wrap justify-end gap-1.5">
                        {message.imageCount > 0 ? (
                          <span className="flex items-center gap-1.5 rounded-full bg-gpt-sidebar px-2.5 py-1 text-xs text-gpt-muted">
                            <ImageIcon size={13} /> 圖片 ×{message.imageCount}
                          </span>
                        ) : null}
                        {message.documentNames.map((name) => (
                          <span
                            key={name}
                            className="flex max-w-full items-center gap-1.5 rounded-full bg-gpt-sidebar px-2.5 py-1 text-xs text-gpt-muted"
                          >
                            <FileText size={13} className="shrink-0" />
                            <span className="truncate">{name}</span>
                          </span>
                        ))}
                      </div>
                    ) : null}
                    {message.content ? (
                      <div className="max-w-[85%] whitespace-pre-wrap rounded-3xl bg-gpt-user px-4 py-2.5 text-base leading-7">
                        {message.content}
                      </div>
                    ) : null}
                  </div>
                ) : (
                  <div key={index} className="text-base leading-7">
                    <MarkdownText>{message.content ?? ''}</MarkdownText>
                  </div>
                ),
              )}
            </div>
            <p className="mt-12 border-t border-gpt-line pb-2 pt-4 text-center text-xs text-gpt-muted">
              這是經由 Agent Portal 分享的對話快照，內容為分享當時的狀態。
            </p>
          </>
        )}
      </main>
    </div>
  )
}

function formatWhen(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-TW', {
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
