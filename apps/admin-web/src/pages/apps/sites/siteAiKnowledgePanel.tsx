import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { DataTable, type DataTableColumn } from '@/components/admin'
import { AI_KB_STATUS, AI_KB_STATUS_VALUES, type AiKnowledgeListItem, type AiKnowledgeStatus } from '@/types/ai'
import { PermissionGate } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

interface EditorState {
  mode: 'create' | 'edit'
  id?: number
  version?: number
  title: string
  content: string
  status: (typeof AI_KB_STATUS)[keyof typeof AI_KB_STATUS]
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

  const deleteMutation = useMutation({
    mutationFn: (id: number) => aiApi.deleteKnowledgeDocument(siteId, id),
    onSuccess: () => {
      toast.success(t('aiWorkspace.knowledge.deleted'))
      void queryClient.invalidateQueries({ queryKey: ['sites', siteId, 'ai', 'knowledge'] })
    },
  })

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
    </div>
  )
}
