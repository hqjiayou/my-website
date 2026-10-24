import { useMemo, useState, type FormEvent } from 'react'

import { siteAiApi } from '@/api/siteAiApi'
import { BottomNav } from '@/components/bottomNav'
import { Screen } from '@/components/screen'
import { resolvePortalSiteId } from '@/config/env'
import { useAuthStore } from '@/stores/authStore'
import type { SiteKnowledgeHit } from '@/types/api'
import { formatSimilarityPercent } from '@/utils/formatSimilarity'
import { errorMessage } from '@/utils/http/apiError'

const TOP_K = 10
const PREVIEW_CHARS = 180

export function KnowledgePage() {
  const site = useMemo(() => resolvePortalSiteId(useAuthStore.getState().site?.siteId), [])
  const [query, setQuery] = useState('')
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hits, setHits] = useState<SiteKnowledgeHit[]>([])
  const [degraded, setDegraded] = useState(false)
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!site.ok || loading) return
    const trimmed = query.trim()
    if (!trimmed) return
    setLoading(true)
    setError(null)
    setSearched(true)
    setExpanded({})
    try {
      const result = await siteAiApi.searchKnowledge(site.siteId, trimmed, TOP_K)
      setHits(Array.isArray(result.hits) ? result.hits : [])
      setDegraded(result.degraded === true)
    } catch (caught) {
      setHits([])
      setDegraded(false)
      setError(errorMessage(caught))
    } finally {
      setLoading(false)
    }
  }

  if (!site.ok) {
    return (
      <Screen>
        <main className="safe-top safe-x with-bottom-nav flex flex-1 flex-col px-page py-6">
          <h1 className="text-2xl font-semibold">知識搜尋</h1>
          <p className="mt-6 text-sm leading-6 text-danger">{site.message}</p>
        </main>
        <BottomNav />
      </Screen>
    )
  }

  return (
    <Screen>
      <header className="safe-top safe-x shrink-0 px-page pt-4">
        <h1 className="text-2xl font-semibold tracking-tight">知識搜尋</h1>
      </header>
      <form onSubmit={(event) => void onSubmit(event)} className="safe-x mt-4 flex gap-2 px-page">
        <label className="sr-only" htmlFor="knowledge-query">
          搜尋
        </label>
        <input
          id="knowledge-query"
          name="query"
          value={query}
          maxLength={16_000}
          enterKeyHint="search"
          placeholder="輸入要查找的內容"
          onChange={(event) => setQuery(event.target.value)}
          className="h-12 min-w-0 flex-1 rounded-control border border-border bg-surface px-3 text-base text-foreground outline-none focus:border-primary"
        />
        <button
          type="submit"
          disabled={loading || !query.trim()}
          className="h-12 shrink-0 rounded-control bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-60"
        >
          {loading ? '搜尋中' : '搜尋'}
        </button>
      </form>
      <main className="safe-x with-bottom-nav mt-section flex flex-1 flex-col gap-stack px-page">
        {!searched && !loading ? (
          <p className="text-sm leading-6 text-muted-foreground">輸入關鍵字，查找這個站點知識庫裡的內容。</p>
        ) : null}
        {loading ? <p className="text-sm text-muted-foreground">正在搜尋…</p> : null}
        {!loading && error ? (
          <p className="rounded-control bg-danger-soft px-3 py-2 text-sm text-danger" role="alert">
            {error}
          </p>
        ) : null}
        {!loading && !error && degraded ? (
          <p className="rounded-control bg-primary-soft px-3 py-2 text-sm leading-6 text-foreground" role="status">
            知識庫暫時不穩定，結果可能不完整
          </p>
        ) : null}
        {!loading && !error && searched && hits.length === 0 ? (
          <p className="text-sm leading-6 text-muted-foreground">沒有找到相關內容</p>
        ) : null}
        {!loading && !error && hits.length > 0 ? (
          <ul className="flex flex-col gap-stack">
            {hits.map((hit, index) => (
              <KnowledgeHit
                key={`${hit.sourceId}-${hit.chunkIndex}-${index}`}
                hit={hit}
                open={expanded[index] === true}
                onToggle={() => setExpanded((current) => ({ ...current, [index]: !current[index] }))}
              />
            ))}
          </ul>
        ) : null}
      </main>
      <BottomNav />
    </Screen>
  )
}

function KnowledgeHit({ hit, open, onToggle }: { hit: SiteKnowledgeHit; open: boolean; onToggle: () => void }) {
  const content = typeof hit.content === 'string' ? hit.content : ''
  const canToggle = content.length > PREVIEW_CHARS
  const shown = canToggle && !open ? `${content.slice(0, PREVIEW_CHARS)}…` : content

  return (
    <li className="rounded-panel border border-border bg-surface p-4 shadow-raised">
      <p className="whitespace-pre-wrap break-words text-sm leading-6">{shown}</p>
      {canToggle ? (
        <button type="button" onClick={onToggle} className="mt-2 text-sm font-medium text-primary">
          {open ? '收合' : '展開全文'}
        </button>
      ) : null}
      <p className="mt-3 break-all text-xs leading-5 text-muted-foreground">
        來源 {hit.sourceId} · 片段 {hit.chunkIndex} · 相似度 {formatSimilarityPercent(hit.score)}
      </p>
    </li>
  )
}
