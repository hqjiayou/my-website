import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowDown, ArrowUp, Check, Copy, Ellipsis, FileText, ImagePlus, Mic, Pencil, Plus, RotateCcw, Share2, Square, SquarePen, Trash, Unlink, X } from 'lucide-react'

import { siteAiApi, sseErrorText } from '@/api/siteAiApi'
import { MarkdownText } from '@/components/markdown'
import { useSidebarExtra } from '@/components/sidebarExtra'
import { resolvePortalSiteId } from '@/config/env'
import { ROUTES } from '@/router/config/routePaths'
import { useAuthStore } from '@/stores/authStore'
import type { SiteAiCitation, SiteAiConversation, SiteAiDocument, SiteAiMessage } from '@/types/api'
import { formatSimilarityPercent } from '@/utils/formatSimilarity'
import { errorMessage } from '@/utils/http/apiError'

import { ConversationSheet } from './conversationSheet'
import { ProcessTimeline, type ProcessStep } from './processTimeline'
import { useKeyboardInset } from './useKeyboardInset'
import { useSpeechInput } from './useSpeechInput'

const MAX_MESSAGE_CHARS = 16_000
const MAX_IMAGES = 3
const MAX_IMAGE_EDGE = 1600
const MAX_IMAGE_BYTES = 2 * 1024 * 1024
const MAX_DOCUMENTS = 3
const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024
const DOCUMENT_EXTENSIONS = ['pdf', 'docx', 'xlsx', 'pptx', 'txt', 'md']

interface ChatImage {
  mediaType: string
  data: string
}

interface PendingImage extends ChatImage {
  id: string
}

interface PendingDocument extends SiteAiDocument {
  id: string
  sizeBytes: number
}

/** 訊息裡的文件引用（只有檔名，用於芯片顯示；原文不回傳）。 */
interface ChatDocumentRef {
  fileName: string
}

const SUGGESTIONS = ['這個站點提供哪些服務？', '幫我整理預約需要的資訊', '知識庫裡有什麼相關內容？']

/** provider 狀態的人話文案：原始枚舉只作未知值兜底，不直接糊給用戶。 */
const PROVIDER_STATUS_TEXT: Record<string, string> = {
  EXTERNAL_DEPENDENCY_BLOCKED: 'AI 服務暫時不可用（外部服務受阻，請稍後再試）',
  BLOCKED: 'AI 服務尚未就緒，請稍後再試',
}

interface ChatTurn {
  id: string
  role: string
  content: string
  images?: ChatImage[]
  documents?: ChatDocumentRef[]
  pending?: boolean
  providerStatus?: string
  citations?: SiteAiCitation[]
  /** 本轮的过程步骤（思考/工具）。只在当次会话内保留，刷新后服务端不回传。 */
  process?: ProcessStep[]
}

interface StoredLoadOptions {
  silent?: boolean
  citations?: SiteAiCitation[]
  providerStatus?: string
  process?: ProcessStep[]
}

function conversationPath(id: string) {
  return `${ROUTES.chat}/${id}`
}

/** 路徑上的會話 ID 必須是伺服器的正整數主鍵。 */
function readRouteConversationId(value: string | undefined): string | null {
  if (!value || !/^[1-9]\d*$/.test(value)) return null
  return value
}

/** 历史过程摘要 → 时间线步骤：只有工具步（无思考文本）；搜索摘要按序挂到搜索工具步。 */
function storedProcessSteps(messageId: string, process: SiteAiMessage['process']): ProcessStep[] {
  if (!process) return []
  const steps: ProcessStep[] = []
  let searchIndex = 0
  for (const [index, tool] of (process.tools ?? []).entries()) {
    const isSearch = tool.name === 'search_site_knowledge'
    const search = isSearch ? process.searches?.[searchIndex] : undefined
    if (isSearch) searchIndex += 1
    steps.push({
      kind: 'tool',
      id: `hist-${messageId}-${index}`,
      callId: `hist-${messageId}-${index}`,
      name: tool.name,
      status: tool.ok ? 'ok' : 'failed',
      summary: search ? { query: search.query, hitCount: search.hitCount, sources: [] } : undefined,
    })
  }
  return steps
}

function toStoredTurns(
  items: SiteAiMessage[],
  extra?: Pick<StoredLoadOptions, 'citations' | 'providerStatus' | 'process'>,
): ChatTurn[] {
  let lastAssistant = -1
  const turns: ChatTurn[] = items.map((item, index) => {
    if (item.role === 'ASSISTANT') lastAssistant = index
    const storedSteps = item.role === 'ASSISTANT' ? storedProcessSteps(String(item.id), item.process) : []
    const imageAttachments = item.attachments?.filter(
      (attachment) => attachment.kind !== 'DOCUMENT' && attachment.data,
    )
    const documentAttachments = item.attachments?.filter((attachment) => attachment.kind === 'DOCUMENT')
    return {
      id: String(item.id),
      role: item.role,
      content: item.content,
      images: imageAttachments?.length
        ? imageAttachments.map((attachment) => ({ mediaType: attachment.mediaType, data: attachment.data ?? '' }))
        : undefined,
      documents: documentAttachments?.length
        ? documentAttachments.map((attachment) => ({ fileName: attachment.fileName ?? '文件' }))
        : undefined,
      citations: item.role === 'ASSISTANT' && item.citations?.length ? item.citations : undefined,
      process: storedSteps.length ? storedSteps : undefined,
    }
  })
  // 即時輪的 extra 只覆蓋最後一條 ASSISTANT；沒有 extra citations 時保留該條已存的引用。
  // process 只存在即時輪：靜默重載回存檔訊息時把它接回去，免得時間線在 DONE 後消失。
  if (
    lastAssistant >= 0 &&
    extra &&
    (extra.citations?.length || extra.providerStatus !== undefined || extra.process?.length)
  ) {
    turns[lastAssistant] = {
      ...turns[lastAssistant],
      citations: extra.citations?.length ? extra.citations : turns[lastAssistant].citations,
      providerStatus: extra.providerStatus,
      process: extra.process?.length ? extra.process : turns[lastAssistant].process,
    }
  }
  return turns
}

interface SendFailure {
  message: string
  retryText: string
  retryImages: ChatImage[]
  retryDocuments: SiteAiDocument[]
  userId: string
  assistantId: string
}

export function ChatPage() {
  const navigate = useNavigate()
  const routeConversationId = useParams().conversationId
  const routeId = readRouteConversationId(routeConversationId)
  const site = useMemo(() => resolvePortalSiteId(useAuthStore.getState().site?.siteId), [])
  const displayName = useAuthStore((state) => state.user?.displayName || state.user?.username || '')
  const { setExtra, closeDrawer } = useSidebarExtra()
  useKeyboardInset()

  const voiceBaseRef = useRef('')
  const speech = useSpeechInput({
    onSessionText: (text) => setDraft(voiceBaseRef.current + text),
    onError: (message) => setAttachError(message),
  })

  function toggleVoice() {
    if (speech.listening) {
      speech.stop()
      return
    }
    const current = draftRef.current?.value ?? draft
    voiceBaseRef.current = current && !/\s$/.test(current) ? `${current} ` : current
    setAttachError(null)
    speech.start()
  }

  const [conversations, setConversations] = useState<SiteAiConversation[]>([])
  const [listLoading, setListLoading] = useState(site.ok)
  const [listError, setListError] = useState<string | null>(null)
  const [activeId, setActiveId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatTurn[]>([])
  const [historyLoading, setHistoryLoading] = useState(routeId != null)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [pending, setPending] = useState<PendingImage[]>([])
  const [pendingDocs, setPendingDocs] = useState<PendingDocument[]>([])
  const [attachError, setAttachError] = useState<string | null>(null)
  const [compressing, setCompressing] = useState(false)
  const [sending, setSending] = useState(false)
  const [failure, setFailure] = useState<SendFailure | null>(null)
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const [copyErrorId, setCopyErrorId] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editDraft, setEditDraft] = useState('')
  const [attachMenuOpen, setAttachMenuOpen] = useState(false)
  const [lightboxSrc, setLightboxSrc] = useState<string | null>(null)
  const [share, setShare] = useState<{ conversationId: string; token: string } | null>(null)
  const [headerMenuOpen, setHeaderMenuOpen] = useState(false)
  const [headerConfirm, setHeaderConfirm] = useState(false)
  const [headerNotice, setHeaderNotice] = useState<string | null>(null)

  const bottomRef = useRef<HTMLDivElement | null>(null)
  const scrollRef = useRef<HTMLDivElement | null>(null)
  const nearBottomRef = useRef(true)
  const [showScrollDown, setShowScrollDown] = useState(false)
  const draftRef = useRef<HTMLTextAreaElement | null>(null)
  const fileRef = useRef<HTMLInputElement | null>(null)
  const docFileRef = useRef<HTMLInputElement | null>(null)
  const compressingRef = useRef(false)
  const abortRef = useRef<AbortController | null>(null)
  const generation = useRef(0)
  const sendingRef = useRef(false)

  const freshChat = !routeId && !sending
  const visibleMessages = freshChat ? [] : messages
  const visibleHistoryError = freshChat ? null : historyError
  const selectedId = freshChat ? null : (routeId ?? activeId)
  const conversationRef = useRef<string | null>(null)
  const loadToken = useRef(0)

  useLayoutEffect(() => {
    if (sendingRef.current) return
    conversationRef.current = routeId
    if (!routeId) loadToken.current += 1
  }, [routeId])

  const loadConversations = useCallback(async (siteId: string, isCurrent: () => boolean = () => true) => {
    setListLoading(true)
    setListError(null)
    try {
      const items = await siteAiApi.listConversations(siteId)
      if (!isCurrent()) return
      setConversations(items)
    } catch (error) {
      if (!isCurrent()) return
      setListError(errorMessage(error))
    } finally {
      if (isCurrent()) setListLoading(false)
    }
  }, [])

  const loadStoredMessages = useCallback(
    (id: string, options?: StoredLoadOptions) => {
      if (!site.ok) return Promise.resolve()
      const token = ++loadToken.current
      conversationRef.current = id
      setActiveId(id)
      if (!options?.silent) {
        setMessages([])
        setHistoryLoading(true)
        setHistoryError(null)
      }
      return siteAiApi
        .listMessages(site.siteId, id)
        .then((items) => {
          if (loadToken.current !== token) return
          if (items.length === 0 && options?.silent) return
          setMessages(toStoredTurns(items, options))
          setHistoryError(null)
        })
        .catch((error: unknown) => {
          if (loadToken.current !== token || options?.silent) return
          setHistoryError(errorMessage(error))
          setMessages([])
        })
        .finally(() => {
          if (loadToken.current !== token || options?.silent) return
          setHistoryLoading(false)
        })
    },
    [site],
  )

  useEffect(() => {
    if (!routeConversationId || routeId) return
    navigate(ROUTES.chat, { replace: true })
  }, [navigate, routeConversationId, routeId])

  useEffect(() => {
    if (!site.ok || !routeId || sendingRef.current) return
    void loadStoredMessages(routeId)
  }, [loadStoredMessages, routeId, site])

  useEffect(() => {
    if (!site.ok) return
    let current = true
    siteAiApi.listConversations(site.siteId).then(
      (items) => {
        if (!current) return
        setConversations(items)
        setListLoading(false)
      },
      (error: unknown) => {
        if (!current) return
        setListError(errorMessage(error))
        setListLoading(false)
      },
    )
    return () => {
      current = false
    }
  }, [site])

  useEffect(() => {
    const activeGeneration = generation
    const activeAbort = abortRef
    return () => {
      activeGeneration.current += 1
      activeAbort.current?.abort()
    }
  }, [])

  // 只有視口貼著底部時才自動跟隨新訊息；用戶上翻看舊內容時不硬拽回去（GPT 行為）。
  useEffect(() => {
    if (nearBottomRef.current) bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages, sending, failure])

  function onScrollArea() {
    const el = scrollRef.current
    if (!el) return
    const distance = el.scrollHeight - el.scrollTop - el.clientHeight
    nearBottomRef.current = distance < 160
    setShowScrollDown(distance > 320)
  }

  function scrollToBottom() {
    const el = scrollRef.current
    if (!el) return
    nearBottomRef.current = true
    setShowScrollDown(false)
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
  }

  useEffect(() => {
    if (!attachMenuOpen) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setAttachMenuOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [attachMenuOpen])

  useLayoutEffect(() => {
    const node = draftRef.current
    if (!node) return
    node.style.height = '0px'
    node.style.height = `${Math.min(node.scrollHeight, 160)}px`
  }, [draft])

  async function copyReply(id: string, content: string) {
    try {
      await navigator.clipboard.writeText(content)
      setCopyErrorId(null)
      setCopiedId(id)
      window.setTimeout(() => {
        setCopiedId((current) => (current === id ? null : current))
      }, 1500)
    } catch {
      setCopiedId(null)
      setCopyErrorId(id)
    }
  }

  function startEdit(turn: ChatTurn) {
    if (sendingRef.current) return
    setFailure(null)
    setEditingId(turn.id)
    setEditDraft(turn.content)
  }

  function cancelEdit() {
    setEditingId(null)
    setEditDraft('')
  }

  /** 編輯重發：截斷該條及其後的所有訊息，以新文字重新送出（圖片沿用原附件；
   *  文件只有檔名存檔、原文不回傳，無法重傳，編輯框下有提示）。 */
  /** 已落庫的輪次先在後端真截斷（從該 USER 輪起刪），成功才本地截斷＋重送；失敗中止並提示。本地輪次直接回 true。 */
  async function truncateStoredTurn(userTurn: ChatTurn): Promise<boolean> {
    if (!site.ok || !selectedId || !/^\d+$/.test(userTurn.id)) return true
    try {
      await siteAiApi.truncateConversation(site.siteId, selectedId, userTurn.id)
      return true
    } catch {
      setAttachError('刪除舊訊息失敗，請重試')
      return false
    }
  }

  async function submitEdit(turn: ChatTurn) {
    const text = editDraft.trim()
    if (!text || sendingRef.current) return
    if (!(await truncateStoredTurn(turn))) return
    const images = turn.images ?? []
    setEditingId(null)
    setEditDraft('')
    setMessages((current) => {
      const index = current.findIndex((item) => item.id === turn.id)
      return index >= 0 ? current.slice(0, index) : current
    })
    void send(text, images, [])
  }

  /** 重新生成：以該回覆前最近的一條用戶訊息重發，視覺上替換這一問一答（同失敗重試機制）。 */
  async function regenerate(assistantTurn: ChatTurn) {
    if (sendingRef.current) return
    const index = messages.findIndex((item) => item.id === assistantTurn.id)
    if (index < 0) return
    let userTurn: ChatTurn | undefined
    for (let i = index - 1; i >= 0; i -= 1) {
      if (messages[i].role === 'USER') {
        userTurn = messages[i]
        break
      }
    }
    if (!userTurn) return
    if (!(await truncateStoredTurn(userTurn))) return
    setFailure(null)
    void send(userTurn.content, userTurn.images ?? [], [], {
      userId: userTurn.id,
      assistantId: assistantTurn.id,
    })
  }

  function stopGenerating() {
    abortRef.current?.abort()
  }

  function leaveStreaming() {
    generation.current += 1
    sendingRef.current = false
    abortRef.current?.abort()
    abortRef.current = null
    setSending(false)
  }

  function startNewChat() {
    leaveStreaming()
    setEditingId(null)
    loadToken.current += 1
    conversationRef.current = null
    setActiveId(null)
    setMessages([])
    setHistoryError(null)
    setHistoryLoading(false)
    setFailure(null)
    setPending([])
    setAttachError(null)
    closeDrawer()
    if (routeId) navigate(ROUTES.chat)
  }

  function openConversation(conversationId: string) {
    if (!site.ok) return
    closeDrawer()
    if (conversationId === routeId) return
    leaveStreaming()
    setEditingId(null)
    setFailure(null)
    setPending([])
    setAttachError(null)
    setHistoryLoading(true)
    navigate(conversationPath(conversationId))
  }

  async function onPickFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (files.length === 0 || sendingRef.current || compressingRef.current) return
    if (pending.length >= MAX_IMAGES) {
      setAttachError('最多附加 3 張圖片')
      return
    }
    compressingRef.current = true
    setCompressing(true)
    setAttachError(null)
    const room = MAX_IMAGES - pending.length
    const added: PendingImage[] = []
    let error: string | null = null
    try {
      for (const file of files) {
        if (added.length >= room) {
          error = '最多附加 3 張圖片'
          break
        }
        try {
          const image = await compressImageFile(file)
          if (!image) {
            if (!error) error = '這張圖片壓縮後仍超過 2MB，無法附加'
            continue
          }
          added.push({ ...image, id: crypto.randomUUID() })
        } catch {
          if (!error) error = '無法處理這張圖片'
        }
      }
    } finally {
      compressingRef.current = false
      setCompressing(false)
    }
    if (added.length > 0) setPending((current) => [...current, ...added].slice(0, MAX_IMAGES))
    if (error) setAttachError(error)
  }

  async function onPickDocuments(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (files.length === 0 || sendingRef.current) return
    if (pendingDocs.length >= MAX_DOCUMENTS) {
      setAttachError('最多附加 3 份文件')
      return
    }
    setAttachError(null)
    const room = MAX_DOCUMENTS - pendingDocs.length
    const added: PendingDocument[] = []
    let error: string | null = null
    for (const file of files) {
      if (added.length >= room) {
        error = '最多附加 3 份文件'
        break
      }
      const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
      if (!DOCUMENT_EXTENSIONS.includes(extension)) {
        if (!error) error = '只支援 PDF、Word、Excel、PPT、txt、md 文件'
        continue
      }
      if (file.size > MAX_DOCUMENT_BYTES) {
        if (!error) error = '文件超過 10MB，無法附加'
        continue
      }
      try {
        const data = await blobToBase64(file)
        added.push({
          id: crypto.randomUUID(),
          fileName: file.name,
          mediaType: file.type || 'application/octet-stream',
          data,
          sizeBytes: file.size,
        })
      } catch {
        if (!error) error = '無法讀取這份文件'
      }
    }
    if (added.length > 0) setPendingDocs((current) => [...current, ...added].slice(0, MAX_DOCUMENTS))
    if (error) setAttachError(error)
  }

  async function send(
    text: string,
    images: ChatImage[] = [],
    documents: SiteAiDocument[] = [],
    replace?: { userId: string; assistantId: string },
  ) {
    if (!site.ok || sendingRef.current || historyLoading || compressingRef.current) return
    const trimmed = text.trim()
    if (!trimmed && images.length === 0 && documents.length === 0) return
    if (trimmed.length > MAX_MESSAGE_CHARS) {
      setFailure({
        message: `訊息超過 ${MAX_MESSAGE_CHARS} 字`,
        retryText: trimmed,
        retryImages: images,
        retryDocuments: documents,
        userId: replace?.userId ?? '',
        assistantId: replace?.assistantId ?? '',
      })
      return
    }
    if (images.length > 0) setPending([])
    if (documents.length > 0) setPendingDocs([])
    setAttachError(null)
    speech.stop()

    const gen = generation.current
    const conversationId = conversationRef.current
    const userId = `local-user-${crypto.randomUUID()}`
    const assistantId = `local-assistant-${crypto.randomUUID()}`
    const controller = new AbortController()
    abortRef.current = controller
    sendingRef.current = true
    setDraft('')
    setFailure(null)
    setSending(true)
    if (!conversationId) setActiveId(null)
    setMessages((current) => {
      const sameThread = current
      const base = conversationId
        ? replace
          ? sameThread.filter((item) => item.id !== replace.userId && item.id !== replace.assistantId)
          : sameThread
        : []
      return [
        ...base,
        {
          id: userId,
          role: 'USER',
          content: trimmed,
          images: images.length ? images : undefined,
          documents: documents.length ? documents.map((doc) => ({ fileName: doc.fileName })) : undefined,
        },
        { id: assistantId, role: 'ASSISTANT', content: '', pending: true },
      ]
    })

    const markStopped = () => {
      setMessages((current) =>
        current.flatMap((item) => {
          if (item.id !== assistantId) return [item]
          if (!item.content) return []
          return [{ ...item, pending: false }]
        }),
      )
    }

    const markFailed = (message: string) => {
      setFailure({ message, retryText: trimmed, retryImages: images, retryDocuments: documents, userId, assistantId })
      setMessages((current) => current.map((item) => (item.id === assistantId ? { ...item, pending: false } : item)))
    }

    let finished = false
    let liveCitations: SiteAiCitation[] | undefined
    let liveProviderStatus: string | undefined
    // 过程步骤在闭包里同步累积，同时镜像进 assistant 轮渲染；DONE 后随静默重载带回。
    const liveSteps: ProcessStep[] = []
    const syncSteps = () => {
      const snapshot = [...liveSteps]
      setMessages((current) =>
        current.map((item) => (item.id === assistantId ? { ...item, process: snapshot } : item)),
      )
    }
    try {
      await siteAiApi.streamChat(
        site.siteId,
        {
          message: trimmed,
          ...(conversationId ? { conversationId } : {}),
          ...(images.length
            ? { attachments: images.map((image) => ({ mediaType: image.mediaType, data: image.data })) }
            : {}),
          ...(documents.length
            ? {
                documents: documents.map((doc) => ({
                  fileName: doc.fileName,
                  mediaType: doc.mediaType,
                  data: doc.data,
                })),
              }
            : {}),
        },
        (event) => {
          if (generation.current !== gen) return
          if (event.event === 'TEXT_DELTA') {
            setMessages((current) =>
              current.map((item) =>
                item.id === assistantId ? { ...item, content: item.content + event.delta, pending: true } : item,
              ),
            )
          } else if (event.event === 'THINKING') {
            const last = liveSteps[liveSteps.length - 1]
            if (last?.kind === 'thinking') {
              liveSteps[liveSteps.length - 1] = { ...last, text: last.text + event.delta }
            } else {
              liveSteps.push({ kind: 'thinking', id: crypto.randomUUID(), text: event.delta })
            }
            syncSteps()
          } else if (event.event === 'TOOL_START') {
            liveSteps.push({
              kind: 'tool',
              id: crypto.randomUUID(),
              callId: event.toolCallId,
              name: event.toolName,
              status: 'running',
            })
            syncSteps()
          } else if (event.event === 'TOOL_RESULT') {
            const index = liveSteps.findIndex(
              (step) => step.kind === 'tool' && step.callId === event.toolCallId,
            )
            if (index >= 0) {
              const step = liveSteps[index]
              if (step.kind === 'tool') {
                liveSteps[index] = {
                  ...step,
                  status: event.ok ? 'ok' : 'failed',
                  summary: event.summary ?? step.summary,
                }
              }
            }
            syncSteps()
          } else if (event.event === 'DONE') {
            finished = true
            liveCitations = event.citations
            liveProviderStatus = event.providerStatus
            setMessages((current) =>
              current.map((item) =>
                item.id === assistantId
                  ? {
                      ...item,
                      content: event.reply || item.content,
                      pending: false,
                      providerStatus: event.providerStatus,
                      citations: event.citations,
                    }
                  : item,
              ),
            )
          } else if (event.event === 'CONVERSATION') {
            if (!event.conversationId) return
            conversationRef.current = event.conversationId
            setActiveId(event.conversationId)
            navigate(conversationPath(event.conversationId), { replace: true })
          } else if (event.event === 'ERROR') {
            finished = true
            markFailed(sseErrorText(event.errorCode))
          }
        },
        controller.signal,
      )
      if (generation.current !== gen) return
      if (controller.signal.aborted) {
        markStopped()
        return
      }
      if (!finished) {
        markFailed('連線已結束，但沒有收到完成事件')
      } else if (conversationRef.current) {
        await loadStoredMessages(conversationRef.current, {
          silent: true,
          citations: liveCitations,
          providerStatus: liveProviderStatus,
          process: liveSteps.length ? [...liveSteps] : undefined,
        })
      }
    } catch (error) {
      if (generation.current !== gen) return
      if (controller.signal.aborted) {
        markStopped()
        return
      }
      markFailed(errorMessage(error))
    } finally {
      if (generation.current === gen) {
        sendingRef.current = false
        setSending(false)
        void loadConversations(site.siteId)
      }
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (sending) {
      stopGenerating()
      return
    }
    void send(draft, pending, pendingDocs)
  }

  async function renameConversation(id: string, title: string) {
    if (!site.ok) return
    const updated = await siteAiApi.renameConversation(site.siteId, id, title)
    setConversations((current) =>
      current.map((item) => (String(item.id) === id ? { ...item, title: updated.title } : item)),
    )
  }

  async function removeConversation(id: string) {
    if (!site.ok) return
    if (sending && id === selectedId) {
      throw new Error('正在產生回覆，請稍後再刪除')
    }
    await siteAiApi.deleteConversation(site.siteId, id)
    setConversations((current) => current.filter((item) => String(item.id) !== id))
    if (id === selectedId) startNewChat()
  }

  const shareToken = share && selectedId && share.conversationId === selectedId ? share.token : null

  useEffect(() => {
    if (!site.ok || !selectedId) return
    let cancelled = false
    siteAiApi
      .getShare(site.siteId, selectedId)
      .then((result) => {
        if (!cancelled) {
          setShare(result ? { conversationId: selectedId, token: result.token } : null)
        }
      })
      .catch(() => {
        // 查詢失敗時不顯示分享狀態，使用者仍可從選單重新建立
      })
    return () => {
      cancelled = true
    }
  }, [selectedId, site])

  async function shareConversation() {
    if (!site.ok || !selectedId) return
    setHeaderMenuOpen(false)
    try {
      const created = await siteAiApi.createShare(site.siteId, selectedId)
      setShare({ conversationId: selectedId, token: created.token })
      let copied = true
      try {
        await navigator.clipboard.writeText(`${window.location.origin}${ROUTES.share}/${created.token}`)
      } catch {
        copied = false
      }
      setHeaderNotice(
        copied
          ? shareToken
            ? '分享快照已更新，連結已複製'
            : '分享連結已複製'
          : '分享已建立，但複製失敗，請再試一次',
      )
    } catch (error) {
      setHeaderNotice(errorMessage(error))
    }
    window.setTimeout(() => setHeaderNotice(null), 3000)
  }

  async function revokeShareLink() {
    if (!site.ok || !selectedId) return
    setHeaderMenuOpen(false)
    try {
      await siteAiApi.revokeShare(site.siteId, selectedId)
      setShare(null)
      setHeaderNotice('分享已撤銷')
    } catch (error) {
      setHeaderNotice(errorMessage(error))
    }
    window.setTimeout(() => setHeaderNotice(null), 3000)
  }

  async function deleteFromHeader() {
    if (!selectedId) return
    setHeaderMenuOpen(false)
    setHeaderConfirm(false)
    try {
      await removeConversation(selectedId)
    } catch (error) {
      setHeaderNotice(errorMessage(error))
      window.setTimeout(() => setHeaderNotice(null), 3000)
    }
  }

  const activeTitle =
    conversations.find((item) => String(item.id) === selectedId)?.title?.trim() || (selectedId ? '對話' : '新對話')

  // 会话列表注入全站侧边栏（唯一的导航）；离开聊天页时清空。
  useEffect(() => {
    if (!site.ok) return
    setExtra(
      <ConversationSheet
        open
        items={conversations}
        loading={listLoading}
        error={listError}
        activeId={selectedId}
        deleteLockedId={sending ? selectedId : null}
        onClose={closeDrawer}
        onRefresh={() => void loadConversations(site.siteId)}
        onCreate={startNewChat}
        onOpen={(id) => void openConversation(id)}
        onRename={renameConversation}
        onDelete={removeConversation}
      />,
    )
    // 回调每轮渲染都是新引用，只按数据依赖刷新注入内容
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [setExtra, site, conversations, listLoading, listError, selectedId, sending])

  useEffect(() => {
    return () => setExtra(null)
  }, [setExtra])

  const composer = (
    <form onSubmit={onSubmit} className='w-full'>
      <div className="mx-auto w-full max-w-3xl">
        {attachError ? <p className="mb-2 text-center text-xs text-danger">{attachError}</p> : null}
        {pending.length > 0 ? (
          <ul className="mb-2 flex flex-wrap gap-2">
            {pending.map((image) => (
              <li key={image.id} className="relative">
                <button type="button" onClick={() => setLightboxSrc(imageSrc(image))} aria-label="放大圖片" className="cursor-zoom-in">
                  <img src={imageSrc(image)} alt="" className="h-16 w-16 rounded-xl object-cover" />
                </button>
                <button
                  type="button"
                  aria-label="移除圖片"
                  disabled={sending}
                  onClick={() => {
                    setPending((current) => current.filter((item) => item.id !== image.id))
                    setAttachError(null)
                  }}
                  className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-gpt-ink text-gpt-canvas disabled:opacity-30"
                >
                  <X size={11} />
                </button>
              </li>
            ))}
          </ul>
        ) : null}
        {pendingDocs.length > 0 ? (
          <ul className="mb-2 flex flex-wrap gap-2">
            {pendingDocs.map((doc) => (
              <li
                key={doc.id}
                className="relative flex max-w-full items-center gap-1.5 rounded-full border border-gpt-line px-3 py-1.5 pr-7 text-xs text-gpt-muted"
              >
                <FileText size={14} className="shrink-0" />
                <span className="truncate">{doc.fileName}</span>
                <span className="shrink-0">{formatFileSize(doc.sizeBytes)}</span>
                <button
                  type="button"
                  aria-label="移除文件"
                  disabled={sending}
                  onClick={() => {
                    setPendingDocs((current) => current.filter((item) => item.id !== doc.id))
                    setAttachError(null)
                  }}
                  className="absolute right-1 top-1/2 flex h-5 w-5 -translate-y-1/2 items-center justify-center rounded-full bg-gpt-ink text-gpt-canvas disabled:opacity-30"
                >
                  <X size={11} />
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      <div className="relative flex w-full items-end gap-2 rounded-[28px] border border-gpt-line bg-gpt-canvas px-3 py-2 shadow-composer transition focus-within:border-gpt-muted">
        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          multiple
          className="sr-only"
          disabled={sending || compressing}
          onChange={(event) => void onPickFiles(event)}
        />
        <input
          ref={docFileRef}
          type="file"
          accept=".pdf,.docx,.xlsx,.pptx,.txt,.md"
          multiple
          className="sr-only"
          disabled={sending}
          onChange={(event) => void onPickDocuments(event)}
        />
        {attachMenuOpen ? (
          <button
            type="button"
            aria-hidden="true"
            tabIndex={-1}
            className="fixed inset-0 z-10 cursor-default"
            onClick={() => setAttachMenuOpen(false)}
          />
        ) : null}
        <div className="relative z-20 mb-0.5 shrink-0">
          <button
            type="button"
            aria-label="附加內容"
            aria-expanded={attachMenuOpen}
            disabled={sending || compressing}
            onClick={() => setAttachMenuOpen((open) => !open)}
            className="flex h-9 w-9 items-center justify-center rounded-full text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink disabled:opacity-30"
          >
            <Plus size={21} strokeWidth={1.9} />
          </button>
          {attachMenuOpen ? (
            <div className="absolute bottom-full left-0 z-20 mb-2 w-60 rounded-2xl border border-gpt-line bg-gpt-canvas p-1.5 shadow-xl">
              <button
                type="button"
                disabled={sending || compressing}
                onClick={() => {
                  setAttachMenuOpen(false)
                  fileRef.current?.click()
                }}
                className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm hover:bg-gpt-hover disabled:opacity-40"
              >
                <ImagePlus size={18} className="shrink-0 text-gpt-muted" />
                附加圖片
                <span className="ml-auto text-xs text-gpt-muted">最多 3 張</span>
              </button>
              <button
                type="button"
                disabled={sending}
                onClick={() => {
                  setAttachMenuOpen(false)
                  docFileRef.current?.click()
                }}
                className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm hover:bg-gpt-hover disabled:opacity-40"
              >
                <FileText size={18} className="shrink-0 text-gpt-muted" />
                附加文件
                <span className="ml-auto text-xs text-gpt-muted">PDF、Word 等</span>
              </button>
            </div>
          ) : null}
        </div>
        <label className="sr-only" htmlFor="chat-draft">
          訊息
        </label>
        <textarea
          id="chat-draft"
          ref={draftRef}
          rows={1}
          value={draft}
          enterKeyHint="send"
          placeholder="傳訊息給 Agent Portal"
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault()
              if (!sending && !compressing) void send(draft, pending, pendingDocs)
            }
          }}
          className="max-h-40 min-h-10 flex-1 resize-none bg-transparent px-2 py-2 text-base outline-none placeholder:text-gpt-muted"
        />
        {speech.supported ? (
          <button
            type="button"
            onClick={toggleVoice}
            disabled={sending}
            aria-label={speech.listening ? '停止語音輸入' : '語音輸入'}
            title={speech.listening ? '停止語音輸入' : '語音輸入'}
            className={
              speech.listening
                ? 'mb-0.5 flex h-9 w-9 shrink-0 animate-pulse items-center justify-center rounded-full bg-danger-soft text-danger disabled:opacity-30'
                : 'mb-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-gpt-muted transition hover:bg-gpt-hover hover:text-gpt-ink active:scale-95 disabled:opacity-30'
            }
          >
            <Mic size={18} />
          </button>
        ) : null}
        <button
          type="submit"
          disabled={
            compressing ||
            (historyLoading && !freshChat) ||
            (!sending && !draft.trim() && pending.length === 0 && pendingDocs.length === 0)
          }
          aria-label={sending ? '停止生成' : '傳送'}
          className="mb-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gpt-ink text-gpt-canvas transition active:scale-95 disabled:opacity-30"
        >
          {sending ? <Square size={12} fill="currentColor" /> : <ArrowUp size={18} strokeWidth={2.4} />}
        </button>
      </div>
      </div>
      <p className="mx-auto mt-2 max-w-3xl text-center text-xs text-gpt-muted">
        回覆成功後寫入這個帳號。內容由 AI 產生，請自行確認。
      </p>
    </form>
  )

  if (!site.ok) {
    return (
      <div className="flex min-h-dvh items-center justify-center bg-gpt-canvas px-6 text-gpt-ink">
        <p className="max-w-sm text-sm leading-6 text-danger">{site.message}</p>
      </div>
    )
  }

  const showEmpty = visibleMessages.length === 0 && !visibleHistoryError && !(historyLoading && !freshChat)
  const lastAssistantId = (() => {
    for (let i = visibleMessages.length - 1; i >= 0; i -= 1) {
      if (visibleMessages[i].role === 'ASSISTANT') return visibleMessages[i].id
    }
    return null
  })()

  return (
    <section
      className="flex min-w-0 w-full flex-1 flex-col bg-gpt-canvas"
      style={{ height: 'calc(100% - var(--keyboard-inset, 0px))' }}
    >
      <div className="relative flex h-11 shrink-0 items-center gap-1 bg-gpt-canvas/80 px-4 backdrop-blur-sm">
        <h1 className="min-w-0 flex-1 truncate text-sm font-medium">{activeTitle}</h1>
        {headerNotice ? (
          <span className="shrink-0 text-xs text-danger">{headerNotice}</span>
        ) : selectedId ? (
          <span className="shrink-0 text-xs text-gpt-muted">已儲存</span>
        ) : null}
        <button
          type="button"
          onClick={startNewChat}
          aria-label="新對話"
          title="新對話"
          className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-gpt-muted transition hover:bg-gpt-hover hover:text-gpt-ink active:scale-95"
        >
          <SquarePen size={17} />
        </button>
        {selectedId ? (
          <div className="relative shrink-0">
            <button
              type="button"
              aria-label="對話操作"
              aria-expanded={headerMenuOpen}
              onClick={() => {
                setHeaderMenuOpen((open) => !open)
                setHeaderConfirm(false)
              }}
              className="flex h-9 w-9 items-center justify-center rounded-lg text-gpt-muted transition hover:bg-gpt-hover hover:text-gpt-ink active:scale-95"
            >
              <Ellipsis size={18} />
            </button>
            {headerMenuOpen ? (
              <>
                <button
                  type="button"
                  aria-hidden="true"
                  tabIndex={-1}
                  className="fixed inset-0 z-10 cursor-default"
                  onClick={() => setHeaderMenuOpen(false)}
                />
                <div className="absolute right-0 top-full z-20 mt-1 w-52 rounded-2xl border border-gpt-line bg-gpt-canvas p-1.5 shadow-xl">
                  {headerConfirm ? (
                    <div className="px-2 py-1.5">
                      <p className="text-sm">確定刪除此對話？</p>
                      <div className="mt-2 flex gap-2">
                        <button
                          type="button"
                          onClick={() => void deleteFromHeader()}
                          className="h-8 rounded-full bg-danger px-3 text-xs font-medium text-white"
                        >
                          刪除
                        </button>
                        <button
                          type="button"
                          onClick={() => setHeaderConfirm(false)}
                          className="h-8 rounded-full px-3 text-xs hover:bg-gpt-hover"
                        >
                          取消
                        </button>
                      </div>
                    </div>
                  ) : (
                    <>
                      {selectedId && messages.length > 0 ? (
                        <button
                          type="button"
                          onClick={() => void shareConversation()}
                          className="flex w-full items-center gap-2.5 rounded-xl px-2.5 py-2 text-left text-sm hover:bg-gpt-hover"
                        >
                          <Share2 size={15} />
                          {shareToken ? '更新分享並複製連結' : '分享此對話'}
                        </button>
                      ) : null}
                      {shareToken ? (
                        <button
                          type="button"
                          onClick={() => void revokeShareLink()}
                          className="flex w-full items-center gap-2.5 rounded-xl px-2.5 py-2 text-left text-sm hover:bg-gpt-hover"
                        >
                          <Unlink size={15} />
                          撤銷分享
                        </button>
                      ) : null}
                      <button
                        type="button"
                        onClick={() => setHeaderConfirm(true)}
                        className="flex w-full items-center gap-2.5 rounded-xl px-2.5 py-2 text-left text-sm text-danger hover:bg-gpt-hover"
                      >
                        <Trash size={15} />
                        刪除此對話
                      </button>
                    </>
                  )}
                </div>
              </>
            ) : null}
          </div>
        ) : null}
      </div>

      <div className="relative flex min-h-0 flex-1 flex-col">
      <div ref={scrollRef} onScroll={onScrollArea} className="min-h-0 flex-1 overflow-y-auto" aria-live="polite">
        <div className="mx-auto flex min-h-full w-full max-w-3xl flex-col px-4 py-6">
          {historyLoading && !freshChat ? <p className="text-sm text-gpt-muted">正在載入訊息…</p> : null}
          {showEmpty ? (
            <div className="flex flex-1 flex-col items-center justify-center py-10">
              <h1 className="text-center text-[26px] font-semibold leading-snug tracking-tight md:text-[32px]">
                {displayName ? `${displayName}，有什麼可以幫忙的？` : '有什麼可以幫忙的？'}
              </h1>
              <div className="mt-9 w-full">{composer}</div>
              <div className="mt-5 flex flex-wrap justify-center gap-2">
                {SUGGESTIONS.map((prompt) => (
                  <button
                    key={prompt}
                    type="button"
                    disabled={sending}
                    onClick={() => void send(prompt)}
                    className="rounded-full border border-gpt-line px-4 py-2 text-sm text-gpt-muted transition hover:bg-gpt-hover hover:text-gpt-ink disabled:opacity-60"
                  >
                    {prompt}
                  </button>
                ))}
              </div>
            </div>
          ) : null}
          {visibleHistoryError ? (
            <div>
              <p className="text-sm text-danger">{visibleHistoryError}</p>
              {routeId ? (
                <button
                  type="button"
                  onClick={() => {
                    if (routeId) void loadStoredMessages(routeId)
                  }}
                  className="mt-3 h-9 rounded-lg border border-gpt-line px-3 text-sm"
                >
                  重試
                </button>
              ) : null}
            </div>
          ) : null}
          <ul className="flex flex-col gap-6">
            {visibleMessages.map((message) => {
              const hasCitations =
                !message.pending && message.role === 'ASSISTANT' && (message.citations?.length ?? 0) > 0
              const hasImages = (message.images?.length ?? 0) > 0
              const hasDocuments = (message.documents?.length ?? 0) > 0
              const hasProcess = message.role === 'ASSISTANT' && (message.process?.length ?? 0) > 0
              const visible =
                Boolean(message.content) ||
                message.pending ||
                message.providerStatus !== undefined ||
                hasCitations ||
                hasImages ||
                hasDocuments ||
                hasProcess
              if (!visible) return null
              const isUser = message.role === 'USER'
              if (isUser && editingId === message.id) {
                return (
                  <li key={message.id} className="flex flex-col items-end">
                    <div className="w-full rounded-3xl border border-gpt-line bg-gpt-canvas px-3 py-2.5 shadow-composer">
                      <textarea
                        autoFocus
                        value={editDraft}
                        onChange={(event) => setEditDraft(event.target.value)}
                        onKeyDown={(event) => {
                          if (event.key === 'Escape') cancelEdit()
                        }}
                        rows={Math.min(6, Math.max(1, editDraft.split('\n').length))}
                        className="max-h-40 w-full resize-none bg-transparent px-1 py-1 text-[15px] leading-7 outline-none"
                        aria-label="編輯訊息"
                      />
                      {(message.documents?.length ?? 0) > 0 ? (
                        <p className="px-1 pb-1 text-xs text-gpt-muted">
                          原附加文件只有檔名留存，重新傳送時不會帶上。
                        </p>
                      ) : null}
                      <div className="flex justify-end gap-2 pt-1">
                        <button
                          type="button"
                          onClick={cancelEdit}
                          className="h-9 rounded-full px-3.5 text-sm hover:bg-gpt-hover"
                        >
                          取消
                        </button>
                        <button
                          type="button"
                          disabled={!editDraft.trim() || sending}
                          onClick={() => void submitEdit(message)}
                          className="h-9 rounded-full bg-gpt-ink px-3.5 text-sm text-gpt-canvas disabled:opacity-40"
                        >
                          重新傳送
                        </button>
                      </div>
                    </div>
                  </li>
                )
              }
              return (
                <li
                  key={message.id}
                  className={isUser ? 'msg-in group flex flex-col items-end' : 'msg-in group flex flex-col items-start'}
                >
                  <div className={isUser ? 'max-w-[85%] rounded-3xl bg-gpt-user px-4 py-2.5' : 'max-w-full'}>
                    {hasProcess ? (
                      <ProcessTimeline steps={message.process ?? []} live={!!message.pending} />
                    ) : null}
                    {hasImages ? (
                      <div className={message.content ? 'mb-2 flex flex-wrap gap-2' : 'flex flex-wrap gap-2'}>
                        {message.images?.map((image, index) => (
                          <button
                            key={`${message.id}-${index}`}
                            type="button"
                            onClick={() => setLightboxSrc(imageSrc(image))}
                            aria-label="放大圖片"
                            className="cursor-zoom-in"
                          >
                            <img
                              src={imageSrc(image)}
                              alt="附件圖片"
                              className="max-h-48 max-w-full rounded-xl"
                            />
                          </button>
                        ))}
                      </div>
                    ) : null}
                    {hasDocuments ? (
                      <ul className={message.content ? 'mb-2 flex flex-wrap gap-2' : 'flex flex-wrap gap-2'}>
                        {message.documents?.map((doc, index) => (
                          <li
                            key={`${message.id}-doc-${index}`}
                            className="flex max-w-full items-center gap-1.5 rounded-full bg-gpt-sidebar px-2.5 py-1 text-xs text-gpt-muted"
                          >
                            <FileText size={14} className="shrink-0" />
                            <span className="truncate">{doc.fileName}</span>
                          </li>
                        ))}
                      </ul>
                    ) : null}
                    {message.content || message.pending ? (
                      isUser ? (
                        <p className="whitespace-pre-wrap break-words text-base leading-7">
                          {message.content}
                        </p>
                      ) : (
                        <div>
                          {message.pending && !message.content && !hasProcess ? (
                            <p className="animate-pulse text-base text-gpt-muted">正在思考…</p>
                          ) : (
                            <>
                              <MarkdownText>{message.content}</MarkdownText>
                              {message.pending ? (
                                <span className="ml-1 inline-block h-2.5 w-2.5 animate-pulse rounded-full bg-gpt-ink align-middle" />
                              ) : null}
                            </>
                          )}
                        </div>
                      )
                    ) : null}
                    {hasCitations ? (
                      <ul className="mt-3 flex flex-wrap gap-1.5">
                        {message.citations?.map((citation, index) => (
                          <li
                            key={`${citation.sourceId}-${citation.chunkIndex}-${index}`}
                            title={citation.sourceId}
                            className="max-w-full rounded-full bg-gpt-sidebar px-2.5 py-1 text-xs text-gpt-muted"
                          >
                            來源 {index + 1} · {formatSimilarityPercent(citation.score)}
                          </li>
                        ))}
                      </ul>
                    ) : null}
                    {message.providerStatus !== undefined && message.providerStatus !== 'LIVE' ? (
                      <p className="mt-2 text-xs text-danger">
                        {message.providerStatus
                          ? (PROVIDER_STATUS_TEXT[message.providerStatus] ??
                            `AI 服務狀態：${message.providerStatus}`)
                          : '尚未回傳 AI 服務狀態'}
                      </p>
                    ) : null}
                  </div>
                  {message.content && (isUser || !message.pending) ? (
                    <div className="mt-1 flex items-center gap-0.5 opacity-70 transition-opacity focus-within:opacity-100 md:opacity-0 md:group-hover:opacity-100">
                      <button
                        type="button"
                        onClick={() => void copyReply(message.id, message.content)}
                        aria-label="複製訊息"
                        title={copyErrorId === message.id ? '無法複製，請再試一次' : '複製'}
                        className="flex h-8 w-8 items-center justify-center rounded-lg text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink"
                      >
                        {copiedId === message.id ? <Check size={16} /> : <Copy size={16} />}
                      </button>
                      {isUser ? (
                        <button
                          type="button"
                          onClick={() => startEdit(message)}
                          disabled={sending}
                          aria-label="編輯訊息"
                          title="編輯"
                          className="flex h-8 w-8 items-center justify-center rounded-lg text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink disabled:opacity-40"
                        >
                          <Pencil size={16} />
                        </button>
                      ) : null}
                      {!isUser && message.id === lastAssistantId ? (
                        <button
                          type="button"
                          onClick={() => void regenerate(message)}
                          disabled={sending}
                          aria-label="重新生成回覆"
                          title="重新生成"
                          className="flex h-8 w-8 items-center justify-center rounded-lg text-gpt-muted hover:bg-gpt-hover hover:text-gpt-ink disabled:opacity-40"
                        >
                          <RotateCcw size={16} />
                        </button>
                      ) : null}
                    </div>
                  ) : null}
                </li>
              )
            })}
          </ul>
          {failure ? (
            <div className="mt-4 flex items-center justify-between gap-3 rounded-2xl border border-gpt-line px-3 py-2" role="alert">
              <p className="text-sm text-danger">{failure.message}</p>
              <button
                type="button"
                onClick={() =>
                  void send(failure.retryText, failure.retryImages, failure.retryDocuments, {
                    userId: failure.userId,
                    assistantId: failure.assistantId,
                  })
                }
                disabled={sending}
                className="h-9 shrink-0 rounded-full border border-gpt-line px-3 text-sm disabled:opacity-60"
              >
                重試
              </button>
            </div>
          ) : null}
          <div ref={bottomRef} />
        </div>
      </div>
      {showScrollDown && !showEmpty ? (
        <button
          type="button"
          onClick={scrollToBottom}
          aria-label="回到底部"
          title="回到底部"
          className="absolute bottom-4 left-1/2 z-10 flex h-9 w-9 -translate-x-1/2 items-center justify-center rounded-full border border-gpt-line bg-gpt-canvas text-gpt-muted shadow-md transition hover:text-gpt-ink"
        >
          <ArrowDown size={17} />
        </button>
      ) : null}
      </div>

      {!showEmpty ? <div className="safe-x shrink-0 px-4 pb-4">{composer}</div> : null}
      {lightboxSrc ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 p-4"
          role="dialog"
          aria-label="圖片預覽"
          onClick={() => setLightboxSrc(null)}
        >
          <img src={lightboxSrc} alt="附件圖片大圖" className="max-h-full max-w-full rounded-xl object-contain" />
          <button
            type="button"
            aria-label="關閉預覽"
            className="absolute right-4 top-4 flex h-10 w-10 items-center justify-center rounded-full bg-white/10 text-white transition hover:bg-white/20"
          >
            <X size={19} />
          </button>
        </div>
      ) : null}
    </section>
  )
}

async function compressImageFile(file: File): Promise<ChatImage | null> {
  const bitmap = await createImageBitmap(file)
  try {
    const longest = Math.max(bitmap.width, bitmap.height)
    const scale = longest > MAX_IMAGE_EDGE ? MAX_IMAGE_EDGE / longest : 1
    const width = Math.max(1, Math.round(bitmap.width * scale))
    const height = Math.max(1, Math.round(bitmap.height * scale))
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) throw new Error('canvas')
    context.drawImage(bitmap, 0, 0, width, height)
    const keepPng = file.type === 'image/png' && hasTransparency(context, width, height)
    const blob = await new Promise<Blob | null>((resolve) => {
      canvas.toBlob((result) => resolve(result), keepPng ? 'image/png' : 'image/jpeg', keepPng ? undefined : 0.85)
    })
    if (!blob) throw new Error('blob')
    if (blob.size > MAX_IMAGE_BYTES) return null
    const data = await blobToBase64(blob)
    return { mediaType: blob.type || (keepPng ? 'image/png' : 'image/jpeg'), data }
  } finally {
    bitmap.close()
  }
}

function hasTransparency(context: CanvasRenderingContext2D, width: number, height: number): boolean {
  try {
    const pixels = context.getImageData(0, 0, width, height).data
    for (let index = 3; index < pixels.length; index += 4) {
      if (pixels[index] < 255) return true
    }
    return false
  } catch {
    return false
  }
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const result = String(reader.result ?? '')
      const comma = result.indexOf(',')
      resolve(comma >= 0 ? result.slice(comma + 1) : result)
    }
    reader.onerror = () => reject(reader.error ?? new Error('read'))
    reader.readAsDataURL(blob)
  })
}

function imageSrc(image: ChatImage): string {
  return `data:${image.mediaType};base64,${image.data}`
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

