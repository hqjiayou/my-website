import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useMemo, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { DataTable, type DataTableColumn } from '@/components/admin'
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
}

interface UploadDialogState {
  file: File | null
  title: string
  fieldError: 'extension' | 'size' | null
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

function uploadFieldError(file: File): UploadDialogState['fieldError'] {
  const extension = knowledgeFileExtension(file.name)
  // zip 是批量导入容器，走 upload-archive 端点，不在单文档格式集合内
  if (extension !== 'zip' && !KB_UPLOAD_EXTENSIONS.includes(extension)) return 'extension'
  if (file.size > KB_UPLOAD_MAX_BYTES) return 'size'
  return null
}

export function SiteAiKnowledgePanel({ siteId }: { siteId: number }) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<{ pageNum: number; pageSize: number; status: AiKnowledgeStatus | '' }>({
    pageNum: 1,
    pageSize: 20,
    status: '',
  })
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [uploadDialog, setUploadDialog] = useState<UploadDialogState | null>(null)
  const uploadInputRef = useRef<HTMLInputElement>(null)
  const [selectedBaseId, setSelectedBaseId] = useState<number | null>(null)
  const [newBaseName, setNewBaseName] = useState<string | null>(null)

  const basesQuery = useQuery({
    queryKey: ['sites', siteId, 'ai', 'knowledge-bases'],
    queryFn: () => aiApi.listKnowledgeBases(siteId),
  })
  const bases = basesQuery.data ?? []
  const selectedBase = selectedBaseId == null ? null : (bases.find((base) => base.id === selectedBaseId) ?? null)
  const viewingShared = selectedBase != null && !selectedBase.owned
  const writableBaseId = selectedBase != null && selectedBase.owned ? selectedBase.id : null

  const createBaseMutation = useMutation({
    mutationFn: (name: string) => aiApi.createKnowledgeBase(siteId, name),
    onSuccess: (base) => {
      toast.success(t('aiWorkspace.knowledge.baseCreated'))
      setNewBaseName(null)
      setSelectedBaseId(base.id)
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'ai', 'knowledge-bases'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const deleteBaseMutation = useMutation({
    mutationFn: (baseId: number) => aiApi.deleteKnowledgeBase(siteId, baseId),
    onSuccess: () => {
      toast.success(t('aiWorkspace.knowledge.baseDeleted'))
      setSelectedBaseId(null)
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'ai', 'knowledge-bases'] })
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'ai', 'knowledge'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', siteId, 'ai', 'knowledge', filters, selectedBaseId],
    queryFn: () =>
      aiApi.listKnowledgeDocuments(siteId, {
        pageNum: filters.pageNum,
        pageSize: filters.pageSize,
        status: filters.status || undefined,
        baseId: selectedBaseId ?? undefined,
      }),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      aiApi.createKnowledgeDocument(siteId, {
        title: editor!.title.trim(),
        content: editor!.content,
        baseId: writableBaseId,
      }),
    onSuccess: () => {
      toast.success(t('aiWorkspace.knowledge.created'))
      setEditor(null)
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'ai', 'knowledge'] })
    },
  })

  const updateMutation = useMutation({
    mutationFn: () =>
      aiApi.updateKnowledgeDocument(siteId, editor!.id!, {
        title: editor!.title.trim(),
        content: editor!.content,
        status: editor!.status,
        version: editor!.version ?? 0,
      }),
    onSuccess: () => {
      toast.success(t('aiWorkspace.knowledge.updated'))
      setEditor(null)
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'ai', 'knowledge'] })
    },
  })

  const uploadMutation = useMutation({
    mutationFn: (input: { file: File; title?: string }) =>
      aiApi.uploadKnowledgeDocument(siteId, input.file, input.title, writableBaseId),
    onSuccess: (detail) => {
      if (detail.indexStatus === 'INDEXED') toast.success(t('aiWorkspace.knowledge.uploadedIndexed'))
      else if (detail.indexStatus === 'FAILED')
        toast.warning(t('aiWorkspace.knowledge.uploadedIndexFailed'))
      else toast.success(t('aiWorkspace.knowledge.uploaded'))
      setUploadDialog(null)
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'ai', 'knowledge'] })
    },
  })

  const reindexMutation = useMutation({
    mutationFn: (id: number) => aiApi.reindexKnowledgeDocument(siteId, id),
    onSuccess: (detail) => {
      if (detail.indexStatus === 'INDEXED') toast.success(t('aiWorkspace.knowledge.reindexed'))
      else toast.warning(t('aiWorkspace.knowledge.reindexFailed', { error: detail.indexError ?? '' }))
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'ai', 'knowledge'] })
    },
    onError: (error: Error) => toast.error(error.message),
  })

  const [batchUploading, setBatchUploading] = useState(false)
  const [dragging, setDragging] = useState(false)

  /** 多文件（选择多选或拖拽）：逐个直传（标题取文件名），最后汇总已索引/索引失败/被拒数量。 */
  const uploadFilesBatch = useCallback(
    async (files: File[]) => {
      if (batchUploading || files.length === 0 || viewingShared) return
      setBatchUploading(true)
      let indexed = 0
      let indexFailed = 0
      let rejected = 0
      try {
        for (const file of files) {
          if (uploadFieldError(file)) {
            rejected++
            continue
          }
          try {
            if (knowledgeFileExtension(file.name) === 'zip') {
              const result = await aiApi.uploadKnowledgeArchive(siteId, file, writableBaseId)
              for (const detail of result.documents) {
                if (detail.indexStatus === 'FAILED') indexFailed++
                else indexed++
              }
              rejected += result.failures.length
              if (result.createdCount === 0 && result.failures.length === 0) rejected++
            } else {
              const detail = await aiApi.uploadKnowledgeDocument(siteId, file, undefined, writableBaseId)
              if (detail.indexStatus === 'INDEXED') indexed++
              else if (detail.indexStatus === 'FAILED') indexFailed++
              else indexed++
            }
          } catch {
            rejected++
          }
        }
        toast.success(
          t('aiWorkspace.knowledge.batchResult', { indexed, indexFailed, rejected }),
        )
        void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'ai', 'knowledge'] })
      } finally {
        setBatchUploading(false)
      }
    },
    [batchUploading, queryClient, siteId, t, viewingShared, writableBaseId],
  )

  const deleteMutation = useMutation({
    mutationFn: (id: number) => aiApi.deleteKnowledgeDocument(siteId, id),
    onSuccess: () => {
      toast.success(t('aiWorkspace.knowledge.deleted'))
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'ai', 'knowledge'] })
    },
  })

  const openUpload = useCallback(() => {
    setUploadDialog({ file: null, title: '', fieldError: null })
  }, [])

  const closeUpload = useCallback(() => {
    if (uploadMutation.isPending) return
    setUploadDialog(null)
  }, [uploadMutation.isPending])

  function onPickUploadFile(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (files.length === 0) return
    if (files.length > 1) {
      setUploadDialog(null)
      void uploadFilesBatch(files)
      return
    }
    const file = files[0]
    // zip 不进单文件对话框（无标题可填）：直接走批量导入路径
    if (knowledgeFileExtension(file.name) === 'zip' && !uploadFieldError(file)) {
      setUploadDialog(null)
      void uploadFilesBatch([file])
      return
    }
    setUploadDialog({
      file,
      title: titleFromFilename(file.name),
      fieldError: uploadFieldError(file),
    })
  }

  function onUploadSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!uploadDialog?.file || uploadMutation.isPending) return
    const fieldError = uploadFieldError(uploadDialog.file)
    if (fieldError) {
      setUploadDialog({ ...uploadDialog, fieldError })
      return
    }
    const trimmed = uploadDialog.title.trim()
    uploadMutation.mutate({
      file: uploadDialog.file,
      title: trimmed || undefined,
    })
  }

  const openEditor = useCallback(async (id: number) => {
    const detail = await aiApi.getKnowledgeDocument(siteId, id)
    setEditor({
      mode: 'edit',
      id: detail.id,
      version: detail.version,
      title: detail.title,
      content: detail.content,
      status: detail.status,
    })
  }, [siteId])

  const columns = useMemo<DataTableColumn<AiKnowledgeListItem>[]>(
    () => [
      {
        id: 'title',
        header: t('aiWorkspace.knowledge.columns.title'),
        cell: (item) => (
          <div>
            <div>{item.title}</div>
            {item.originalFilename ? (
              <div className="text-xs text-slate-500">
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
          <span
            className={
              item.indexStatus === 'INDEXED'
                ? 'text-emerald-700'
                : item.indexStatus === 'FAILED'
                  ? 'text-rose-700'
                  : 'text-slate-500'
            }
            title={item.indexStatus === 'FAILED' ? (item.indexError ?? undefined) : undefined}
          >
            {t(`aiWorkspace.knowledge.indexStatus.${item.indexStatus}`, {
              defaultValue: item.indexStatus,
            })}
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
              <span className="text-xs text-slate-400">{t('aiWorkspace.knowledge.sharedBadge')}</span>
            )}
            {item.hasOriginal ? (
              <button
                type="button"
                className="text-sm underline"
                onClick={() => {
                  void aiApi
                    .getKnowledgeDocumentFile(siteId, item.id)
                    .then((file) => window.open(file.url, '_blank', 'noopener'))
                    .catch((error: Error) => toast.error(error.message))
                }}
              >
                {t('aiWorkspace.knowledge.downloadOriginal')}
              </button>
            ) : null}
            {item.owned && item.status === 'ACTIVE' && item.indexStatus !== 'INDEXED' ? (
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
                  className="text-sm text-rose-700 underline"
                  onClick={() => {
                    if (window.confirm(t('aiWorkspace.knowledge.confirmDelete'))) {
                      deleteMutation.mutate(item.id)
                    }
                  }}
                >
                  {t('aiWorkspace.knowledge.delete')}
                </button>
              </PermissionGate>
            ) : null}
          </div>
        ),
      },
    ],
    [deleteMutation, openEditor, reindexMutation, siteId, t],
  )

  const records = data?.records ?? []

  return (
    <div
      className={`space-y-4 ${dragging ? 'rounded-xl outline-2 outline-dashed outline-blue-400 outline-offset-4' : ''}`}
      onDragOver={(event) => {
        event.preventDefault()
        setDragging(true)
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => {
        event.preventDefault()
        setDragging(false)
        void uploadFilesBatch(Array.from(event.dataTransfer.files))
      }}
    >
      <p className="text-xs text-slate-500">
        {batchUploading ? t('aiWorkspace.knowledge.uploading') : t('aiWorkspace.knowledge.dropHint')}
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <label className="flex items-center gap-2 text-sm">
          <span className="text-slate-600">{t('aiWorkspace.knowledge.baseLabel')}</span>
          <select
            className="rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
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
                      base.ownerSiteName ? ` · ${base.ownerSiteName}` : ''
                    }`}
              </option>
            ))}
          </select>
        </label>
        <PermissionGate code={PERMISSION_CODES.aiKbCreate}>
          {newBaseName == null ? (
            <button
              type="button"
              className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
              onClick={() => setNewBaseName('')}
            >
              {t('aiWorkspace.knowledge.baseNew')}
            </button>
          ) : (
            <span className="flex items-center gap-1">
              <input
                className="rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
                value={newBaseName}
                onChange={(event) => setNewBaseName(event.target.value)}
                placeholder={t('aiWorkspace.knowledge.baseNamePlaceholder')}
              />
              <button
                type="button"
                className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
                disabled={!newBaseName.trim() || createBaseMutation.isPending}
                onClick={() => createBaseMutation.mutate(newBaseName.trim())}
              >
                {t('aiWorkspace.knowledge.baseCreate')}
              </button>
              <button
                type="button"
                className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm"
                onClick={() => setNewBaseName(null)}
              >
                {t('aiWorkspace.knowledge.baseCancel')}
              </button>
            </span>
          )}
        </PermissionGate>
        {selectedBase?.owned && !selectedBase.isDefault ? (
          <PermissionGate code={PERMISSION_CODES.aiKbDelete}>
            <button
              type="button"
              className="rounded-lg border border-rose-200 px-3 py-1.5 text-sm text-rose-700 disabled:opacity-50"
              disabled={deleteBaseMutation.isPending}
              onClick={() => {
                if (window.confirm(t('aiWorkspace.knowledge.baseDeleteConfirm'))) {
                  deleteBaseMutation.mutate(selectedBase.id)
                }
              }}
            >
              {t('aiWorkspace.knowledge.baseDelete')}
            </button>
          </PermissionGate>
        ) : null}
        {viewingShared ? (
          <span className="text-xs text-amber-700">{t('aiWorkspace.knowledge.sharedReadOnly')}</span>
        ) : null}
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <select
          className="rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
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
        <PermissionGate code={PERMISSION_CODES.aiKbCreate}>
          <button
            type="button"
            className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            disabled={viewingShared}
            onClick={() =>
              setEditor({ mode: 'create', title: '', content: '', status: AI_KB_STATUS.ACTIVE })
            }
          >
            {t('aiWorkspace.knowledge.create')}
          </button>
          <button
            type="button"
            className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            disabled={uploadMutation.isPending || viewingShared}
            onClick={openUpload}
          >
            {t('aiWorkspace.knowledge.upload')}
          </button>
        </PermissionGate>
      </div>

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

      {editor ? (
        <form
          className="space-y-3 rounded-xl border border-slate-200 bg-white p-4"
          onSubmit={(event) => {
            event.preventDefault()
            if (editor.mode === 'create') createMutation.mutate()
            else updateMutation.mutate()
          }}
        >
          <input
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
            value={editor.title}
            onChange={(event) => setEditor({ ...editor, title: event.target.value })}
            placeholder={t('aiWorkspace.knowledge.titlePlaceholder')}
            required
          />
          <textarea
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
            rows={8}
            value={editor.content}
            onChange={(event) => setEditor({ ...editor, content: event.target.value })}
            required
          />
          {editor.mode === 'edit' ? (
            <select
              className="rounded-lg border border-slate-200 px-2 py-1.5 text-sm"
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
          ) : null}
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={createMutation.isPending || updateMutation.isPending}
              className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
            >
              {t('aiWorkspace.knowledge.save')}
            </button>
            <button
              type="button"
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm"
              onClick={() => setEditor(null)}
            >
              {t('aiWorkspace.knowledge.cancel')}
            </button>
          </div>
        </form>
      ) : null}

      {uploadDialog ? (
        <>
          <button
            type="button"
            className="fixed inset-0 z-40 bg-slate-900/40"
            aria-label={t('aiWorkspace.knowledge.cancel')}
            onClick={closeUpload}
          />
          <form
            role="dialog"
            aria-modal="true"
            aria-labelledby="knowledge-upload-title"
            className="fixed top-1/2 left-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 space-y-3 rounded-xl border border-slate-200 bg-white p-4 shadow-xl"
            onSubmit={onUploadSubmit}
          >
            <h2 id="knowledge-upload-title" className="text-base font-semibold text-slate-900">
              {t('aiWorkspace.knowledge.uploadDialogTitle')}
            </h2>
            <p className="text-xs text-slate-500">{t('aiWorkspace.knowledge.uploadHint')}</p>
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
              disabled={uploadMutation.isPending}
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm disabled:opacity-50"
              onClick={() => uploadInputRef.current?.click()}
            >
              {t('aiWorkspace.knowledge.chooseFile')}
            </button>
            {uploadDialog.file ? (
              <p className="text-sm text-slate-700">
                {uploadDialog.file.name}
                <span className="text-slate-500"> · {formatFileSize(uploadDialog.file.size)}</span>
              </p>
            ) : null}
            {uploadDialog.fieldError ? (
              <p className="text-sm text-rose-700">
                {uploadDialog.fieldError === 'extension'
                  ? t('aiWorkspace.knowledge.invalidExtension')
                  : t('aiWorkspace.knowledge.fileTooLarge')}
              </p>
            ) : null}
            <label className="block">
              <span className="text-sm font-medium text-slate-800">
                {t('aiWorkspace.knowledge.titleLabel')}
              </span>
              <input
                className="mt-1 w-full rounded-lg border border-slate-200 px-3 py-2 text-sm"
                value={uploadDialog.title}
                disabled={uploadMutation.isPending}
                onChange={(event) =>
                  setUploadDialog((prev) => (prev ? { ...prev, title: event.target.value } : prev))
                }
                placeholder={t('aiWorkspace.knowledge.titlePlaceholder')}
              />
            </label>
            <div className="flex gap-2">
              <button
                type="submit"
                disabled={
                  uploadMutation.isPending || !uploadDialog.file || uploadDialog.fieldError != null
                }
                className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                {uploadMutation.isPending
                  ? t('aiWorkspace.knowledge.uploading')
                  : t('aiWorkspace.knowledge.upload')}
              </button>
              <button
                type="button"
                disabled={uploadMutation.isPending}
                className="rounded-lg border border-slate-200 px-3 py-2 text-sm disabled:opacity-50"
                onClick={closeUpload}
              >
                {t('aiWorkspace.knowledge.cancel')}
              </button>
            </div>
          </form>
        </>
      ) : null}
    </div>
  )
}
