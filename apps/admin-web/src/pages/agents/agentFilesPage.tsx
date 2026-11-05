import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Upload } from 'lucide-react'
import { useMemo, useRef, useState, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'
import { toast } from 'sonner'

import {
  MEDIA_ACCEPT,
  MEDIA_ALLOWED_CONTENT_TYPES,
  MEDIA_SCOPE,
  MEDIA_STATUS,
  mediaApi,
  type MediaListItem,
  type MediaListQuery,
  type MediaListView,
  type MediaScope,
} from '@/api'
import { DataTable, type DataTableColumn } from '@/components/admin'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

function formatFileSize(bytes: number) {
  if (!Number.isFinite(bytes) || bytes < 0) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function statusClassName(status: string) {
  if (status === MEDIA_STATUS.READY) return 'border-success/35 bg-success-muted text-success-emphasis'
  if (status === MEDIA_STATUS.FAILED) return 'border-danger/35 bg-danger-muted text-danger-emphasis'
  return 'border-warning/35 bg-warning-muted text-warning-emphasis'
}

export function AgentFilesPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const canUpload = useHasPermission(PERMISSION_CODES.mediaUpload)
  const canDownload = useHasPermission(PERMISSION_CODES.mediaDownload)
  const canDelete = useHasPermission(PERMISSION_CODES.mediaDelete)

  const [view, setView] = useState<MediaListView>('library')
  const [filters, setFilters] = useState<MediaListQuery>({ pageNum: 1, pageSize: 20, view: 'library' })

  function switchView(next: MediaListView) {
    setView(next)
    setFilters((prev) => ({ ...prev, pageNum: 1, view: next }))
  }

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['sites', site.id, 'media', filters],
    queryFn: () => mediaApi.listMedia(site.id, filters),
  })

  const records = data?.records ?? []
  const total = data?.total ?? 0

  const uploadMutation = useMutation({
    mutationFn: (file: File) => mediaApi.uploadMedia(site.id, file),
    onSuccess: () => {
      toast.success(t('filesManagement.messages.uploaded'))
      void queryClient.invalidateQueries({ queryKey: ['sites', site.id, 'media'] })
      if (fileInputRef.current) fileInputRef.current.value = ''
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const previewMutation = useMutation({
    mutationFn: (id: number) => mediaApi.previewMedia(site.id, id),
    onSuccess: (preview) => {
      if (!preview.previewUrl) {
        toast.error(t('filesManagement.messages.previewFailed'))
        return
      }
      window.open(preview.previewUrl, '_blank', 'noopener,noreferrer')
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => mediaApi.deleteMedia(site.id, id),
    onSuccess: () => {
      toast.success(t('filesManagement.messages.deleted'))
      void queryClient.invalidateQueries({ queryKey: ['sites', site.id, 'media'] })
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const referenceMutation = useMutation({
    mutationFn: (id: number) => mediaApi.referenceMedia(site.id, id),
    onSuccess: () => {
      toast.success(t('filesManagement.messages.referenced'))
      void queryClient.invalidateQueries({ queryKey: ['sites', site.id, 'media'] })
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const unreferenceMutation = useMutation({
    mutationFn: (id: number) => mediaApi.unreferenceMedia(site.id, id),
    onSuccess: () => {
      toast.success(t('filesManagement.messages.unreferenced'))
      void queryClient.invalidateQueries({ queryKey: ['sites', site.id, 'media'] })
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const scopeMutation = useMutation({
    mutationFn: ({ id, scope }: { id: number; scope: MediaScope }) =>
      mediaApi.setMediaScope(site.id, id, scope),
    onSuccess: () => {
      toast.success(t('filesManagement.messages.scopeUpdated'))
      void queryClient.invalidateQueries({ queryKey: ['sites', site.id, 'media'] })
    },
    onError: (err: Error) => toast.error(err.message),
  })

  function onPickFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return

    const allowed = (MEDIA_ALLOWED_CONTENT_TYPES as readonly string[]).includes(file.type)
    if (!allowed) {
      toast.error(t('filesManagement.messages.invalidType'))
      event.target.value = ''
      return
    }

    uploadMutation.mutate(file)
  }

  const columns = useMemo<DataTableColumn<MediaListItem>[]>(
    () => [
      {
        id: 'filename',
        header: t('filesManagement.columns.filename'),
        cell: (item) => (
          <div>
            <p className="font-medium text-foreground">{item.originalFilename}</p>
            <p className="mt-1 text-xs text-muted-foreground">
              #{item.id}
              {item.scope === MEDIA_SCOPE.PLATFORM ? ` · ${t('filesManagement.scope.platform')}` : ''}
            </p>
            {item.agentId !== site.id ? (
              <p className="mt-1 text-xs text-muted-foreground">
                {t('filesManagement.source.fromAgent', { agentId: item.agentId })}
              </p>
            ) : null}
          </div>
        ),
      },
      {
        id: 'contentType',
        header: t('filesManagement.columns.contentType'),
        cell: (item) => item.contentType || '—',
      },
      {
        id: 'fileSize',
        header: t('filesManagement.columns.fileSize'),
        cell: (item) => formatFileSize(item.fileSize),
      },
      {
        id: 'status',
        header: t('filesManagement.columns.status'),
        cell: (item) => (
          <span
            className={cn(
              'inline-flex rounded-md border px-2 py-1 text-xs font-medium',
              statusClassName(item.status),
            )}
          >
            {t(`filesManagement.status.${item.status}`, { defaultValue: item.status })}
          </span>
        ),
      },
      {
        id: 'createdAt',
        header: t('filesManagement.columns.createdAt'),
        cell: (item) => new Date(item.createdAt).toLocaleString(),
      },
      {
        id: 'actions',
        header: t('filesManagement.columns.actions'),
        cell: (item) => {
          const own = item.agentId === site.id
          return (
            <div className="flex flex-wrap gap-2">
              {canDownload ? (
                <button
                  type="button"
                  disabled={item.status !== MEDIA_STATUS.READY || previewMutation.isPending}
                  onClick={() => previewMutation.mutate(item.id)}
                  className="rounded-md border border-primary/35 bg-info-muted px-2.5 py-1.5 text-xs font-medium text-primary transition hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {t('filesManagement.actions.preview')}
                </button>
              ) : null}
              {canDelete && !own && !item.referenced ? (
                <button
                  type="button"
                  disabled={referenceMutation.isPending}
                  onClick={() => referenceMutation.mutate(item.id)}
                  className="rounded-md border border-primary/35 px-2.5 py-1.5 text-xs font-medium text-primary transition hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {t('filesManagement.actions.reference')}
                </button>
              ) : null}
              {canDelete && !own && item.referenced ? (
                <button
                  type="button"
                  disabled={unreferenceMutation.isPending}
                  onClick={() => unreferenceMutation.mutate(item.id)}
                  className="rounded-md border border-warning/35 px-2.5 py-1.5 text-xs font-medium text-warning-emphasis transition hover:bg-warning-muted disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {t('filesManagement.actions.unreference')}
                </button>
              ) : null}
              {canDelete && own ? (
                <button
                  type="button"
                  disabled={scopeMutation.isPending}
                  onClick={() =>
                    scopeMutation.mutate({
                      id: item.id,
                      scope:
                        item.scope === MEDIA_SCOPE.PLATFORM ? MEDIA_SCOPE.AGENT : MEDIA_SCOPE.PLATFORM,
                    })
                  }
                  className="rounded-md border border-primary/35 px-2.5 py-1.5 text-xs font-medium text-primary transition hover:bg-primary/10 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {item.scope === MEDIA_SCOPE.PLATFORM
                    ? t('filesManagement.actions.setScopeAgent')
                    : t('filesManagement.actions.setScopePlatform')}
                </button>
              ) : null}
              {canDelete && own ? (
                <button
                  type="button"
                  disabled={deleteMutation.isPending}
                  onClick={() => {
                    if (
                      !window.confirm(
                        t('filesManagement.messages.confirmDelete', {
                          name: item.originalFilename,
                        }),
                      )
                    ) {
                      return
                    }
                    deleteMutation.mutate(item.id)
                  }}
                  className="rounded-md border border-danger/35 px-2.5 py-1.5 text-xs font-medium text-danger-emphasis transition hover:bg-danger-muted disabled:cursor-not-allowed disabled:opacity-50"
                >
                  {t('filesManagement.actions.delete')}
                </button>
              ) : null}
            </div>
          )
        },
      },
    ],
    [
      canDelete,
      canDownload,
      deleteMutation,
      previewMutation,
      referenceMutation,
      scopeMutation,
      site.id,
      t,
      unreferenceMutation,
    ],
  )

  return (
    <section className="w-full">
      <div className="mb-3 flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm text-muted-foreground">{t('filesManagement.description')}</p>
          <p className="mt-1 text-xs text-muted-foreground">
            {t('filesManagement.hints.whitelist')}
            {canUpload ? null : ` ${t('filesManagement.hints.uploadPermission')}`}
          </p>
        </div>
        <PermissionGate code={PERMISSION_CODES.mediaUpload}>
          <>
            <input
              ref={fileInputRef}
              type="file"
              accept={MEDIA_ACCEPT}
              className="hidden"
              onChange={onPickFile}
            />
            <button
              type="button"
              disabled={uploadMutation.isPending}
              onClick={() => fileInputRef.current?.click()}
              className="inline-flex shrink-0 items-center gap-2 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/92 disabled:cursor-not-allowed disabled:opacity-60"
            >
              <Upload className="h-4 w-4" />
              <span>
                {uploadMutation.isPending
                  ? t('filesManagement.actions.uploading')
                  : t('filesManagement.actions.upload')}
              </span>
            </button>
          </>
        </PermissionGate>
      </div>

      <div className="mb-3 flex flex-wrap gap-2">
        {(['library', 'platform'] as MediaListView[]).map((v) => (
          <button
            key={v}
            type="button"
            onClick={() => switchView(v)}
            className={cn(
              'rounded-md border px-3 py-1.5 text-xs font-medium transition',
              view === v
                ? 'border-primary/40 bg-primary/10 text-primary'
                : 'border-border bg-background text-muted-foreground hover:bg-muted',
            )}
          >
            {t(`filesManagement.views.${v}`)}
          </button>
        ))}
      </div>

      <DataTable
        columns={columns}
        data={records}
        rowKey={(item) => item.id}
        isLoading={isPending}
        isError={isError}
        errorMessage={error instanceof Error ? error.message : undefined}
        emptyMessage={t('filesManagement.empty')}
        onRetry={() => void refetch()}
        pagination={{
          page: filters.pageNum ?? 1,
          pageSize: filters.pageSize ?? 20,
          total,
          onPageChange: (page) => setFilters((prev) => ({ ...prev, pageNum: page })),
        }}
      />
    </section>
  )
}
