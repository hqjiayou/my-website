import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Upload } from 'lucide-react'
import { useMemo, useRef, useState, type ChangeEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'
import { toast } from 'sonner'

import {
  MEDIA_ACCEPT,
  MEDIA_ALLOWED_CONTENT_TYPES,
  MEDIA_STATUS,
  mediaApi,
  type MediaListItem,
  type MediaListQuery,
} from '@/api'
import { DataTable, type DataTableColumn } from '@/components/admin'
import { PageHeader } from '@/components/page'
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
  if (status === MEDIA_STATUS.READY) return 'border-emerald-200 bg-emerald-50 text-emerald-700'
  if (status === MEDIA_STATUS.FAILED) return 'border-rose-200 bg-rose-50 text-rose-700'
  return 'border-amber-200 bg-amber-50 text-amber-700'
}

export function AgentFilesPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()
  const queryClient = useQueryClient()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const canUpload = useHasPermission(PERMISSION_CODES.mediaUpload)
  const canDownload = useHasPermission(PERMISSION_CODES.mediaDownload)
  const canDelete = useHasPermission(PERMISSION_CODES.mediaDelete)

  const [filters, setFilters] = useState<MediaListQuery>({ pageNum: 1, pageSize: 20 })

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
            <p className="font-medium text-slate-900">{item.originalFilename}</p>
            <p className="mt-1 text-xs text-slate-500">#{item.id}</p>
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
        cell: (item) => (
          <div className="flex flex-wrap gap-2">
            {canDownload ? (
              <button
                type="button"
                disabled={item.status !== MEDIA_STATUS.READY || previewMutation.isPending}
                onClick={() => previewMutation.mutate(item.id)}
                className="rounded-md border border-blue-200 bg-blue-50 px-2.5 py-1.5 text-xs font-medium text-blue-700 transition hover:bg-blue-100 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {t('filesManagement.actions.preview')}
              </button>
            ) : null}
            {canDelete ? (
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
                className="rounded-md border border-rose-200 px-2.5 py-1.5 text-xs font-medium text-rose-700 transition hover:bg-rose-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                {t('filesManagement.actions.delete')}
              </button>
            ) : null}
          </div>
        ),
      },
    ],
    [canDelete, canDownload, deleteMutation, previewMutation, t],
  )

  return (
    <section>
      <PageHeader
        title={t('workspace.nav.files')}
        description={t('filesManagement.description')}
        action={
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
                className="inline-flex items-center gap-2 rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60"
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
        }
      />

      <p className="mb-3 text-xs text-slate-500">
        {t('filesManagement.hints.whitelist')}
        {canUpload ? null : ` ${t('filesManagement.hints.uploadPermission')}`}
      </p>

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
