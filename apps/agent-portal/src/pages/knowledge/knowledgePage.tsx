import { useEffect, useMemo, useState, type FormEvent } from 'react'

import { siteAiApi } from '@/api/siteAiApi'
import { BottomNav } from '@/components/bottomNav'
import { Card, CardTitle } from '@/components/card'
import { MarkdownText } from '@/components/markdown'
import { Screen } from '@/components/screen'
import { resolvePortalSiteId } from '@/config/env'
import { useAuthStore } from '@/stores/authStore'
import type {
  SiteKnowledgeDocumentDetail,
  SiteKnowledgeDocumentListItem,
  SiteKnowledgeHit,
} from '@/types/api'
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
  const [rerankApplied, setRerankApplied] = useState(false)
  const [expanded, setExpanded] = useState<Record<number, boolean>>({})

  const [documents, setDocuments] = useState<SiteKnowledgeDocumentListItem[] | null>(null)
  const [documentsError, setDocumentsError] = useState<string | null>(null)
  const [openDocumentId, setOpenDocumentId] = useState<number | null>(null)
  const [details, setDetails] = useState<Record<number, SiteKnowledgeDocumentDetail | 'loading' | 'error'>>({})

  useEffect(() => {
    if (!site.ok) return
    let cancelled = false
    siteAiApi
      .listKnowledgeDocuments(site.siteId)
      .then((items) => {
        if (!cancelled) setDocuments(items)
      })
      .catch((caught) => {
        if (!cancelled) setDocumentsError(errorMessage(caught))
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [site.ok])

  const titleBySourceId = useMemo(() => {
    const map = new Map<string, string>()
    for (const document of documents ?? []) {
      map.set(`kb:${document.id}`, document.title)
    }
    return map
  }, [documents])

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
      setRerankApplied(result.rerankApplied === true)
    } catch (caught) {
      setHits([])
      setDegraded(false)
      setRerankApplied(false)
      setError(errorMessage(caught))
    } finally {
      setLoading(false)
    }
  }

  function clearSearch() {
    setSearched(false)
    setHits([])
    setError(null)
    setDegraded(false)
    setRerankApplied(false)
    setQuery('')
  }

  async function toggleDocument(document: SiteKnowledgeDocumentListItem) {
    if (!site.ok) return
    if (openDocumentId === document.id) {
      setOpenDocumentId(null)
      return
    }
    setOpenDocumentId(document.id)
    if (details[document.id] !== undefined) return
    setDetails((current) => ({ ...current, [document.id]: 'loading' }))
    try {
      const detail = await siteAiApi.getKnowledgeDocument(site.siteId, document.id)
      setDetails((current) => ({ ...current, [document.id]: detail }))
    } catch {
      setDetails((current) => ({ ...current, [document.id]: 'error' }))
    }
  }

  if (!site.ok) {
    return (
      <Screen>
        <main className="safe-top safe-x with-bottom-nav flex flex-1 flex-col px-page py-6">
          <h1 className="text-2xl font-semibold">知識庫</h1>
          <p className="mt-6 text-sm leading-6 text-danger">{site.message}</p>
        </main>
        <BottomNav />
      </Screen>
    )
  }

  return (
    <Screen>
      <header className="safe-top safe-x shrink-0 px-page pt-4">
        <h1 className="text-2xl font-semibold tracking-tight">知識庫</h1>
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
          className="h-12 min-w-0 flex-1 rounded-full border border-border bg-surface px-4 text-base text-gpt-ink outline-none focus:border-primary"
        />
        <button
          type="submit"
          disabled={loading || !query.trim()}
          className="h-12 shrink-0 rounded-full bg-primary px-4 text-sm font-medium text-primary-foreground disabled:opacity-30"
        >
          {loading ? '搜尋中' : '搜尋'}
        </button>
      </form>

      <main className="safe-x with-bottom-nav mt-section flex flex-1 flex-col gap-stack px-page">
        {searched ? (
          <>
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold">搜尋結果</h2>
              <button type="button" onClick={clearSearch} className="text-sm font-medium text-primary">
                清除搜尋
              </button>
            </div>
            {loading ? <p className="text-sm text-gpt-muted">正在搜尋…</p> : null}
            {!loading && error ? (
              <p className="rounded-control bg-danger-soft px-3 py-2 text-sm text-danger" role="alert">
                {error}
              </p>
            ) : null}
            {!loading && !error && degraded ? (
              <p className="rounded-panel border border-border bg-surface px-3 py-2 text-sm leading-6" role="status">
                知識庫暫時不穩定，結果可能不完整
              </p>
            ) : null}
            {!loading && !error && rerankApplied && hits.length > 0 ? (
              <p className="text-xs text-gpt-muted" role="status">
                結果已由 AI 依相關性重排
              </p>
            ) : null}
            {!loading && !error && hits.length === 0 ? (
              <p className="text-sm leading-6 text-gpt-muted">沒有找到相關內容</p>
            ) : null}
            {!loading && !error && hits.length > 0 ? (
              <ul className="flex flex-col gap-stack">
                {hits.map((hit, index) => (
                  <KnowledgeHit
                    key={`${hit.sourceId}-${hit.chunkIndex}-${index}`}
                    hit={hit}
                    sourceTitle={titleBySourceId.get(hit.sourceId)}
                    open={expanded[index] === true}
                    onToggle={() => setExpanded((current) => ({ ...current, [index]: !current[index] }))}
                  />
                ))}
              </ul>
            ) : null}
          </>
        ) : (
          <Card>
            <CardTitle>全部文件{documents !== null ? `（${documents.length} 篇）` : ''}</CardTitle>
            {documents === null && !documentsError ? (
              <p className="mt-3 text-sm text-gpt-muted">載入中…</p>
            ) : null}
            {documentsError ? (
              <p className="mt-3 text-sm leading-6 text-danger">{documentsError}</p>
            ) : null}
            {documents !== null && documents.length === 0 ? (
              <p className="mt-3 text-sm leading-6 text-gpt-muted">
                這個站點尚無知識文件。可以先用上方搜尋，或請管理員在後台上傳。
              </p>
            ) : null}
            {documents !== null && documents.length > 0 ? (
              <ul className="mt-2 flex flex-col">
                {documents.map((document) => {
                  const detail = details[document.id]
                  const open = openDocumentId === document.id
                  return (
                    <li key={document.id} className="border-b border-border last:border-b-0">
                      <button
                        type="button"
                        onClick={() => void toggleDocument(document)}
                        aria-expanded={open}
                        className="flex w-full items-center justify-between gap-3 rounded-lg px-2 py-3 text-left hover:bg-gpt-hover"
                      >
                        <span className="min-w-0 truncate text-sm font-medium">{document.title}</span>
                        <span className="shrink-0 text-xs text-gpt-muted">{open ? '收合' : '閱讀'}</span>
                      </button>
                      {open ? (
                        <div className="px-2 pb-4">
                          {detail === 'loading' || detail === undefined ? (
                            <p className="text-sm text-gpt-muted">載入中…</p>
                          ) : detail === 'error' ? (
                            <p className="text-sm text-danger">文件載入失敗，請稍後重試</p>
                          ) : (
                            <MarkdownText>{detail.content}</MarkdownText>
                          )}
                        </div>
                      ) : null}
                    </li>
                  )
                })}
              </ul>
            ) : null}
          </Card>
        )}
      </main>
      <BottomNav />
    </Screen>
  )
}

function KnowledgeHit({
  hit,
  sourceTitle,
  open,
  onToggle,
}: {
  hit: SiteKnowledgeHit
  sourceTitle?: string
  open: boolean
  onToggle: () => void
}) {
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
      <p className="mt-3 text-xs leading-5 text-gpt-muted">
        來源 {sourceTitle ?? hit.sourceId} · 片段 {hit.chunkIndex} · 相似度 {formatSimilarityPercent(hit.score)}
      </p>
    </li>
  )
}
