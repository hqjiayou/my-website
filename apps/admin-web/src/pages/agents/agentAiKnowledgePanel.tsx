import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, CircleAlert, FileUp, LoaderCircle, X } from 'lucide-react'
import { useCallback, useMemo, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { DataTable, FormDialog, Spinner, type DataTableColumn } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminFieldClass,
  adminFormActionsClass,
  adminPrimaryButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { AI_KB_STATUS, AI_KB_STATUS_VALUES, type AiKnowledgeListItem, type AiKnowledgeStatus } from '@/types/ai'
import { PermissionGate } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

const KB_UPLOAD_MAX_BYTES = 10 * 1024 * 1024
// 与后端 AdminAiKnowledgeController.SUPPORTED_EXTENSIONS 保持一致（增删格式时三处同改：
// 控制器集合、AdminAiKnowledgeContentConverter 的 switch、本列表）
const KB_UPLOAD_EXTENSIONS = [
  'txt', 'md', 'markdown', 'text', 'rst', 'adoc', 'asciidoc', 'org', 'tex',
  'csv', 'tsv', 'json', 'xml', 'yaml', 'yml', 'toml', 'ini', 'log',
  'jsonl', 'ndjson', 'properties', 'conf', 'cfg', 'env', 'diff', 'patch',
  'py', 'java', 'js', 'ts', 'tsx', 'jsx', 'css', 'scss', 'sql', 'sh',
  'bash', 'zsh', 'ps1', 'mjs', 'cjs',
  'go', 'rs', 'c', 'h', 'cpp', 'hpp', 'cs', 'rb', 'php', 'swift', 'kt', 'kts',
  'scala', 'pl', 'r', 'lua', 'vue', 'svelte', 'dart', 'groovy', 'gradle',
  'hs', 'ex', 'exs', 'tf', 'proto',
  'pdf', 'docx', 'docm', 'doc', 'html', 'htm', 'xlsx', 'xlsm', 'xls',
  'pptx', 'pptm', 'ppt', 'epub', 'odt', 'ods', 'odp', 'rtf',
  'png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp', 'heic', 'heif',
]
const KB_UPLOAD_ACCEPT = KB_UPLOAD_EXTENSIONS.map((ext) => `.${ext}`).join(',')

interface EditorState {
  mode: 'create' | 'edit'
  id?: number
  version?: number
  title: string
  content: string
  status: (typeof AI_KB_STATUS)[keyof typeof AI_KB_STATUS]
  unevaluatedFormulaCount?: number
}

type UploadItemStatus = 'queued' | 'uploading' | 'success' | 'index_failed' | 'rejected' | 'failed'

interface UploadItem {
  id: string
  file: File
  title: string
  fieldError: 'extension' | 'size' | null
  status: UploadItemStatus
  detail?: string
}

function knowledgeFileExtension(filename: string) {
  const dot = filename.lastIndexOf('.')
  if (dot < 0 || dot === filename.length - 1) return ''
  return filename.slice(dot + 1).toLowerCase()
}

function titleFromFilename(filename: string) {
  const dot = filename.lastIndexOf('.')
  if (dot <= 0) return ''
  return filename.slice(0, dot).trim()
}

function formatFileSize(bytes: number) {
  if (!Number.isFinite(bytes) || bytes < 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function uploadFieldError(file: File): UploadItem['fieldError'] {
  const extension = knowledgeFileExtension(file.name)
  // zip 是批量导入容器，走 upload-archive 端点，不在单文档格式集合内
  if (extension !== 'zip' && !KB_UPLOAD_EXTENSIONS.includes(extension)) return 'extension'
  if (file.size > KB_UPLOAD_MAX_BYTES) return 'size'
  return null
}

function toUploadItems(files: File[]): UploadItem[] {
  return files.map((file) => {
    const fieldError = uploadFieldError(file)
    return {
      id: `${file.name}-${file.size}-${file.lastModified}-${Math.random().toString(36).slice(2, 8)}`,
      file,
      title: titleFromFilename(file.name),
      fieldError,
      status: fieldError ? 'rejected' : 'queued',
      detail: fieldError ?? undefined,
    }
  })
}

export function AgentAiKnowledgePanel({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<{ pageNum: number; pageSize: number; status: AiKnowledgeStatus | '' }>({
    pageNum: 1,
    pageSize: 20,
    status: '',
  })
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [uploadOpen, setUploadOpen] = useState(false)
  const [uploadItems, setUploadItems] = useState<UploadItem[]>([])
  const [uploadBusy, setUploadBusy] = useState(false)
  const uploadInputRef = useRef<HTMLInputElement>(null)
  // 上传转写模式（Issue #208）：缺省判定与试聊同源——打开上传框时不带 mode 查
  // preview/model，服务端按「有 CLI 默认入口先 CLI、否则 KEY」判定；用户手动切换后不再覆盖。
  const [uploadMode, setUploadMode] = useState<'CLI' | 'KEY'>('CLI')
  const [uploadModeChosen, setUploadModeChosen] = useState(false)
  const uploadModelQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'preview', 'model', 'default'],
    queryFn: () => aiApi.getPreviewModel(agentId),
    enabled: uploadOpen,
  })
  const [adoptedUploadDefault, setAdoptedUploadDefault] = useState<{
    agentId: number
    mode: 'CLI' | 'KEY'
  } | null>(null)
  if (
    uploadOpen &&
    !uploadModeChosen &&
    uploadModelQuery.data &&
    (adoptedUploadDefault == null ||
      adoptedUploadDefault.agentId !== agentId ||
      adoptedUploadDefault.mode !== uploadModelQuery.data.mode)
  ) {
    setAdoptedUploadDefault({ agentId, mode: uploadModelQuery.data.mode })
    setUploadMode(uploadModelQuery.data.mode)
  }

  const chooseUploadMode = (next: 'CLI' | 'KEY') => {
    setUploadMode(next)
    setUploadModeChosen(true)
  }
  const [selectedBaseId, setSelectedBaseId] = useState<number | null>(null)
  const [newBaseName, setNewBaseName] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const [deletingDoc, setDeletingDoc] = useState<AiKnowledgeListItem | null>(null)
  const [deletingBase, setDeletingBase] = useState<{ id: number; name: string } | null>(null)

  const basesQuery = useQuery({
    queryKey: ['sites', agentId, 'ai', 'knowledge-bases'],
    queryFn: () => aiApi.listKnowledgeBases(agentId),
  })
  const bases = basesQuery.data ?? []
  const selectedBase = selectedBaseId == null ? null : (bases.find((base) => base.id === selectedBaseId) ?? null)
  const viewingShared = selectedBase != null && !selectedBase.owned
  const writableBaseId = selectedBase != null && selectedBase.owned ? selectedBase.id : null

  const createBaseMutation = useMutation({
    mutationFn: (name: string) => aiApi.createKnowledgeBase(agentId, name),
    onSuccess: (base) => {
      toast.success(t('aiWorkspace.knowledge.baseCreated'))
      setNewBaseName(null)
      setSelectedBaseId(base.id)
      void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai', 'knowledge-bases'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const deleteBaseMutation = useMutation({
    mutationFn: (baseId: number) => aiApi.deleteKnowledgeBase(agentId, baseId),
    onSuccess: () => {
      setDeletingBase(null)
      toast.success(t('aiWorkspace.knowledge.baseDeleted'))
      setSelectedBaseId(null)
      void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai', 'knowledge-bases'] })
      void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai', 'knowledge'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', agentId, 'ai', 'knowledge', filters, selectedBaseId],
    queryFn: () =>
      aiApi.listKnowledgeDocuments(agentId, {
        pageNum: filters.pageNum,
        pageSize: filters.pageSize,
        status: filters.status || undefined,
        baseId: selectedBaseId ?? undefined,
      }),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      aiApi.createKnowledgeDocument(agentId, {
        title: editor!.title.trim(),
        content: editor!.content,
        baseId: writableBaseId,
      }),
    onSuccess: () => {
      toast.success(t('aiWorkspace.knowledge.created'))
      setEditor(null)
      void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai', 'knowledge'] })
    },
  })

  const updateMutation = useMutation({
    mutationFn: () =>
      aiApi.updateKnowledgeDocument(agentId, editor!.id!, {
        title: editor!.title.trim(),
        content: editor!.content,
        status: editor!.status,
        version: editor!.version ?? 0,
      }),
    onSuccess: () => {
      toast.success(t('aiWorkspace.knowledge.updated'))
      setEditor(null)
      void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai', 'knowledge'] })
    },
  })

  const reindexMutation = useMutation({
    mutationFn: (id: number) => aiApi.reindexKnowledgeDocument(agentId, id),
    onSuccess: (detail) => {
      if (detail.indexStatus === 'INDEXED') toast.success(t('aiWorkspace.knowledge.reindexed'))
      else toast.warning(t('aiWorkspace.knowledge.reindexFailed', { error: detail.indexError ?? '' }))
      void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai', 'knowledge'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => aiApi.deleteKnowledgeDocument(agentId, id),
    onSuccess: () => {
      setDeletingDoc(null)
      toast.success(t('aiWorkspace.knowledge.deleted'))
      void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai', 'knowledge'] })
    },
  })

  const queueUploadFiles = useCallback(
    (files: File[]) => {
      if (files.length === 0 || viewingShared || uploadBusy) return
      setUploadItems((prev) => [...prev, ...toUploadItems(files)])
      setUploadOpen(true)
    },
    [uploadBusy, viewingShared],
  )

  const openUpload = useCallback(() => {
    if (viewingShared) return
    setUploadOpen(true)
  }, [viewingShared])

  const closeUpload = useCallback(() => {
    if (uploadBusy) return
    setUploadOpen(false)
    setUploadItems([])
    // 下次打开重新按服务端缺省判定采纳模式
    setUploadModeChosen(false)
  }, [uploadBusy])

  function onPickUploadFile(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    queueUploadFiles(files)
  }

  function removeUploadItem(id: string) {
    if (uploadBusy) return
    setUploadItems((prev) => prev.filter((item) => item.id !== id))
  }

  function patchUploadItem(id: string, patch: Partial<UploadItem>) {
    setUploadItems((prev) => prev.map((item) => (item.id === id ? { ...item, ...patch } : item)))
  }

  async function onUploadSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (uploadBusy || viewingShared) return
    const pending = uploadItems.filter((item) => item.status === 'queued')
    if (pending.length === 0) return

    setUploadBusy(true)
    let indexed = 0
    let indexFailed = 0
    let rejected = 0

    const paint = () => new Promise<void>((resolve) => window.setTimeout(resolve, 0))

    try {
      for (const item of pending) {
        patchUploadItem(item.id, { status: 'uploading', detail: undefined })
        await paint()
        const singleTitle =
          pending.length === 1 && knowledgeFileExtension(item.file.name) !== 'zip'
            ? item.title.trim() || undefined
            : undefined
        try {
          if (knowledgeFileExtension(item.file.name) === 'zip') {
            const result = await aiApi.uploadKnowledgeArchive(agentId, item.file, uploadMode, writableBaseId)
            let zipIndexed = 0
            let zipFailed = 0
            for (const detail of result.documents) {
              if (detail.indexStatus === 'FAILED') zipFailed++
              else zipIndexed++
            }
            const zipRejected = result.failures.length
            indexed += zipIndexed
            indexFailed += zipFailed
            rejected += zipRejected
            if (result.createdCount === 0 && result.failures.length === 0) {
              rejected++
              patchUploadItem(item.id, {
                status: 'failed',
                detail: t('aiWorkspace.knowledge.uploadStatus.emptyArchive'),
              })
            } else if (zipFailed > 0 || zipRejected > 0) {
              patchUploadItem(item.id, {
                status: zipIndexed > 0 ? 'index_failed' : 'failed',
                detail: t('aiWorkspace.knowledge.uploadStatus.archivePartial', {
                  indexed: zipIndexed,
                  indexFailed: zipFailed,
                  rejected: zipRejected,
                }),
              })
            } else {
              patchUploadItem(item.id, {
                status: 'success',
                detail: t('aiWorkspace.knowledge.uploadStatus.archiveOk', { count: zipIndexed }),
              })
            }
          } else {
            const detail = await aiApi.uploadKnowledgeDocument(
              agentId,
              item.file,
              uploadMode,
              singleTitle,
              writableBaseId,
            )
            if (detail.indexStatus === 'FAILED') {
              indexFailed++
              patchUploadItem(item.id, {
                status: 'index_failed',
                detail: detail.indexError || t('aiWorkspace.knowledge.uploadedIndexFailed'),
              })
            } else {
              indexed++
              patchUploadItem(item.id, {
                status: 'success',
                detail:
                  detail.indexStatus === 'INDEXED'
                    ? t('aiWorkspace.knowledge.uploadedIndexed')
                    : t('aiWorkspace.knowledge.uploaded'),
              })
            }
          }
        } catch (error) {
          rejected++
          patchUploadItem(item.id, {
            status: 'failed',
            detail: error instanceof Error ? error.message : t('aiWorkspace.knowledge.uploadStatus.failed'),
          })
        }
        await paint()
      }

      toast.success(t('aiWorkspace.knowledge.batchResult', { indexed, indexFailed, rejected }))
      void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai', 'knowledge'] })
    } finally {
      setUploadBusy(false)
    }
  }

  const queuedCount = uploadItems.filter((item) => item.status === 'queued').length
  const singleQueued =
    uploadItems.length === 1 &&
    uploadItems[0].status === 'queued' &&
    uploadItems[0].fieldError == null &&
    knowledgeFileExtension(uploadItems[0].file.name) !== 'zip'
      ? uploadItems[0]
      : null

  function uploadStatusLabel(status: UploadItemStatus) {
    return t(`aiWorkspace.knowledge.uploadStatus.${status}`)
  }

  function uploadRowClass(status: UploadItemStatus) {
    switch (status) {
      case 'success':
        return 'border-success/35 bg-success-muted/60'
      case 'index_failed':
      case 'rejected':
      case 'failed':
        return 'border-danger/35 bg-danger-muted/60'
      case 'uploading':
        return 'border-ring/40 bg-card-muted/50'
      default:
        return 'border-border bg-card'
    }
  }

  function uploadStatusClass(status: UploadItemStatus) {
    switch (status) {
      case 'success':
        return 'text-success-emphasis'
      case 'index_failed':
      case 'rejected':
      case 'failed':
        return 'text-danger-emphasis'
      case 'uploading':
        return 'text-muted-foreground'
      default:
        return 'text-muted-foreground'
    }
  }

  const openEditor = useCallback(async (id: number) => {
    const detail = await aiApi.getKnowledgeDocument(agentId, id)
    setEditor({
      mode: 'edit',
      id: detail.id,
      version: detail.version,
      title: detail.title,
      content: detail.content,
      status: detail.status,
      unevaluatedFormulaCount: detail.unevaluatedFormulaCount,
    })
  }, [agentId])

  const columns = useMemo<DataTableColumn<AiKnowledgeListItem>[]>(
    () => [
      {
        id: 'title',
        header: t('aiWorkspace.knowledge.columns.title'),
        cell: (item) => (
          <div>
            <div>{item.title}</div>
            {item.originalFilename ? (
              <div className="text-xs text-muted-foreground">
                {item.originalFilename}
                {item.sourceFormat ? ` · ${item.sourceFormat}` : ''}
                {item.fileSizeBytes != null ? ` · ${formatFileSize(item.fileSizeBytes)}` : ''}
              </div>
            ) : null}
          </div>
        ),
      },
      {
        id: 'status',
        header: t('aiWorkspace.knowledge.columns.status'),
        cell: (item) => t(`aiWorkspace.knowledge.status.${item.status}`, { defaultValue: item.status }),
      },
      {
        id: 'indexStatus',
        header: t('aiWorkspace.knowledge.columns.index'),
        cell: (item) => (
          <span>
            <span
              className={
                item.indexStatus === 'INDEXED'
                  ? 'text-success-emphasis'
                  : item.indexStatus === 'FAILED'
                    ? 'text-danger-emphasis'
                    : 'text-muted-foreground'
              }
              title={item.indexStatus === 'FAILED' ? (item.indexError ?? undefined) : undefined}
            >
              {t(`aiWorkspace.knowledge.indexStatus.${item.indexStatus}`, {
                defaultValue: item.indexStatus,
              })}
            </span>
            {item.indexStale ? (
              <span className="ml-2 rounded bg-warning/[0.12] px-1.5 py-0.5 text-xs font-medium text-warning-emphasis">
                {t('aiWorkspace.knowledge.indexStale')}
              </span>
            ) : null}
            {(item.unevaluatedFormulaCount ?? 0) > 0 ? (
              <span className="ml-2 rounded bg-warning/[0.12] px-1.5 py-0.5 text-xs font-medium text-warning-emphasis">
                {t('aiWorkspace.knowledge.unevaluatedFormulas')}
              </span>
            ) : null}
          </span>
        ),
      },
      {
        id: 'updatedAt',
        header: t('aiWorkspace.knowledge.columns.updatedAt'),
        cell: (item) => item.updatedAt,
      },
      {
        id: 'actions',
        header: t('aiWorkspace.knowledge.columns.actions'),
        cell: (item) => (
          <div className="flex gap-2">
            {item.owned ? (
              <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
                <button
                  type="button"
                  className="text-sm underline"
                  onClick={() => void openEditor(item.id)}
                >
                  {t('aiWorkspace.knowledge.edit')}
                </button>
              </PermissionGate>
            ) : (
              <span className="text-xs text-muted-foreground">{t('aiWorkspace.knowledge.sharedBadge')}</span>
            )}
            {item.hasOriginal ? (
              <button
                type="button"
                className="text-sm underline"
                onClick={() => {
                  void aiApi
                    .getKnowledgeDocumentFile(agentId, item.id)
                    .then((file) => window.open(file.url, '_blank', 'noopener'))
                    .catch((error: Error) => toast.error(error.message))
                }}
              >
                {t('aiWorkspace.knowledge.downloadOriginal')}
              </button>
            ) : null}
            {item.owned &&
            item.status === 'ACTIVE' &&
            (item.indexStatus !== 'INDEXED' || item.indexStale) ? (
              <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
                <button
                  type="button"
                  className="text-sm underline"
                  disabled={reindexMutation.isPending}
                  onClick={() => reindexMutation.mutate(item.id)}
                >
                  {t('aiWorkspace.knowledge.reindex')}
                </button>
              </PermissionGate>
            ) : null}
            {item.owned ? (
              <PermissionGate code={PERMISSION_CODES.aiKbDelete}>
                <button
                  type="button"
                  className="text-sm text-danger-emphasis underline"
                  onClick={() => setDeletingDoc(item)}
                >
                  {t('aiWorkspace.knowledge.delete')}
                </button>
              </PermissionGate>
            ) : null}
          </div>
        ),
      },
    ],
    [openEditor, reindexMutation, agentId, t],
  )

  const records = data?.records ?? []

  return (
    <div
      className={`flex h-full min-h-0 flex-col gap-3 ${dragging ? 'rounded-xl outline-2 outline-dashed outline-ring outline-offset-4' : ''}`}
      onDragOver={(event) => {
        event.preventDefault()
        setDragging(true)
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => {
        event.preventDefault()
        setDragging(false)
        if (viewingShared) return
        queueUploadFiles(Array.from(event.dataTransfer.files))
      }}
    >
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card-muted/50 px-3 py-2.5">
        <div className="flex min-w-0 flex-wrap items-center gap-2">
          <label className="flex items-center gap-2 text-sm">
            <span className="shrink-0 text-xs font-medium text-muted-foreground">
              {t('aiWorkspace.knowledge.baseLabel')}
            </span>
            <select
              className="rounded-lg border border-border bg-card px-2.5 py-1.5 text-sm"
              value={selectedBaseId == null ? '' : String(selectedBaseId)}
              onChange={(event) => {
                setSelectedBaseId(event.target.value === '' ? null : Number(event.target.value))
                setFilters((prev) => ({ ...prev, pageNum: 1 }))
              }}
            >
              <option value="">{t('aiWorkspace.knowledge.baseAll')}</option>
              {bases.map((base) => (
                <option key={base.id} value={base.id}>
                  {base.name}
                  {base.isDefault ? ` (${t('aiWorkspace.knowledge.baseDefault')})` : ''}
                  {base.owned
                    ? ''
                    : ` · ${t('aiWorkspace.knowledge.sharedBadge')}${
                        base.ownerAgentName ? ` · ${base.ownerAgentName}` : ''
                      }`}
                </option>
              ))}
            </select>
          </label>
          <select
            className="rounded-lg border border-border bg-card px-2.5 py-1.5 text-sm"
            value={filters.status}
            onChange={(event) =>
              setFilters((prev) => ({
                ...prev,
                pageNum: 1,
                status: event.target.value as AiKnowledgeStatus | '',
              }))
            }
          >
            <option value="">{t('aiWorkspace.knowledge.allStatus')}</option>
            {AI_KB_STATUS_VALUES.map((status) => (
              <option key={status} value={status}>
                {status}
              </option>
            ))}
          </select>
          {viewingShared ? (
            <span className="text-xs text-warning-emphasis">{t('aiWorkspace.knowledge.sharedReadOnly')}</span>
          ) : null}
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <PermissionGate code={PERMISSION_CODES.aiKbCreate}>
            {newBaseName == null ? (
              <button type="button" className={adminSecondaryButtonClass} onClick={() => setNewBaseName('')}>
                {t('aiWorkspace.knowledge.baseNew')}
              </button>
            ) : (
              <span className="flex items-center gap-1">
                <input
                  className="w-36 rounded-lg border border-border px-2 py-1.5 text-sm"
                  value={newBaseName}
                  onChange={(event) => setNewBaseName(event.target.value)}
                  placeholder={t('aiWorkspace.knowledge.baseNamePlaceholder')}
                />
                <button
                  type="button"
                  className={adminPrimaryButtonClass}
                  disabled={!newBaseName.trim() || createBaseMutation.isPending}
                  onClick={() => createBaseMutation.mutate(newBaseName.trim())}
                >
                  {createBaseMutation.isPending ? <Spinner /> : null}
                  {t('aiWorkspace.knowledge.baseCreate')}
                </button>
                <button type="button" className={adminSecondaryButtonClass} onClick={() => setNewBaseName(null)}>
                  {t('aiWorkspace.knowledge.baseCancel')}
                </button>
              </span>
            )}
            <button
              type="button"
              className={adminSecondaryButtonClass}
              disabled={uploadBusy || viewingShared}
              onClick={openUpload}
            >
              {t('aiWorkspace.knowledge.upload')}
            </button>
            <button
              type="button"
              className={adminPrimaryButtonClass}
              disabled={viewingShared}
              onClick={() =>
                setEditor({ mode: 'create', title: '', content: '', status: AI_KB_STATUS.ACTIVE })
              }
            >
              {t('aiWorkspace.knowledge.create')}
            </button>
          </PermissionGate>
          {selectedBase?.owned && !selectedBase.isDefault ? (
            <PermissionGate code={PERMISSION_CODES.aiKbDelete}>
              <button
                type="button"
                className="rounded-lg border border-danger/35 px-3 py-1.5 text-sm text-danger-emphasis disabled:opacity-50"
                disabled={deleteBaseMutation.isPending}
                onClick={() => setDeletingBase({ id: selectedBase.id, name: selectedBase.name })}
              >
                {t('aiWorkspace.knowledge.baseDelete')}
              </button>
            </PermissionGate>
          ) : null}
        </div>
      </div>

      <p className="text-xs text-muted-foreground">
        {uploadBusy ? t('aiWorkspace.knowledge.uploading') : t('aiWorkspace.knowledge.dropHint')}
      </p>

      <div className="min-h-0 flex-1 overflow-auto">
        <DataTable
          columns={columns}
          data={records}
          rowKey={(item) => item.id}
          isLoading={isPending}
          isError={isError}
          errorMessage={error instanceof Error ? error.message : undefined}
          emptyMessage={t('aiWorkspace.knowledge.empty')}
          onRetry={() => void refetch()}
          pagination={{
            page: filters.pageNum,
            pageSize: filters.pageSize,
            total: data?.total ?? 0,
            onPageChange: (page) => setFilters((prev) => ({ ...prev, pageNum: page })),
          }}
        />
      </div>

      {editor ? (
        <FormDialog
          title={
            editor.mode === 'create' ? t('aiWorkspace.knowledge.create') : t('aiWorkspace.knowledge.edit')
          }
          closeLabel={t('aiWorkspace.knowledge.cancel')}
          onClose={() => setEditor(null)}
          placement="end"
        >
          <form
            className="flex min-h-full flex-col gap-4"
            onSubmit={(event) => {
              event.preventDefault()
              if (editor.mode === 'create') createMutation.mutate()
              else updateMutation.mutate()
            }}
          >
            {(editor.unevaluatedFormulaCount ?? 0) > 0 ? (
              <p className="rounded-lg bg-warning-muted px-3 py-2 text-sm text-warning-emphasis">
                {t('aiWorkspace.knowledge.unevaluatedFormulas')}
              </p>
            ) : null}
            <label className="block space-y-1.5">
              <span className="text-xs font-medium text-muted-foreground">
                {t('aiWorkspace.knowledge.titleLabel')}
              </span>
              <input
                className={adminFieldClass}
                value={editor.title}
                onChange={(event) => setEditor({ ...editor, title: event.target.value })}
                placeholder={t('aiWorkspace.knowledge.titlePlaceholder')}
                required
              />
            </label>
            <label className="flex min-h-0 flex-1 flex-col space-y-1.5">
              <span className="text-xs font-medium text-muted-foreground">
                {t('aiWorkspace.knowledge.contentLabel')}
              </span>
              <textarea
                className={`${adminFieldClass} min-h-[24rem] flex-1 resize-y font-mono text-[13px] leading-6`}
                value={editor.content}
                onChange={(event) => setEditor({ ...editor, content: event.target.value })}
                required
              />
            </label>
            {editor.mode === 'edit' ? (
              <label className="block space-y-1.5">
                <span className="text-xs font-medium text-muted-foreground">
                  {t('aiWorkspace.knowledge.allStatus')}
                </span>
                <select
                  className={adminFieldClass}
                  value={editor.status}
                  onChange={(event) =>
                    setEditor({
                      ...editor,
                      status: event.target.value as EditorState['status'],
                    })
                  }
                >
                  {AI_KB_STATUS_VALUES.map((status) => (
                    <option key={status} value={status}>
                      {status}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}
            <div className="sticky bottom-0 -mx-5 mt-auto flex justify-end gap-2 border-t border-border bg-admin-surface px-5 py-4">
              <button type="button" className={adminSecondaryButtonClass} onClick={() => setEditor(null)}>
                {t('aiWorkspace.knowledge.cancel')}
              </button>
              <button
                type="submit"
                disabled={createMutation.isPending || updateMutation.isPending}
                className={adminPrimaryButtonClass}
              >
                {createMutation.isPending || updateMutation.isPending ? <Spinner /> : null}
                {t('aiWorkspace.knowledge.save')}
              </button>
            </div>
          </form>
        </FormDialog>
      ) : null}

      {uploadOpen ? (
        <FormDialog
          title={t('aiWorkspace.knowledge.uploadDialogTitle')}
          description={t('aiWorkspace.knowledge.uploadDialogDesc')}
          closeLabel={t('aiWorkspace.knowledge.cancel')}
          onClose={closeUpload}
          size="l"
        >
          <form className="flex min-h-0 flex-col gap-4" onSubmit={onUploadSubmit}>
            <p className="text-xs leading-relaxed text-muted-foreground">
              {t('aiWorkspace.knowledge.uploadHint')}
            </p>
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs font-medium text-muted-foreground">
                {t('aiWorkspace.knowledge.uploadModeLabel')}
              </span>
              {(['CLI', 'KEY'] as const).map((value) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={uploadMode === value}
                  disabled={uploadBusy}
                  className={
                    uploadMode === value
                      ? 'rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground'
                      : 'rounded-lg border border-border bg-card px-3 py-1.5 text-xs font-medium text-foreground'
                  }
                  onClick={() => chooseUploadMode(value)}
                >
                  {value === 'CLI' ? t('aiWorkspace.preview.modeCli') : t('aiWorkspace.preview.modeKey')}
                </button>
              ))}
            </div>
            <p className="text-xs leading-relaxed text-muted-foreground">
              {t('aiWorkspace.knowledge.uploadTranscribeHint')}
            </p>
            <input
              ref={uploadInputRef}
              type="file"
              accept={`${KB_UPLOAD_ACCEPT},.zip`}
              multiple
              className="hidden"
              onChange={onPickUploadFile}
            />
            <button
              type="button"
              disabled={uploadBusy}
              className="flex w-full flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-border bg-card-muted/40 px-4 py-8 text-sm transition hover:border-ring/50 hover:bg-card-muted disabled:opacity-50"
              onClick={() => uploadInputRef.current?.click()}
            >
              <FileUp className="size-6 text-muted-foreground" aria-hidden />
              <span className="font-medium text-foreground">{t('aiWorkspace.knowledge.chooseFile')}</span>
              <span className="text-xs text-muted-foreground">{t('aiWorkspace.knowledge.queueHint')}</span>
            </button>

            {uploadItems.length > 0 ? (
              <ul className="max-h-72 space-y-2 overflow-y-auto pr-0.5">
                {uploadItems.map((item) => (
                  <li
                    key={item.id}
                    className={cn(
                      'flex items-start gap-3 rounded-lg border px-3 py-2.5 text-sm',
                      uploadRowClass(item.status),
                    )}
                  >
                    <span className={cn('mt-0.5 shrink-0', uploadStatusClass(item.status))}>
                      {item.status === 'uploading' ? (
                        <LoaderCircle className="size-4 animate-spin" aria-hidden />
                      ) : item.status === 'success' ? (
                        <CheckCircle2 className="size-4" aria-hidden />
                      ) : item.status === 'queued' ? (
                        <FileUp className="size-4" aria-hidden />
                      ) : (
                        <CircleAlert className="size-4" aria-hidden />
                      )}
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
                        <span className="truncate font-medium text-foreground">{item.file.name}</span>
                        <span className="shrink-0 text-xs text-muted-foreground">
                          {formatFileSize(item.file.size)}
                        </span>
                      </div>
                      <p className={cn('mt-0.5 text-xs', uploadStatusClass(item.status))}>
                        {item.fieldError === 'extension'
                          ? t('aiWorkspace.knowledge.invalidExtension')
                          : item.fieldError === 'size'
                            ? t('aiWorkspace.knowledge.fileTooLarge')
                            : item.detail || uploadStatusLabel(item.status)}
                      </p>
                    </div>
                    {item.status === 'queued' || item.status === 'rejected' ? (
                      <button
                        type="button"
                        disabled={uploadBusy}
                        className="shrink-0 rounded-md p-1 text-muted-foreground transition hover:bg-card-muted hover:text-foreground disabled:opacity-40"
                        aria-label={t('aiWorkspace.knowledge.removeQueued')}
                        onClick={() => removeUploadItem(item.id)}
                      >
                        <X className="size-4" />
                      </button>
                    ) : null}
                  </li>
                ))}
              </ul>
            ) : (
              <p className="rounded-lg border border-border bg-card-muted/30 px-3 py-6 text-center text-sm text-muted-foreground">
                {t('aiWorkspace.knowledge.queueEmpty')}
              </p>
            )}

            {singleQueued ? (
              <label className="block space-y-1.5">
                <span className="text-xs font-medium text-muted-foreground">
                  {t('aiWorkspace.knowledge.titleLabel')}
                </span>
                <input
                  className={adminFieldClass}
                  value={singleQueued.title}
                  disabled={uploadBusy}
                  onChange={(event) =>
                    patchUploadItem(singleQueued.id, { title: event.target.value })
                  }
                  placeholder={t('aiWorkspace.knowledge.titlePlaceholder')}
                />
              </label>
            ) : null}

            <div className="sticky bottom-0 -mx-5 mt-auto flex justify-end gap-2 border-t border-border bg-admin-surface px-5 pt-4">
              <button
                type="button"
                disabled={uploadBusy}
                className={adminSecondaryButtonClass}
                onClick={closeUpload}
              >
                {queuedCount > 0 && !uploadBusy
                  ? t('aiWorkspace.knowledge.cancel')
                  : t('aiWorkspace.knowledge.close')}
              </button>
              <button
                type="submit"
                disabled={uploadBusy || queuedCount === 0}
                className={adminPrimaryButtonClass}
              >
                {uploadBusy ? <Spinner /> : null}
                {uploadBusy
                  ? t('aiWorkspace.knowledge.uploading')
                  : t('aiWorkspace.knowledge.uploadStart', { count: queuedCount })}
              </button>
            </div>
          </form>
        </FormDialog>
      ) : null}

      {deletingDoc ? (
        <FormDialog
          title={t('aiWorkspace.knowledge.deleteConfirmTitle')}
          closeLabel={t('aiWorkspace.knowledge.cancel')}
          onClose={() => setDeletingDoc(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('aiWorkspace.knowledge.confirmDelete', { title: deletingDoc.title })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setDeletingDoc(null)}
            >
              {t('aiWorkspace.knowledge.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(deletingDoc.id)}
            >
              {deleteMutation.isPending ? <Spinner /> : null}
              {t('aiWorkspace.knowledge.delete')}
            </button>
          </div>
        </FormDialog>
      ) : null}

      {deletingBase ? (
        <FormDialog
          title={t('aiWorkspace.knowledge.baseDeleteConfirmTitle')}
          closeLabel={t('aiWorkspace.knowledge.cancel')}
          onClose={() => setDeletingBase(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('aiWorkspace.knowledge.baseDeleteConfirm', { name: deletingBase.name })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setDeletingBase(null)}
            >
              {t('aiWorkspace.knowledge.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={deleteBaseMutation.isPending}
              onClick={() => deleteBaseMutation.mutate(deletingBase.id)}
            >
              {deleteBaseMutation.isPending ? <Spinner /> : null}
              {t('aiWorkspace.knowledge.baseDelete')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </div>
  )
}
