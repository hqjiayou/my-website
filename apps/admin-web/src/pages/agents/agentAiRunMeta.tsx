import { useTranslation } from 'react-i18next'

import type { AiCitation, AiRunMeta, AiRunUsage } from '@/types/ai'

/** spec（provider:model）取冒号后模型段，与执行层回报的裸 model_name 同口径比较。 */
function modelPartOfSpec(spec: string): string {
  const idx = spec.indexOf(':')
  return idx >= 0 ? spec.slice(idx + 1) : spec
}

export function AiRunUsageLine({ usage }: { usage: AiRunUsage }) {
  const { t } = useTranslation('sites')
  return <p className="font-mono text-xs text-admin-muted">{t('aiWorkspace.runMeta.usage', { ...usage })}</p>
}

/** 会话消息下的运行元数据：用量 / 工具轨迹 / 生效 skills（仅后台观测）。 */
export function AiRunMetaSummary({ meta }: { meta: AiRunMeta }) {
  const { t } = useTranslation('sites')
  return (
    <div className="mt-2 space-y-1 border-t border-admin pt-2">
      <AiRunUsageLine usage={meta.usage} />
      {meta.tools.length ? (
        <ul className="flex flex-wrap gap-1">
          {meta.tools.map((tool, index) => (
            <li
              key={`${tool.name}-${index}`}
              className={`rounded px-1.5 py-0.5 font-mono text-xs ${
                tool.ok ? 'bg-success-muted text-success-emphasis' : 'bg-danger-muted text-danger-emphasis'
              }`}
              title={t(`aiWorkspace.preview.run.toolState.${tool.ok ? 'ok' : 'failed'}`)}
            >
              {tool.name}
            </li>
          ))}
        </ul>
      ) : null}
      {meta.skillsApplied.length ? (
        <p className="text-xs text-admin-muted">
          {t('aiWorkspace.runMeta.skills', { ids: meta.skillsApplied.join(', ') })}
        </p>
      ) : null}
      {meta.model ? (
        <p className="font-mono text-xs text-admin-muted">
          {t('aiWorkspace.runMeta.modelActual', { model: meta.model })}
        </p>
      ) : null}
      {meta.requestedModel && meta.model && modelPartOfSpec(meta.requestedModel) !== meta.model ? (
        <p className="font-mono text-xs text-admin-muted">
          {t('aiWorkspace.runMeta.modelRequested', { model: meta.requestedModel })}
        </p>
      ) : null}
      {meta.mode ? (
        <p className="text-xs text-admin-muted">{t('aiWorkspace.runMeta.mode', { mode: meta.mode })}</p>
      ) : null}
      {meta.citations?.length ? (
        <ul className="space-y-0.5">
          {meta.citations.map((citation, index) => (
            <li key={`${citation.sourceId}-${citation.chunkIndex}-${index}`} className="text-xs text-admin-muted">
              {t('aiWorkspace.runMeta.citation', {
                n: citation.cite ?? index + 1,
                title: citation.title || citation.sourceId,
                location: citationLocationText(citation),
              })}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

function citationLocationText(citation: AiCitation): string {
  const page = citation.location?.page
  const text = typeof page === 'number' && page > 0 ? `第 ${page} 页` : (citation.location?.sheet ?? '')
  return text ? ` · ${text}` : ''
}
