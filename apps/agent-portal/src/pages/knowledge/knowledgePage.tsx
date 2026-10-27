import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronDown, Search, X } from 'lucide-react'

import { siteAiApi } from '@/api/siteAiApi'
import { Card, CardTitle } from '@/components/card'
import { MarkdownText } from '@/components/markdown'
import { Screen } from '@/components/screen'
import { resolvePortalSiteId } from '@/config/env'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import type { SiteKnowledgeDocumentDetail, SiteKnowledgeDocumentListItem } from '@/types/api'
import { errorMessage } from '@/utils/http/apiError'

/**
 * 知识库页只做文档浏览与全文阅读。
 * 检索不在这里发生：到 AI 对话里提问，agent 会在回答时自行检索知识库。
 */
export function KnowledgePage() {
  const site = useMemo(() => resolvePortalSiteId(useAuthStore.getState().site?.siteId), [])
  const [documents, setDocuments] = useState<SiteKnowledgeDocumentListItem[] | null>(null)
  const [documentsError, setDocumentsError] = useState<string | null>(null)
  const [query, setQuery] = useState('')
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
        <main className="safe-x flex flex-1 flex-col overflow-y-auto px-page py-6">
          <h1 className="text-2xl font-semibold">知識庫</h1>
          <p className="mt-6 text-sm leading-6 text-danger">{site.message}</p>
        </main>
      </Screen>
    )
  }

  const visibleDocuments =
    documents?.filter((document) => !query.trim() || document.title.toLowerCase().includes(query.trim().toLowerCase())) ??
    null

  return (
    <Screen>
      <main className="safe-x flex flex-1 flex-col overflow-y-auto px-page py-6">
        <h1 className="text-2xl font-semibold tracking-tight">知識庫</h1>
        <p className="mt-2 text-sm leading-6 text-gpt-muted">
          這裡只瀏覽文件。想找內容，
          <Link to={ROUTES.chat} className="font-medium text-primary">
            到 AI 對話裡問
          </Link>
          ，agent 會在回答時替你檢索。
        </p>

        <div className="relative mt-6">
          <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-gpt-muted" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="搜尋文件標題"
            aria-label="搜尋文件標題"
            className="w-full rounded-full border border-gpt-line bg-gpt-canvas py-2.5 pl-9 pr-9 text-sm outline-none transition placeholder:text-gpt-muted focus:border-gpt-muted"
          />
          {query ? (
            <button
              type="button"
              aria-label="清除搜尋"
              onClick={() => setQuery('')}
              className="absolute right-2 top-1/2 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-full text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink"
            >
              <X size={13} />
            </button>
          ) : null}
        </div>

        <Card className="mt-section">
          <CardTitle>全部文件{documents !== null ? `（${documents.length} 篇）` : ''}</CardTitle>
          {documents === null && !documentsError ? (
            <div className="mt-3 flex flex-col gap-2.5" aria-label="載入中">
              <span className="h-4 w-2/3 animate-pulse rounded bg-gpt-hover" />
              <span className="h-4 w-1/2 animate-pulse rounded bg-gpt-hover" />
              <span className="h-4 w-3/5 animate-pulse rounded bg-gpt-hover" />
            </div>
          ) : null}
          {documentsError ? <p className="mt-3 text-sm leading-6 text-danger">{documentsError}</p> : null}
          {documents !== null && documents.length === 0 ? (
            <p className="mt-3 text-sm leading-6 text-gpt-muted">
              這個站點尚無知識文件，請管理員在後台上傳。
            </p>
          ) : null}
          {documents !== null && documents.length > 0 && visibleDocuments !== null && visibleDocuments.length === 0 ? (
            <p className="mt-3 text-sm leading-6 text-gpt-muted">找不到符合「{query.trim()}」的文件。</p>
          ) : null}
          {visibleDocuments !== null && visibleDocuments.length > 0 ? (
            <ul className="mt-2 flex flex-col">
              {visibleDocuments.map((document) => {
                const detail = details[document.id]
                const open = openDocumentId === document.id
                return (
                  <li key={document.id} className="border-b border-border last:border-b-0">
                    <button
                      type="button"
                      onClick={() => void toggleDocument(document)}
                      aria-expanded={open}
                      className="flex w-full items-center justify-between gap-3 rounded-lg px-2 py-3 text-left transition-colors hover:bg-gpt-hover"
                    >
                      <span className="min-w-0 truncate text-sm font-medium">{document.title}</span>
                      <ChevronDown
                        size={16}
                        className={
                          open
                            ? 'shrink-0 rotate-180 text-gpt-muted transition-transform'
                            : 'shrink-0 text-gpt-muted transition-transform'
                        }
                      />
                    </button>
                    {open ? (
                      <div className="px-2 pb-4">
                        {detail === 'loading' || detail === undefined ? (
                          <div className="flex flex-col gap-2 pt-1" aria-label="載入中">
                            <span className="h-4 w-full animate-pulse rounded bg-gpt-hover" />
                            <span className="h-4 w-5/6 animate-pulse rounded bg-gpt-hover" />
                            <span className="h-4 w-2/3 animate-pulse rounded bg-gpt-hover" />
                          </div>
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
      </main>
    </Screen>
  )
}
