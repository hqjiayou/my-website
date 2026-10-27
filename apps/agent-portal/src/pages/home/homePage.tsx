import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { siteAiApi } from '@/api/siteAiApi'
import { Card, CardTitle } from '@/components/card'
import { Screen } from '@/components/screen'
import { readAccessTokenSiteId, resolvePortalSiteId } from '@/config/env'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import type { SiteAiConversation, SiteKnowledgeDocumentListItem, SiteUserMemory } from '@/types/api'

const RECENT_CONVERSATIONS = 3
const RECENT_DOCUMENTS = 2

function formatWhen(value: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('zh-TW', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function HomePage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const sessionSite = useAuthStore((state) => state.site)
  const accessToken = useAuthStore((state) => state.accessToken)
  const site = resolvePortalSiteId(sessionSite?.siteId)
  const tokenSiteId = readAccessTokenSiteId(accessToken)

  const [conversations, setConversations] = useState<SiteAiConversation[] | null>(null)
  const [documents, setDocuments] = useState<SiteKnowledgeDocumentListItem[] | null>(null)
  const [memories, setMemories] = useState<SiteUserMemory[] | null>(null)
  const [modulesError, setModulesError] = useState(false)

  useEffect(() => {
    if (!site.ok) return
    let cancelled = false
    void Promise.allSettled([
      siteAiApi.listConversations(site.siteId),
      siteAiApi.listKnowledgeDocuments(site.siteId),
      siteAiApi.listMemories(site.siteId),
    ]).then(([conv, docs, mem]) => {
      if (cancelled) return
      if (conv.status === 'fulfilled') setConversations(conv.value)
      if (docs.status === 'fulfilled') setDocuments(docs.value)
      if (mem.status === 'fulfilled') setMemories(mem.value)
      if (conv.status === 'rejected' && docs.status === 'rejected' && mem.status === 'rejected') {
        setModulesError(true)
      }
    })
    return () => {
      cancelled = true
    }
    // site.siteId 在登入期間不變，只在掛載時載入一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [site.ok])

  const displayName = user?.displayName || user?.username || '已登入'
  const siteMismatch = site.ok && tokenSiteId !== null && tokenSiteId !== site.siteId
  const siteIdFromSession = sessionSite != null && site.ok && String(sessionSite.siteId) === site.siteId
  const siteName = sessionSite?.siteName || 'Agent Portal'

  return (
    <Screen>
      <main className="safe-x flex flex-1 flex-col overflow-y-auto px-page py-6">
        <h1 className="text-3xl font-semibold tracking-tight">{siteName}</h1>
        <p className="mt-2 text-sm leading-6 text-gpt-muted">問 AI、查知識庫，或接續之前的對話。</p>

        <button
          type="button"
          disabled={!site.ok}
          onClick={() => navigate(ROUTES.chat)}
          className="mt-section rounded-panel bg-primary p-4 text-left text-primary-foreground shadow-raised disabled:opacity-60"
        >
          <span className="block text-lg font-semibold">開始 AI 對話</span>
          <span className="mt-1 block text-sm leading-6 opacity-80">串流回覆，可傳圖，並會參考這個站點的知識庫</span>
        </button>

        {site.ok ? (
          <div className="mt-section flex flex-col gap-stack">
            <Card>
              <CardTitle
                action={
                  <button
                    type="button"
                    onClick={() => navigate(ROUTES.chat)}
                    className="text-sm font-medium text-primary"
                  >
                    查看全部
                  </button>
                }
              >
                最近對話
              </CardTitle>
              {conversations === null ? (
                <p className="mt-3 text-sm text-gpt-muted">{modulesError ? '載入失敗，請稍後重試' : '載入中…'}</p>
              ) : conversations.length === 0 ? (
                <p className="mt-3 text-sm leading-6 text-gpt-muted">尚無對話，點上方開始第一場。</p>
              ) : (
                <ul className="mt-2 flex flex-col">
                  {conversations.slice(0, RECENT_CONVERSATIONS).map((conversation) => (
                    <li key={conversation.id}>
                      <button
                        type="button"
                        onClick={() => navigate(`${ROUTES.chat}/${conversation.id}`)}
                        className="flex w-full items-center justify-between gap-3 rounded-lg px-2 py-2.5 text-left hover:bg-gpt-hover"
                      >
                        <span className="min-w-0 truncate text-sm font-medium">
                          {conversation.title?.trim() || '未命名對話'}
                        </span>
                        <span className="shrink-0 text-xs text-gpt-muted">
                          {formatWhen(conversation.lastMessageAt ?? conversation.createdAt)}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card>
              <CardTitle
                action={
                  <button
                    type="button"
                    onClick={() => navigate(ROUTES.knowledge)}
                    className="text-sm font-medium text-primary"
                  >
                    瀏覽全部
                  </button>
                }
              >
                知識庫{documents !== null ? `（${documents.length} 篇）` : ''}
              </CardTitle>
              {documents === null ? (
                <p className="mt-3 text-sm text-gpt-muted">{modulesError ? '載入失敗，請稍後重試' : '載入中…'}</p>
              ) : documents.length === 0 ? (
                <p className="mt-3 text-sm leading-6 text-gpt-muted">這個站點尚無知識文件。</p>
              ) : (
                <ul className="mt-2 flex flex-col">
                  {documents.slice(0, RECENT_DOCUMENTS).map((document) => (
                    <li key={document.id}>
                      <button
                        type="button"
                        onClick={() => navigate(ROUTES.knowledge)}
                        className="flex w-full items-center justify-between gap-3 rounded-lg px-2 py-2.5 text-left hover:bg-gpt-hover"
                      >
                        <span className="min-w-0 truncate text-sm font-medium">{document.title}</span>
                        <span className="shrink-0 text-xs text-gpt-muted">{formatWhen(document.updatedAt)}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            <Card>
              <CardTitle
                action={
                  <button
                    type="button"
                    onClick={() => navigate(ROUTES.memories)}
                    className="text-sm font-medium text-primary"
                  >
                    管理
                  </button>
                }
              >
                我的記憶{memories !== null ? `（${memories.length} 條）` : ''}
              </CardTitle>
              {memories === null ? (
                <p className="mt-3 text-sm text-gpt-muted">{modulesError ? '載入失敗，請稍後重試' : '載入中…'}</p>
              ) : memories.length === 0 ? (
                <p className="mt-3 text-sm leading-6 text-gpt-muted">尚無記住的內容，對話裡提到的偏好會記在這裡。</p>
              ) : (
                <p className="mt-3 line-clamp-2 text-sm leading-6 text-gpt-muted">{memories[0].content}</p>
              )}
            </Card>
          </div>
        ) : null}

        <Card className="mt-section">
          <CardTitle>帳號</CardTitle>
          {user?.avatarUrl ? (
            <img
              src={user.avatarUrl}
              alt=""
              className="mt-3 h-12 w-12 rounded-pill object-cover"
              onError={(event) => {
                event.currentTarget.hidden = true
              }}
            />
          ) : null}
          <dl className="mt-3 flex flex-col gap-2 text-sm">
            <div>
              <dt className="text-xs text-gpt-muted">顯示名稱</dt>
              <dd className="mt-0.5">{displayName}</dd>
            </div>
            {user?.username ? (
              <div>
                <dt className="text-xs text-gpt-muted">使用者名稱</dt>
                <dd className="mt-0.5 break-all">{user.username}</dd>
              </div>
            ) : null}
            {user?.userCode ? (
              <div>
                <dt className="text-xs text-gpt-muted">使用者代碼</dt>
                <dd className="mt-0.5 break-all">{user.userCode}</dd>
              </div>
            ) : null}
          </dl>

          <h3 className="mt-4 text-sm font-medium">目前站點</h3>
          {sessionSite ? (
            <dl className="mt-3 flex flex-col gap-2 text-sm">
              <div>
                <dt className="text-xs text-gpt-muted">站點名稱</dt>
                <dd className="mt-0.5">{sessionSite.siteName}</dd>
              </div>
              <div>
                <dt className="text-xs text-gpt-muted">站點代碼</dt>
                <dd className="mt-0.5 break-all">{sessionSite.siteCode}</dd>
              </div>
            </dl>
          ) : site.ok ? (
            <p className="mt-1 text-lg font-medium">站點 ID {site.siteId}</p>
          ) : (
            <p className="mt-1 text-sm leading-6 text-danger">{site.message}</p>
          )}
          {sessionSite && !site.ok ? (
            <p className="mt-2 text-sm leading-6 text-danger">{site.message}</p>
          ) : null}
          {siteMismatch ? (
            <p className="mt-2 text-sm leading-6 text-danger">
              {siteIdFromSession
                ? '權杖裡的 site_id 與目前站點不一致。站點 AI 會依路徑上的站點 ID 驗證，存取會被拒絕。'
                : '權杖裡的 site_id 與 VITE_PORTAL_SITE_ID 不一致。站點 AI 會依路徑上的站點 ID 驗證，存取會被拒絕。'}
            </p>
          ) : null}
        </Card>

      </main>
    </Screen>
  )
}
