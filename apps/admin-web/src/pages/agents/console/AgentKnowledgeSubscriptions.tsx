import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { FormDialog, ResourcePickerDrawer, StatusTag } from '@/components/admin'
import {
  adminDangerButtonMdClass,
  adminFormActionsClass,
  adminGhostButtonClass,
  adminSecondaryButtonClass,
} from '@/components/admin/controls'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type { AiKnowledgeBase } from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

type LinkBatchResult = {
  succeeded: number[]
  failed: Array<{ id: number; message: string }>
}

/**
 * Agent 知识空间关联：与文档管理分离。
 * 抽屉确认后立刻调用 link（关联本身即服务端写）；解绑需二次确认且不删平台空间。
 * 批量关联用 allSettled：部分成功如实反馈并刷新，不把半成功伪装成全失败。
 */
export function AgentKnowledgeSubscriptions({ agentId }: { agentId: number }) {
  const { t } = useTranslation('sites')
  const queryClient = useQueryClient()
  const canManage = useHasPermission(PERMISSION_CODES.aiKbUpdate)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [unlinkTarget, setUnlinkTarget] = useState<AiKnowledgeBase | null>(null)

  const basesQuery = useQuery({
    queryKey: ['ai-knowledge-bases-all'],
    queryFn: () => aiApi.listAllKnowledgeBases(),
  })

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['ai-knowledge-bases-all'] })
    void queryClient.invalidateQueries({ queryKey: ['ai-knowledge-overview'] })
    void queryClient.invalidateQueries({ queryKey: ['sites', agentId, 'ai', 'knowledge'] })
    void queryClient.invalidateQueries({ queryKey: ['sites'] })
  }

  const linkMutation = useMutation({
    mutationFn: async (ids: number[]): Promise<LinkBatchResult> => {
      const unique = [...new Set(ids)]
      const settled = await Promise.allSettled(
        unique.map((baseId) => aiApi.linkKnowledgeBase(baseId, agentId)),
      )
      const succeeded: number[] = []
      const failed: Array<{ id: number; message: string }> = []
      settled.forEach((result, index) => {
        const id = unique[index]
        if (result.status === 'fulfilled') {
          succeeded.push(id)
          return
        }
        const reason = result.reason
        failed.push({
          id,
          message: reason instanceof Error ? reason.message : String(reason ?? 'error'),
        })
      })
      return { succeeded, failed }
    },
    onSuccess: (result) => {
      invalidate()
      const nameOf = (id: number) =>
        basesQuery.data?.find((base) => base.id === id)?.name ?? `#${id}`
      if (result.failed.length === 0) {
        toast.success(t('list.messages.subscribed'))
        setPickerOpen(false)
        return
      }
      if (result.succeeded.length === 0) {
        toast.error(
          t('workspace.configKnowledge.linkAllFailed', { failed: result.failed.length }),
        )
        return
      }
      toast.warning(
        t('workspace.configKnowledge.linkPartial', {
          ok: result.succeeded.length,
          failed: result.failed.length,
          names: result.failed.map((item) => nameOf(item.id)).join(', '),
        }),
      )
      // 已成功项会从 linkable 消失；保留抽屉以便对失败项重试，不重复打成功项。
    },
    onError: (error) => {
      invalidate()
      toast.error(error instanceof Error ? error.message : t('list.messages.subscribed'))
    },
  })

  const unlinkMutation = useMutation({
    mutationFn: (baseId: number) => aiApi.unlinkKnowledgeBase(baseId, agentId),
    onSuccess: () => {
      toast.success(t('list.messages.unsubscribed'))
      setUnlinkTarget(null)
      invalidate()
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : t('list.messages.unsubscribed')),
  })

  const bases = useMemo(
    () => [...(basesQuery.data ?? [])].sort((a, b) => a.name.localeCompare(b.name)),
    [basesQuery.data],
  )

  const linked = bases.filter((base) => base.agentId === agentId || base.linkedAgentIds.includes(agentId))
  const linkable = bases.filter((base) => base.agentId !== agentId && !base.linkedAgentIds.includes(agentId))

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="text-sm font-semibold text-admin">
            {t('workspace.configKnowledge.subscriptionsTitle')}
          </h3>
          <p className="mt-1 text-xs text-admin-muted">{t('workspace.configKnowledge.subscriptionsHint')}</p>
        </div>
        <PermissionGate code={PERMISSION_CODES.aiKbUpdate}>
          <button
            type="button"
            className={adminSecondaryButtonClass}
            disabled={!canManage || linkMutation.isPending}
            onClick={() => setPickerOpen(true)}
          >
            <Plus className="h-4 w-4" aria-hidden />
            {t('workspace.configKnowledge.addSubscription')}
          </button>
        </PermissionGate>
      </div>

      {basesQuery.isPending ? (
        <p className="text-sm text-admin-muted">{t('workspace.loading')}</p>
      ) : basesQuery.isError ? (
        <div className="text-sm text-danger-emphasis">
          <p>{basesQuery.error instanceof Error ? basesQuery.error.message : t('list.subscriptions.empty')}</p>
          <button type="button" className="mt-1 underline" onClick={() => void basesQuery.refetch()}>
            {t('aiWorkspace.retry')}
          </button>
        </div>
      ) : linked.length === 0 ? (
        <p className="text-sm text-admin-muted">{t('workspace.configKnowledge.noLinked')}</p>
      ) : (
        <ul className="divide-y divide-border rounded-lg border border-admin">
          {linked.map((base) => {
            const owned = base.agentId === agentId
            return (
              <li key={base.id} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2.5">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2 text-sm font-medium text-admin">
                    {base.name}
                    {owned ? (
                      <StatusTag status="info" tone="info" label={t('list.subscriptions.own')} />
                    ) : (
                      <StatusTag status="neutral" tone="neutral" label={t('workspace.configKnowledge.linkedTag')} />
                    )}
                  </div>
                  <p className="text-xs text-admin-muted">
                    {t('list.subscriptions.owner', { name: base.ownerAgentName ?? `#${base.agentId}` })}
                    {' · '}
                    {t('list.subscriptions.documents', { count: base.documentCount })}
                  </p>
                </div>
                {!owned && canManage ? (
                  <button
                    type="button"
                    className={adminGhostButtonClass}
                    disabled={unlinkMutation.isPending}
                    onClick={() => setUnlinkTarget(base)}
                  >
                    {t('workspace.configKnowledge.unlink')}
                  </button>
                ) : null}
              </li>
            )
          })}
        </ul>
      )}

      <ResourcePickerDrawer
        open={pickerOpen}
        title={t('workspace.configKnowledge.pickerTitle')}
        description={t('workspace.configKnowledge.pickerHint')}
        items={linkable}
        getId={(base) => String(base.id)}
        getAccessibleName={(base) => base.name}
        alreadySelectedIds={linked.map((base) => String(base.id))}
        alreadySelectedLabel={t('workspace.configKnowledge.linkedTag')}
        searchText={(base) => `${base.name} ${base.ownerAgentName ?? ''} ${base.id}`}
        columns={[
          {
            id: 'name',
            header: t('workspace.configKnowledge.colName'),
            cell: (base) => <span className="font-medium">{base.name}</span>,
          },
          {
            id: 'docs',
            header: t('workspace.configKnowledge.colDocs'),
            cell: (base) => t('list.subscriptions.documents', { count: base.documentCount }),
          },
          {
            id: 'owner',
            header: t('workspace.configKnowledge.colOwner'),
            cell: (base) => base.ownerAgentName ?? `#${base.agentId}`,
          },
        ]}
        isLoading={basesQuery.isPending}
        isError={basesQuery.isError}
        onRetry={() => void basesQuery.refetch()}
        confirmLabel={t('workspace.configKnowledge.confirmLink')}
        onConfirm={(ids) => {
          const linkedSet = new Set(linked.map((base) => base.id))
          const numeric = ids
            .map(Number)
            .filter((id) => Number.isFinite(id) && !linkedSet.has(id))
          if (numeric.length === 0) {
            setPickerOpen(false)
            return
          }
          linkMutation.mutate(numeric)
        }}
        onClose={() => setPickerOpen(false)}
      />

      {unlinkTarget ? (
        <FormDialog
          title={t('workspace.configKnowledge.unlinkTitle')}
          closeLabel={t('create.close')}
          onClose={() => setUnlinkTarget(null)}
          size="s"
          danger
        >
          <p className="text-sm text-admin">
            {t('workspace.configKnowledge.unlinkBody', { name: unlinkTarget.name })}
          </p>
          <div className={adminFormActionsClass}>
            <button type="button" className={adminSecondaryButtonClass} onClick={() => setUnlinkTarget(null)}>
              {t('create.close')}
            </button>
            <button
              type="button"
              className={adminDangerButtonMdClass}
              disabled={unlinkMutation.isPending}
              onClick={() => unlinkMutation.mutate(unlinkTarget.id)}
            >
              {t('workspace.configKnowledge.unlink')}
            </button>
          </div>
        </FormDialog>
      ) : null}
    </div>
  )
}
