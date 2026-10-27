import { useState } from 'react'

import type { SiteSearchSummary } from '@/types/api'

/**
 * 一轮回复的过程步骤：模型思考与工具调用。
 * 数据只来自 SSE 过程事件（包1 契约）：思考增量、工具名与状态、
 * 知识搜索的安全摘要。不含工具返回正文。
 */
export type ProcessStep =
  | { kind: 'thinking'; id: string; text: string }
  | {
      kind: 'tool'
      id: string
      callId: string
      name: string
      status: 'running' | 'ok' | 'failed'
      summary?: SiteSearchSummary
    }

const TOOL_LABELS: Record<string, string> = {
  search_site_knowledge: '檢索知識庫',
  get_site_info: '查詢站點資訊',
  list_site_contents: '列出站點內容',
}

function toolLabel(name: string): string {
  return TOOL_LABELS[name] ?? name
}

function summarize(steps: ProcessStep[]): string {
  const parts: string[] = []
  if (steps.some((step) => step.kind === 'thinking')) parts.push('思考')
  let otherTools = 0
  for (const step of steps) {
    if (step.kind !== 'tool') continue
    if (step.name === 'search_site_knowledge' && step.summary) {
      parts.push(`檢索「${step.summary.query}」命中 ${step.summary.hitCount} 篇`)
    } else if (step.name === 'search_site_knowledge') {
      parts.push('檢索知識庫')
    } else {
      otherTools += 1
    }
  }
  if (otherTools > 0) parts.push(`工具 ${otherTools} 次`)
  return parts.join(' · ') || '處理過程'
}

function ToolDetail({ step }: { step: Extract<ProcessStep, { kind: 'tool' }> }) {
  if (step.name === 'search_site_knowledge' && step.summary) {
    return (
      <span className="text-gpt-muted">
        「{step.summary.query}」· 命中 {step.summary.hitCount} 篇
      </span>
    )
  }
  return null
}

export function ProcessTimeline({ steps, live }: { steps: ProcessStep[]; live: boolean }) {
  const [open, setOpen] = useState(false)
  if (steps.length === 0) return null
  const expanded = live || open

  if (!expanded) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        aria-expanded={false}
        className="mb-2 flex max-w-full items-center gap-2 rounded-full bg-gpt-sidebar px-3 py-1.5 text-xs text-gpt-muted hover:bg-gpt-hover"
      >
        <span className="truncate">{summarize(steps)}</span>
        <span aria-hidden="true">▾</span>
      </button>
    )
  }

  return (
    <div className="mb-3 rounded-2xl border border-gpt-line px-3 py-2.5">
      {!live ? (
        <button
          type="button"
          onClick={() => setOpen(false)}
          aria-expanded
          className="mb-1 flex items-center gap-2 text-xs text-gpt-muted hover:text-gpt-ink"
        >
          <span>{summarize(steps)}</span>
          <span aria-hidden="true">▴</span>
        </button>
      ) : null}
      <ol className="flex flex-col gap-2.5">
        {steps.map((step) =>
          step.kind === 'thinking' ? (
            <li key={step.id}>
              <p className="text-xs font-medium text-gpt-muted">思考</p>
              <div className="mt-1 max-h-44 overflow-y-auto whitespace-pre-wrap break-words text-xs leading-5 text-gpt-muted">
                {step.text}
                {live ? <span className="ml-0.5 inline-block h-3 w-1.5 animate-pulse bg-gpt-muted align-middle" /> : null}
              </div>
            </li>
          ) : (
            <li key={step.id} className="flex items-center gap-2 text-xs">
              <span
                aria-hidden="true"
                className={
                  step.status === 'running'
                    ? 'h-2 w-2 shrink-0 animate-pulse rounded-full bg-primary'
                    : step.status === 'ok'
                      ? 'flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-full bg-primary-soft text-[9px] text-primary'
                      : 'flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-full bg-danger-soft text-[9px] text-danger'
                }
              >
                {step.status === 'running' ? null : step.status === 'ok' ? '✓' : '✕'}
              </span>
              <span className="font-medium">{toolLabel(step.name)}</span>
              <ToolDetail step={step} />
              {step.status === 'failed' ? <span className="text-danger">失敗</span> : null}
            </li>
          ),
        )}
      </ol>
    </div>
  )
}
