import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, X } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'
import { toast } from 'sonner'

import {
  CONTENT_STATUS,
  CONTENT_STATUS_VALUES,
  CONTENT_TYPE_VALUES,
  contentApi,
  MEDIA_STATUS,
  mediaApi,
  type ContentListItem,
  type ContentListQuery,
  type ContentStatus,
  type ContentType,
  type CreateContentInput,
  type MediaListItem,
  type UpdateContentInput,
} from '@/api'
import { DataTable, FormDialog, Spinner, type DataTableColumn } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminFormActionsClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

type EditorMode = 'create' | 'edit'

interface EditorState {
  mode: EditorMode
  contentId?: number
  version?: number
  /** 打开编辑时的原始 status；用于判断是否需另调 PATCH .../status */
  initialStatus?: ContentStatus
  type: ContentType
  title: string
  body: string
  status: ContentStatus
  sortOrder: string
  coverMediaId: string
}

function emptyEditor(defaults?: Partial<EditorState>): EditorState {
  return {
    mode: 'create',
    type: 'hero',
    title: '',
    body: '',
    status: CONTENT_STATUS.DRAFT,
    sortOrder: '0',
    coverMediaId: '',
    ...defaults,
  }
}

function statusClassName(status: string) {
  switch (status) {
    case CONTENT_STATUS.PUBLISHED:
      return 'border border-success/35 bg-success-muted text-success-emphasis'
    case CONTENT_STATUS.DRAFT:
      return 'border border-warning/35 bg-warning-muted text-warning-emphasis'
    case CONTENT_STATUS.HIDDEN:
      return 'border border-border bg-card-muted text-muted-foreground'
    default:
      return 'border border-border bg-card-muted text-muted-foreground'
  }
}

function parseOptionalMediaId(value: string): number | null {
  const trimmed = value.trim()
  if (!trimmed) return null
  const id = Number(trimmed)
  return Number.isFinite(id) ? id : null
}

function parseSortOrder(value: string): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? Math.trunc(parsed) : 0
}

export function AgentContentsPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const queryClient = useQueryClient()

  const canCreate = useHasPermission(PERMISSION_CODES.contentCreate)
  const canUpdate = useHasPermission(PERMISSION_CODES.contentUpdate)
  const canDelete = useHasPermission(PERMISSION_CODES.contentDelete)
  const canStatus = useHasPermission(PERMISSION_CODES.contentStatus)
  const canListMedia = useHasPermission(PERMISSION_CODES.mediaList)

  const [filters, setFilters] = useState<ContentListQuery>({ pageNum: 1, pageSize: 20 })
  const [draftType, setDraftType] = useState('')
  const [draftStatus, setDraftStatus] = useState('')
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [deleting, setDeleting] = useState<ContentListItem | null>(null)

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', site.id, 'contents', filters],
    queryFn: () => contentApi.listContents(site.id, filters),
  })

  // 封面可选；无 media:list 时不请求，避免干扰内容页主流程
  const mediaFilters = { pageNum: 1, pageSize: 100 }
  const { data: mediaPage } = useQuery({
    queryKey: ['sites', site.id, 'media', mediaFilters],
    queryFn: () => mediaApi.listMedia(site.id, mediaFilters),
    enabled: Boolean(editor) && canListMedia,
  })

  const readyMedia = useMemo(
    () => (mediaPage?.records ?? []).filter((item) => item.status === MEDIA_STATUS.READY),
    [mediaPage?.records],
  )

  const records = data?.records ?? []
  const total = data?.total ?? 0

  const invalidateContents = () =>
    void queryClient.invalidateQueries({ queryKey: ['sites', site.id, 'contents'] })

  const createMutation = useMutation({
    mutationFn: (input: CreateContentInput) => contentApi.createContent(site.id, input),
    onSuccess: () => {
      toast.success(t('contentManagement.messages.created'))
      setEditor(null)
      invalidateContents()
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const updateMutation = useMutation({
    mutationFn: async ({
      id,
      input,
      nextStatus,
    }: {
      id: number
      input: UpdateContentInput
      /** 相对打开编辑时有变更时才传；PUT 后用返回 version 再 PATCH */
      nextStatus?: ContentStatus
    }) => {
      const updated = await contentApi.updateContent(site.id, id, input)
      if (nextStatus != null && nextStatus !== updated.status) {
        return contentApi.updateContentStatus(site.id, id, {
          status: nextStatus,
          version: updated.version,
        })
      }
      return updated
    },
    onSuccess: () => {
      toast.success(t('contentManagement.messages.updated'))
      setEditor(null)
      invalidateContents()
    },
    onError: (err: Error) => {
      toast.error(err.message)
      invalidateContents()
    },
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status, version }: { id: number; status: string; version: number }) =>
      contentApi.updateContentStatus(site.id, id, { status, version }),
    onSuccess: () => {
      toast.success(t('contentManagement.messages.statusUpdated'))
      invalidateContents()
    },
    onError: (err: Error) => {
      toast.error(err.message)
      invalidateContents()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => contentApi.deleteContent(site.id, id),
    onSuccess: () => {
      setDeleting(null)
      toast.success(t('contentManagement.messages.deleted'))
      invalidateContents()
    },
    onError: (err: Error) => toast.error(err.message),
  })

  function openCreate() {
    setEditor(emptyEditor({ mode: 'create' }))
  }

  function openEdit(item: ContentListItem) {
    const status = (CONTENT_STATUS_VALUES.includes(item.status as ContentStatus)
      ? item.status
      : CONTENT_STATUS.DRAFT) as ContentStatus
    setEditor(
      emptyEditor({
        mode: 'edit',
        contentId: item.id,
        version: item.version,
        initialStatus: status,
        type: (CONTENT_TYPE_VALUES.includes(item.type as ContentType)
          ? item.type
          : 'hero') as ContentType,
        title: item.title,
        body: item.body ?? '',
        status,
        sortOrder: String(item.sortOrder ?? 0),
        coverMediaId: item.coverMediaId != null ? String(item.coverMediaId) : '',
      }),
    )
  }

  function submitEditor() {
    if (!editor) return
    const title = editor.title.trim()
    if (!title) {
      toast.error(t('contentManagement.messages.titleRequired'))
      return
    }

    const coverMediaId = parseOptionalMediaId(editor.coverMediaId)
    const sortOrder = parseSortOrder(editor.sortOrder)
    const body = editor.body.trim() || null

    if (editor.mode === 'create') {
      createMutation.mutate({
        type: editor.type,
        title,
        body,
        status: editor.status,
        sortOrder,
        coverMediaId,
      })
      return
    }

    if (editor.contentId == null || editor.version == null) return
    const statusChanged =
      editor.initialStatus != null && editor.status !== editor.initialStatus
    updateMutation.mutate({
      id: editor.contentId,
      input: {
        type: editor.type,
        title,
        body,
        sortOrder,
        coverMediaId,
        version: editor.version,
      },
      nextStatus: statusChanged ? editor.status : undefined,
    })
  }

  function applyFilters() {
    setFilters((prev) => ({
      ...prev,
      pageNum: 1,
      type: draftType || undefined,
      status: draftStatus || undefined,
    }))
  }

  function resetFilters() {
    setDraftType('')
    setDraftStatus('')
    setFilters({ pageNum: 1, pageSize: filters.pageSize ?? 20 })
  }

  const columns = useMemo<DataTableColumn<ContentListItem>[]>(
    () => [
      {
        id: 'title',
        header: t('contentManagement.columns.title'),
        cell: (item) => (
          <div>
            <p className="font-medium text-foreground">{item.title}</p>
            <p className="mt-1 max-w-[280px] truncate text-xs text-muted-foreground">
              {item.body?.trim() || '—'}
            </p>
          </div>
        ),
      },
      {
        id: 'type',
        header: t('contentManagement.columns.type'),
        cell: (item) => t(`contentManagement.typeLabels.${item.type}`, { defaultValue: item.type }),
      },
      {
        id: 'status',
        header: t('contentManagement.columns.status'),
        cell: (item) =>
          canStatus ? (
            <select
              value={item.status}
              disabled={statusMutation.isPending}
              onChange={(event) => {
                const next = event.target.value
                if (next === item.status) return
                statusMutation.mutate({ id: item.id, status: next, version: item.version })
              }}
              className={cn(
                'rounded-md border px-2 py-1 text-xs font-medium outline-none',
                statusClassName(item.status),
              )}
            >
              {CONTENT_STATUS_VALUES.map((status) => (
                <option key={status} value={status}>
                  {t(`contentManagement.statusLabels.${status}`)}
                </option>
              ))}
            </select>
          ) : (
            <span
              className={cn(
                'inline-flex rounded-md px-2 py-1 text-xs font-medium',
                statusClassName(item.status),
              )}
            >
              {t(`contentManagement.statusLabels.${item.status}`, { defaultValue: item.status })}
            </span>
          ),
      },
      {
        id: 'sortOrder',
        header: t('contentManagement.columns.sortOrder'),
        cell: (item) => item.sortOrder,
      },
      {
        id: 'coverMediaId',
        header: t('contentManagement.columns.cover'),
        cell: (item) => (item.coverMediaId != null ? `#${item.coverMediaId}` : '—'),
      },
      {
        id: 'updatedAt',
        header: t('contentManagement.columns.updatedAt'),
        cell: (item) => new Date(item.updatedAt).toLocaleString(),
      },
      {
        id: 'actions',
        header: t('contentManagement.columns.actions'),
        cell: (item) => (
          <div className="flex flex-wrap gap-2">
            {canUpdate ? (
              <button
                type="button"
                onClick={() => openEdit(item)}
                className="rounded-md border border-border px-2.5 py-1.5 text-xs font-medium text-foreground transition hover:bg-card-muted"
              >
                {t('contentManagement.actions.edit')}
              </button>
            ) : null}
            {canDelete ? (
              <button
                type="button"
                onClick={() => setDeleting(item)}
                className="rounded-md border border-danger/35 px-2.5 py-1.5 text-xs font-medium text-danger-emphasis transition hover:bg-danger-muted"
              >
                {t('contentManagement.actions.delete')}
              </button>
            ) : null}
          </div>
        ),
      },
    ],
    [canDelete, canStatus, canUpdate, statusMutation, t],
  )

  const saving = createMutation.isPending || updateMutation.isPending

  return (
    <section className="w-full">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <p className="text-sm text-muted-foreground">{t('contentManagement.description')}</p>
        <PermissionGate code={PERMISSION_CODES.contentCreate}>
          <button
            type="button"
            onClick={openCreate}
            className="inline-flex shrink-0 items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/92"
          >
            <Plus className="h-4 w-4" />
            {t('contentManagement.actions.create')}
          </button>
        </PermissionGate>
      </div>

      <div className="mb-4 rounded-xl border border-border bg-card p-4">
        <div className="grid gap-3 md:grid-cols-4">
          <label className="space-y-1.5 text-sm">
            <span className="font-medium text-foreground">{t('contentManagement.filters.type')}</span>
            <select
              value={draftType}
              onChange={(event) => setDraftType(event.target.value)}
              className="w-full rounded-lg border border-border bg-card px-3 py-2 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <option value="">{t('contentManagement.filters.allTypes')}</option>
              {CONTENT_TYPE_VALUES.map((type) => (
                <option key={type} value={type}>
                  {t(`contentManagement.typeLabels.${type}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="space-y-1.5 text-sm">
            <span className="font-medium text-foreground">{t('contentManagement.filters.status')}</span>
            <select
              value={draftStatus}
              onChange={(event) => setDraftStatus(event.target.value)}
              className="w-full rounded-lg border border-border bg-card px-3 py-2 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <option value="">{t('contentManagement.filters.allStatuses')}</option>
              {CONTENT_STATUS_VALUES.map((status) => (
                <option key={status} value={status}>
                  {t(`contentManagement.statusLabels.${status}`)}
                </option>
              ))}
            </select>
          </label>
          <div className="flex items-end gap-2 md:col-span-2">
            <button
              type="button"
              onClick={applyFilters}
              className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/92"
            >
              {t('contentManagement.filters.search')}
            </button>
            <button
              type="button"
              onClick={resetFilters}
              className="rounded-lg border border-border px-3 py-2 text-sm text-foreground hover:bg-card-muted"
            >
              {t('contentManagement.filters.reset')}
            </button>
          </div>
        </div>
      </div>

      {editor ? (
        <article className="mb-4 rounded-xl border border-border bg-card p-4">
          <div className="mb-3 flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold text-foreground">
              {editor.mode === 'create'
                ? t('contentManagement.editor.createTitle')
                : t('contentManagement.editor.editTitle')}
            </h2>
            <button
              type="button"
              onClick={() => setEditor(null)}
              className="rounded-md border border-border p-1.5 text-muted-foreground transition hover:bg-card-muted"
              aria-label={t('contentManagement.actions.cancel')}
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-foreground">{t('contentManagement.fields.type')}</span>
              <select
                value={editor.type}
                onChange={(event) =>
                  setEditor((prev) => (prev ? { ...prev, type: event.target.value as ContentType } : prev))
                }
                className="w-full rounded-lg border border-border px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {CONTENT_TYPE_VALUES.map((type) => (
                  <option key={type} value={type}>
                    {t(`contentManagement.typeLabels.${type}`)}
                  </option>
                ))}
              </select>
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-foreground">{t('contentManagement.fields.status')}</span>
              <select
                value={editor.status}
                onChange={(event) =>
                  setEditor((prev) =>
                    prev ? { ...prev, status: event.target.value as ContentStatus } : prev,
                  )
                }
                className="w-full rounded-lg border border-border px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {CONTENT_STATUS_VALUES.map((status) => (
                  <option key={status} value={status}>
                    {t(`contentManagement.statusLabels.${status}`)}
                  </option>
                ))}
              </select>
            </label>

            <label className="space-y-1.5 text-sm md:col-span-2">
              <span className="font-medium text-foreground">{t('contentManagement.fields.title')}</span>
              <input
                value={editor.title}
                onChange={(event) =>
                  setEditor((prev) => (prev ? { ...prev, title: event.target.value } : prev))
                }
                maxLength={255}
                className="w-full rounded-lg border border-border px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </label>

            <label className="space-y-1.5 text-sm md:col-span-2">
              <span className="font-medium text-foreground">{t('contentManagement.fields.body')}</span>
              <textarea
                value={editor.body}
                onChange={(event) =>
                  setEditor((prev) => (prev ? { ...prev, body: event.target.value } : prev))
                }
                rows={5}
                maxLength={20000}
                className="w-full rounded-lg border border-border px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-foreground">{t('contentManagement.fields.sortOrder')}</span>
              <input
                type="number"
                value={editor.sortOrder}
                onChange={(event) =>
                  setEditor((prev) => (prev ? { ...prev, sortOrder: event.target.value } : prev))
                }
                className="w-full rounded-lg border border-border px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-foreground">{t('contentManagement.fields.cover')}</span>
              <select
                value={editor.coverMediaId}
                onChange={(event) =>
                  setEditor((prev) => (prev ? { ...prev, coverMediaId: event.target.value } : prev))
                }
                className="w-full rounded-lg border border-border px-3 py-2 outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">{t('contentManagement.fields.coverNone')}</option>
                {readyMedia.map((item: MediaListItem) => (
                  <option key={item.id} value={String(item.id)}>
                    #{item.id} · {item.originalFilename}
                  </option>
                ))}
              </select>
              <p className="text-xs text-muted-foreground">{t('contentManagement.fields.coverHint')}</p>
            </label>
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="button"
              disabled={saving || (editor.mode === 'create' ? !canCreate : !canUpdate)}
              onClick={submitEditor}
              className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/92 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {saving ? t('contentManagement.actions.saving') : t('contentManagement.actions.save')}
            </button>
            <button
              type="button"
              onClick={() => setEditor(null)}
              className="rounded-lg border border-border px-3 py-2 text-sm text-foreground hover:bg-card-muted"
            >
              {t('contentManagement.actions.cancel')}
            </button>
          </div>
        </article>
      ) : null}

      <DataTable
        columns={columns}
        data={records}
        rowKey={(item) => item.id}
        isLoading={isPending}
        isError={isError}
        errorMessage={error instanceof Error ? error.message : undefined}
        emptyMessage={t('contentManagement.empty')}
        onRetry={() => void refetch()}
        pagination={{
          page: filters.pageNum ?? 1,
          pageSize: filters.pageSize ?? 20,
          total,
          onPageChange: (page) => setFilters((prev) => ({ ...prev, pageNum: page })),
        }}
      />

      {deleting ? (
        <FormDialog
          title={t('contentManagement.messages.deleteConfirmTitle')}
          closeLabel={t('contentManagement.actions.cancel')}
          onClose={() => setDeleting(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('contentManagement.messages.confirmDelete', { title: deleting.title })}
          </p>
          <div className={adminFormActionsClass}>
            <button
              type="button"
              className={adminSecondaryButtonClass}
              onClick={() => setDeleting(null)}
            >
              {t('contentManagement.actions.cancel')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(deleting.id)}
            >
              {deleteMutation.isPending ? <Spinner /> : null}
              {t('contentManagement.actions.delete')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </section>
  )
}
