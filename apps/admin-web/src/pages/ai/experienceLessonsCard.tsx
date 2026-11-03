import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, ChevronRight, Sparkles } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { aiApi } from '@/api/apps/aiApi'
import { StatusTag } from '@/components/admin'
import {
  adminDangerGhostButtonSmClass,
  adminFieldClass,
  adminGhostButtonClass,
  adminPrimaryButtonClass,
} from '@/components/admin/controls'
import { cn } from '@/lib/cn'
import { PermissionGate, useHasPermission } from '@/router/permission'
import type {
  AiExperienceExtractionResult,
  AiExperienceLesson,
  AiExperienceLessonApproveInput,
  AiExperienceLessonListItem,
  AiExperienceLessonStatus,
} from '@/types/ai'
import { PERMISSION_CODES } from '@/types/rbac'

const STATUSES: AiExperienceLessonStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'DISABLED']

const STATUS_TONE: Record<AiExperienceLessonStatus, 'warning' | 'active' | 'danger' | 'disabled'> = {
  PENDING: 'warning',
  APPROVED: 'active',
  REJECTED: 'danger',
  DISABLED: 'disabled',
}

/**
 * 经验沉淀人审（Issue #184 UoW-3，P1/P2 定案）：AI 设置页的一节。
 * 候选来自提炼管线（正式对话信号预筛）或手动新建；人审批准（可改写）后分配
 * L 编号。批准条目的下发组装在 UoW-4，本节只负责审与触发提炼。
 * 写接口后端校验 admin:ai-model:update，与运行配置同口径（aiRuntimeConfigUpdate）。
 */
export function ExperienceLessonsCard() {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const canUpdate = useHasPermission(PERMISSION_CODES.aiRuntimeConfigUpdate)
  const [status, setStatus] = useState<AiExperienceLessonStatus>('PENDING')
  const [expandedId, setExpandedId] = useState<number | null>(null)

  const listQuery = useQuery({
    queryKey: ['ai', 'experience-lessons', status],
    queryFn: () => aiApi.listExperienceLessons(status),
  })

  function invalidateAll() {
    void queryClient.invalidateQueries({ queryKey: ['ai', 'experience-lessons'] })
  }

  const items = listQuery.data ?? []

  return (
    <article className="space-y-3 rounded-xl border border-admin bg-admin-surface p-5">
      <h3 className="text-sm font-semibold text-admin">{t('settings.experience.title')}</h3>
      <p className="text-sm text-admin-muted">{t('settings.experience.description')}</p>

      <div className="flex flex-wrap gap-2" role="tablist" aria-label={t('settings.experience.title')}>
        {STATUSES.map((item) => (
          <button
            key={item}
            type="button"
            role="tab"
            aria-selected={status === item}
            className={cn(
              adminGhostButtonClass,
              status === item && 'border-primary/45 bg-primary/7 font-semibold text-primary',
            )}
            onClick={() => {
              setStatus(item)
              setExpandedId(null)
            }}
          >
            {t(`settings.experience.tab.${item}`)}
          </button>
        ))}
      </div>

      {listQuery.isPending ? (
        <p className="text-sm text-admin-muted">{t('loading')}</p>
      ) : listQuery.isError ? (
        <div className="rounded-lg border border-danger/35 bg-danger-muted px-4 py-3 text-sm text-danger-emphasis">
          <p>{listQuery.error instanceof Error ? listQuery.error.message : t('error')}</p>
          <button type="button" className="mt-2 underline" onClick={() => void listQuery.refetch()}>
            {t('retry')}
          </button>
        </div>
      ) : items.length === 0 ? (
        <p className="text-sm text-admin-muted">
          {status === 'PENDING' ? t('settings.experience.emptyPending') : t('settings.experience.emptyOther')}
        </p>
      ) : (
        <ul className="space-y-2">
          {items.map((item) => (
            <LessonRow
              key={item.id}
              item={item}
              expanded={expandedId === item.id}
              canUpdate={canUpdate}
              onToggle={() => setExpandedId(expandedId === item.id ? null : item.id)}
              onChanged={invalidateAll}
            />
          ))}
        </ul>
      )}

      <PermissionGate code={PERMISSION_CODES.aiRuntimeConfigUpdate}>
        <ExtractPanel onDone={invalidateAll} />
        <ManualCreateForm
          onCreated={() => {
            setStatus('PENDING')
            invalidateAll()
          }}
        />
      </PermissionGate>
    </article>
  )
}

function LessonRow({
  item,
  expanded,
  canUpdate,
  onToggle,
  onChanged,
}: {
  item: AiExperienceLessonListItem
  expanded: boolean
  canUpdate: boolean
  onToggle: () => void
  onChanged: () => void
}) {
  const { t } = useTranslation('aiAdmin')
  const statusLabel: Record<AiExperienceLessonStatus, string> = {
    PENDING: t('settings.experience.tab.PENDING'),
    APPROVED: t('settings.experience.tab.APPROVED'),
    REJECTED: t('settings.experience.tab.REJECTED'),
    DISABLED: t('settings.experience.tab.DISABLED'),
  }
  return (
    <li className="rounded-lg border border-admin px-3 py-2.5">
      <div className="flex items-start gap-2">
        <button
          type="button"
          className="flex min-w-0 flex-1 items-start gap-2 text-left"
          onClick={onToggle}
          aria-expanded={expanded}
        >
          {expanded ? (
            <ChevronDown className="mt-0.5 h-4 w-4 shrink-0 text-admin-muted" aria-hidden />
          ) : (
            <ChevronRight className="mt-0.5 h-4 w-4 shrink-0 text-admin-muted" aria-hidden />
          )}
          <span className="min-w-0 flex-1">
            <span className="flex flex-wrap items-center gap-2">
              <span className="font-medium text-admin">{item.title}</span>
              {item.lessonNo != null ? (
                <span className="rounded-md bg-admin-surface-muted px-1.5 py-0.5 font-mono text-xs text-admin-muted">
                  {t('settings.experience.lessonNo', { no: item.lessonNo })}
                </span>
              ) : null}
              <StatusTag status={item.status} tone={STATUS_TONE[item.status]} label={statusLabel[item.status]} />
            </span>
            {item.lessonSummary ? (
              <span className="mt-0.5 block truncate text-sm text-admin-muted">{item.lessonSummary}</span>
            ) : null}
            <span className="mt-0.5 block text-xs text-admin-muted">
              {item.sourceAgentId != null
                ? t('settings.experience.sourceAgent', { id: item.sourceAgentId })
                : t('settings.experience.sourceManual')}
              {' · '}
              {t('settings.experience.createdAtTag')} {new Date(item.createdAt).toLocaleString()}
              {item.reviewedAt
                ? ` · ${t('settings.experience.reviewedAtTag')} ${new Date(item.reviewedAt).toLocaleString()}`
                : ''}
            </span>
          </span>
        </button>
      </div>
      {expanded ? <LessonDetail id={item.id} canUpdate={canUpdate} onChanged={onChanged} /> : null}
    </li>
  )
}

/** 展开区：拉详情全文；PENDING 可改写四字段后批准/驳回，APPROVED 可停用。 */
function LessonDetail({ id, canUpdate, onChanged }: { id: number; canUpdate: boolean; onChanged: () => void }) {
  const { t } = useTranslation('aiAdmin')
  const detailQuery = useQuery({
    queryKey: ['ai', 'experience-lessons', 'detail', id],
    queryFn: () => aiApi.getExperienceLesson(id),
  })

  if (detailQuery.isPending) return <p className="mt-3 text-sm text-admin-muted">{t('loading')}</p>
  if (detailQuery.isError) {
    return (
      <p className="mt-3 text-sm text-danger-emphasis">
        {detailQuery.error instanceof Error ? detailQuery.error.message : t('error')}
      </p>
    )
  }
  const detail = detailQuery.data
  if (!detail) return null
  // key 按 detail id＋状态：动作成功后列表刷新会卸载本行，不存在脏草稿残留问题；
  // 详情重拉（同 id）时以服务端最新值重置编辑态。
  return (
    <LessonDetailBody
      key={`${detail.id}-${detail.status}-${detail.updatedAt ?? ''}`}
      detail={detail}
      canUpdate={canUpdate}
      onChanged={onChanged}
    />
  )
}

function LessonDetailBody({
  detail,
  canUpdate,
  onChanged,
}: {
  detail: AiExperienceLesson
  canUpdate: boolean
  onChanged: () => void
}) {
  const { t } = useTranslation('aiAdmin')
  const queryClient = useQueryClient()
  const [title, setTitle] = useState(detail.title)
  const [symptom, setSymptom] = useState(detail.symptom ?? '')
  const [lesson, setLesson] = useState(detail.lesson)
  const [evidence, setEvidence] = useState(detail.evidence ?? '')

  const actionMutation = useMutation({
    mutationFn: (action: 'approve' | 'reject' | 'disable') => {
      if (action === 'approve') {
        const input: AiExperienceLessonApproveInput = {}
        if (title !== detail.title) input.title = title
        if (symptom !== (detail.symptom ?? '')) input.symptom = symptom
        if (lesson !== detail.lesson) input.lesson = lesson
        if (evidence !== (detail.evidence ?? '')) input.evidence = evidence
        return aiApi.approveExperienceLesson(detail.id, input)
      }
      return action === 'reject'
        ? aiApi.rejectExperienceLesson(detail.id)
        : aiApi.disableExperienceLesson(detail.id)
    },
    onSuccess: (updated, action) => {
      toast.success(
        action === 'approve'
          ? t('settings.experience.approvedToast', { no: updated.lessonNo ?? '' })
          : action === 'reject'
            ? t('settings.experience.rejectedToast')
            : t('settings.experience.disabledToast'),
      )
      void queryClient.invalidateQueries({ queryKey: ['ai', 'experience-lessons', 'detail', detail.id] })
      onChanged()
    },
    onError: (actionError: Error) => toast.error(actionError.message),
  })

  function approve() {
    if (!title.trim() || !lesson.trim()) {
      toast.error(t('settings.experience.titleRequired'))
      return
    }
    actionMutation.mutate('approve')
  }

  const editable = canUpdate && detail.status === 'PENDING'

  return (
    <div className="mt-3 space-y-3 border-t border-admin pt-3">
      {detail.status === 'PENDING' && canUpdate ? (
        <>
          <label className="block text-sm text-admin">
            {t('settings.experience.titleField')}
            <input
              className={`${adminFieldClass} mt-1`}
              value={title}
              onChange={(event) => setTitle(event.target.value)}
            />
          </label>
          <TextAreaField
            label={t('settings.experience.symptomField')}
            value={symptom}
            onChange={setSymptom}
          />
          <TextAreaField label={t('settings.experience.lessonField')} value={lesson} onChange={setLesson} />
          <TextAreaField
            label={t('settings.experience.evidenceField')}
            value={evidence}
            onChange={setEvidence}
          />
          <p className="text-xs text-admin-muted">{t('settings.experience.editHint')}</p>
        </>
      ) : (
        <>
          <ReadOnlyField label={t('settings.experience.symptomField')} value={detail.symptom} />
          <ReadOnlyField label={t('settings.experience.lessonField')} value={detail.lesson} />
          <ReadOnlyField label={t('settings.experience.evidenceField')} value={detail.evidence} />
        </>
      )}

      <p className="text-xs text-admin-muted">
        {detail.sourceConversationId != null
          ? `${t('settings.experience.sourceConversation', { id: detail.sourceConversationId })}${
              detail.sourceMessageId != null
                ? ` · ${t('settings.experience.sourceMessage', { id: detail.sourceMessageId })}`
                : ''
            }`
          : t('settings.experience.sourceNone')}
      </p>

      {editable ? (
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            className={adminPrimaryButtonClass}
            disabled={actionMutation.isPending}
            onClick={approve}
          >
            {actionMutation.isPending
              ? t('settings.experience.approving')
              : t('settings.experience.approve')}
          </button>
          <button
            type="button"
            className={adminDangerGhostButtonSmClass}
            disabled={actionMutation.isPending}
            onClick={() => {
              if (!window.confirm(t('settings.experience.confirmReject'))) return
              actionMutation.mutate('reject')
            }}
          >
            {t('settings.experience.reject')}
          </button>
        </div>
      ) : null}

      {canUpdate && detail.status === 'APPROVED' ? (
        <button
          type="button"
          className={adminDangerGhostButtonSmClass}
          disabled={actionMutation.isPending}
          onClick={() => {
            if (!window.confirm(t('settings.experience.confirmDisable'))) return
            actionMutation.mutate('disable')
          }}
        >
          {t('settings.experience.disable')}
        </button>
      ) : null}
    </div>
  )
}

function TextAreaField({
  label,
  value,
  onChange,
}: {
  label: string
  value: string
  onChange: (value: string) => void
}) {
  return (
    <label className="block text-sm text-admin">
      {label}
      <textarea
        className={`${adminFieldClass} mt-1 min-h-20`}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  )
}

function ReadOnlyField({ label, value }: { label: string; value: string | null }) {
  const { t } = useTranslation('aiAdmin')
  return (
    <div>
      <p className="text-xs font-medium text-admin-muted">{label}</p>
      <p className="mt-1 whitespace-pre-wrap text-sm text-admin">{value ?? t('settings.experience.none')}</p>
    </div>
  )
}

/** 提炼触发面板：窗口/上限可改（缺省走服务端默认），结果汇总如实展示（含失败轮数）。 */
function ExtractPanel({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation('aiAdmin')
  const [windowDays, setWindowDays] = useState('14')
  const [maxTurns, setMaxTurns] = useState('40')
  const [result, setResult] = useState<AiExperienceExtractionResult | null>(null)

  const extractMutation = useMutation({
    mutationFn: () =>
      aiApi.extractExperienceLessons({
        windowDays: windowDays.trim() === '' ? null : Number(windowDays),
        maxTurns: maxTurns.trim() === '' ? null : Number(maxTurns),
      }),
    onSuccess: (summary) => {
      setResult(summary)
      toast.success(t('settings.experience.extractedToast'))
      onDone()
    },
    onError: (extractError: Error) => toast.error(extractError.message),
  })

  return (
    <div className="space-y-2 rounded-lg border border-admin px-4 py-3">
      <h4 className="text-sm font-medium text-admin">{t('settings.experience.extractTitle')}</h4>
      <p className="text-sm text-admin-muted">{t('settings.experience.extractDescription')}</p>
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          extractMutation.mutate()
        }}
      >
        <label className="block text-sm text-admin">
          {t('settings.experience.windowDays')}
          <input
            type="number"
            min={1}
            max={90}
            className={`${adminFieldClass} mt-1 w-28`}
            value={windowDays}
            onChange={(event) => setWindowDays(event.target.value)}
          />
        </label>
        <label className="block text-sm text-admin">
          {t('settings.experience.maxTurns')}
          <input
            type="number"
            min={1}
            max={100}
            className={`${adminFieldClass} mt-1 w-28`}
            value={maxTurns}
            onChange={(event) => setMaxTurns(event.target.value)}
          />
        </label>
        <button type="submit" className={adminPrimaryButtonClass} disabled={extractMutation.isPending}>
          <Sparkles className="h-3.5 w-3.5" aria-hidden />
          {extractMutation.isPending
            ? t('settings.experience.extracting')
            : t('settings.experience.extract')}
        </button>
      </form>
      {result ? (
        <p className="text-sm text-admin">
          {t('settings.experience.extractSummary', {
            days: result.windowDays,
            scanned: result.scannedTurns,
            calls: result.extractionCalls,
            produced: result.producedCandidates,
            failed: result.failedTurns,
          })}
        </p>
      ) : null}
    </div>
  )
}

/** 手动新建候选表单：兜底试聊等未落库场景，来源三 id 由后端留空。 */
function ManualCreateForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useTranslation('aiAdmin')
  const [title, setTitle] = useState('')
  const [symptom, setSymptom] = useState('')
  const [lesson, setLesson] = useState('')
  const [evidence, setEvidence] = useState('')

  const createMutation = useMutation({
    mutationFn: () =>
      aiApi.createExperienceLesson({
        title: title.trim(),
        symptom: symptom.trim() || null,
        lesson: lesson.trim(),
        evidence: evidence.trim() || null,
      }),
    onSuccess: () => {
      toast.success(t('settings.experience.createdToast'))
      setTitle('')
      setSymptom('')
      setLesson('')
      setEvidence('')
      onCreated()
    },
    onError: (createError: Error) => toast.error(createError.message),
  })

  const valid = title.trim() !== '' && lesson.trim() !== ''

  return (
    <form
      className="space-y-3 rounded-lg border border-admin px-4 py-3"
      onSubmit={(event) => {
        event.preventDefault()
        if (!valid) return
        createMutation.mutate()
      }}
    >
      <h4 className="text-sm font-medium text-admin">{t('settings.experience.createTitle')}</h4>
      <label className="block text-sm text-admin">
        {t('settings.experience.titleField')}
        <input
          className={`${adminFieldClass} mt-1`}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
        />
      </label>
      <TextAreaField label={t('settings.experience.symptomField')} value={symptom} onChange={setSymptom} />
      <TextAreaField label={t('settings.experience.lessonField')} value={lesson} onChange={setLesson} />
      <TextAreaField label={t('settings.experience.evidenceField')} value={evidence} onChange={setEvidence} />
      <button type="submit" className={adminPrimaryButtonClass} disabled={!valid || createMutation.isPending}>
        {createMutation.isPending ? t('settings.experience.creating') : t('settings.experience.create')}
      </button>
    </form>
  )
}
