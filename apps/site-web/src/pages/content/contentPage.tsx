import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { publicApi } from '@/api/publicApi'
import { FullScreenStatus, Screen } from '@/components/screen'
import { RetryButton } from '@/components/retryButton'
import { sitePath } from '@/router/config/routePaths'
import type { PublicContentDetail } from '@/types/public'
import { formatPublishedAt } from '@/utils/formatPublishedAt'
import { isNotFound } from '@/utils/http/apiError'

type DetailState =
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'error' }
  | { kind: 'ready'; content: PublicContentDetail }

function readContentId(raw: string | undefined): number | null {
  if (!raw || !/^[1-9]\d*$/.test(raw)) return null
  const value = Number(raw)
  return Number.isSafeInteger(value) ? value : null
}

export function ContentPage() {
  const { siteCode, contentId: contentIdRaw } = useParams()
  const contentId = readContentId(contentIdRaw)
  const [attempt, setAttempt] = useState(0)
  if (!siteCode || contentId == null) return <FullScreenStatus text="找不到這則內容" />
  return (
    <ContentLoad
      key={`${siteCode}:${contentId}:${attempt}`}
      siteCode={siteCode}
      contentId={contentId}
      onRetry={() => setAttempt((value) => value + 1)}
    />
  )
}

function ContentLoad({
  siteCode,
  contentId,
  onRetry,
}: {
  siteCode: string
  contentId: number
  onRetry: () => void
}) {
  const [state, setState] = useState<DetailState>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    void publicApi.getContent(siteCode, contentId).then(
      (content) => {
        if (!cancelled) setState({ kind: 'ready', content })
      },
      (error: unknown) => {
        if (!cancelled) setState(isNotFound(error) ? { kind: 'not-found' } : { kind: 'error' })
      },
    )
    return () => {
      cancelled = true
    }
  }, [siteCode, contentId])

  if (state.kind === 'not-found') return <FullScreenStatus text="找不到這則內容" />
  if (state.kind === 'loading') return <FullScreenStatus text="載入中" />
  if (state.kind === 'error') {
    return (
      <FullScreenStatus text="無法載入，請再試一次">
        <RetryButton onRetry={onRetry} />
      </FullScreenStatus>
    )
  }

  const { content } = state
  return (
    <Screen>
      <main className="safe-top safe-x safe-bottom flex flex-1 flex-col px-page pt-8">
        <Link to={sitePath(siteCode)} className="text-sm text-primary">
          返回列表
        </Link>
        <h1 className="mt-4 text-3xl font-semibold tracking-tight">{content.title}</h1>
        <p className="mt-2 text-sm text-muted-foreground">{formatPublishedAt(content.publishedAt)}</p>
        <p className="mt-section whitespace-pre-wrap text-sm leading-7">{content.body ?? ''}</p>
      </main>
    </Screen>
  )
}
