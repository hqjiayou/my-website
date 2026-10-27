import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

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

        <Card className="mt-section">
          <CardTitle>全部文件{documents !== null ? `（${documents.length} 篇）` : ''}</CardTitle>
          {documents === null && !documentsError ? (
            <p className="mt-3 text-sm text-gpt-muted">載入中…</p>
          ) : null}
          {documentsError ? <p className="mt-3 text-sm leading-6 text-danger">{documentsError}</p> : null}
          {documents !== null && documents.length === 0 ? (
            <p className="mt-3 text-sm leading-6 text-gpt-muted">
              這個站點尚無知識文件，請管理員在後台上傳。
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
      </main>
    </Screen>
  )
}
