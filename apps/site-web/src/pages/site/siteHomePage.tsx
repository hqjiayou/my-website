import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { publicApi } from '@/api/publicApi'
import { FullScreenStatus, Screen } from '@/components/screen'
import { RetryButton } from '@/components/retryButton'
import { contentPath } from '@/router/config/routePaths'
import type { PublicContentItem, PublicSite } from '@/types/public'
import { formatPublishedAt } from '@/utils/formatPublishedAt'
import { isNotFound } from '@/utils/http/apiError'

type HomeState =
  | { kind: 'loading' }
  | { kind: 'not-found' }
  | { kind: 'error' }
  | { kind: 'ready'; site: PublicSite; items: PublicContentItem[] }

export function SiteHomePage() {
  const { siteCode } = useParams()
  const [attempt, setAttempt] = useState(0)
  if (!siteCode) return <FullScreenStatus text="找不到這個站點" />
  return (
    <SiteHomeLoad
      key={`${siteCode}:${attempt}`}
      siteCode={siteCode}
      onRetry={() => setAttempt((value) => value + 1)}
    />
  )
}

function SiteHomeLoad({ siteCode, onRetry }: { siteCode: string; onRetry: () => void }) {
  const [state, setState] = useState<HomeState>({ kind: 'loading' })

  useEffect(() => {
    let cancelled = false
    void (async () => {
      try {
        const site = await publicApi.getSite(siteCode)
        const list = await publicApi.listContents(siteCode)
        if (cancelled) return
        if (!Array.isArray(list.items)) {
          setState({ kind: 'error' })
          return
        }
        setState({ kind: 'ready', site, items: list.items })
      } catch (error) {
        if (!cancelled) setState(isNotFound(error) ? { kind: 'not-found' } : { kind: 'error' })
      }
    })()
    return () => {
      cancelled = true
    }
  }, [siteCode])

  if (state.kind === 'not-found') return <FullScreenStatus text="找不到這個站點" />
  if (state.kind === 'loading') return <FullScreenStatus text="載入中" />
  if (state.kind === 'error') {
    return (
      <FullScreenStatus text="無法載入，請再試一次">
        <RetryButton onRetry={onRetry} />
      </FullScreenStatus>
    )
  }

  const { site, items } = state
  return (
    <Screen>
      <header className="safe-top safe-x px-page pt-8">
        <h1 className="text-3xl font-semibold tracking-tight">{site.siteName}</h1>
        <p className="mt-2 text-sm text-muted-foreground">{site.siteType}</p>
        {site.description ? <p className="mt-3 text-sm leading-6">{site.description}</p> : null}
      </header>
      <main className="safe-x safe-bottom flex flex-1 flex-col px-page pt-8">
        {items.length === 0 ? (
          <p className="text-sm leading-6 text-muted-foreground">目前沒有已發佈的內容</p>
        ) : (
          <ul className="flex flex-col gap-stack">
            {items.map((item) => (
              <li key={item.id}>
                <Link
                  to={contentPath(siteCode, item.id)}
                  className="block rounded-panel border border-border bg-surface p-4 shadow-raised"
                >
                  <span className="block text-lg font-semibold">{item.title}</span>
                  <span className="mt-1 block text-sm text-muted-foreground">
                    {item.type} · {formatPublishedAt(item.publishedAt)}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </main>
    </Screen>
  )
}
