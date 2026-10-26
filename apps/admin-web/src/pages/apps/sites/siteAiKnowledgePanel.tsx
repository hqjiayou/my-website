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
const KB_UPLOAD_ACCEPT =
  '.txt,.md,.pdf,.docx,.html,.htm,text/plain,text/markdown,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document,text/html'

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
  if (!['txt', 'md', 'pdf', 'docx', 'html', 'htm'].includes(extension)) return 'extension'
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

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', siteId, 'ai', 'knowledge', filters],
    queryFn: () =>
      aiApi.listKnowledgeDocuments(siteId, {
        pageNum: filters.pageNum,
        pageSize: filters.pageSize,
        status: filters.status || undefined,
      }),
  })

  const createMutation = useMutation({
    mutationFn: () =>
      aiApi.createKnowledgeDocument(siteId, { title: editor!.title.trim(), content: editor!.content }),
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
      aiApi.uploadKnowledgeDocument(siteId, input.file, input.title),
    onSuccess: () => {
      toast.success(t('aiWorkspace.knowledge.uploaded'))
      setUploadDialog(null)
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'ai', 'knowledge'] })
    },
  })

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
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
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
      { id: 'title', header: t('aiWorkspace.knowledge.columns.title'), cell: (item) => item.title },
      {
        id: 'status',
        header: t('aiWorkspace.knowledge.columns.status'),
        cell: (item) => t(`aiWorkspace.knowledge.status.${item.status}`, { defaultValue: item.status }),
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
            <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
              <button
                type="button"
                className="text-sm underline"
                onClick={() => void openEditor(item.id)}
              >
                {t('aiWorkspace.knowledge.edit')}
              </button>
            </PermissionGate>
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
          </div>
        ),
      },
    ],
    [deleteMutation, openEditor, t],
  )

  const records = data?.records ?? []

  return (
    <div className="space-y-4">
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
            className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-medium text-white"
            onClick={() =>
              setEditor({ mode: 'create', title: '', content: '', status: AI_KB_STATUS.ACTIVE })
            }
          >
            {t('aiWorkspace.knowledge.create')}
          </button>
          <button
            type="button"
            className="rounded-lg bg-slate-900 px-3 py-1.5 text-sm font-medium text-white disabled:opacity-50"
            disabled={uploadMutation.isPending}
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
              accept={KB_UPLOAD_ACCEPT}
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
