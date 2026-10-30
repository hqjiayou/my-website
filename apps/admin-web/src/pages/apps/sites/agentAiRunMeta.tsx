import { useTranslation } from 'react-i18next'

import type { AiRunMeta, AiRunUsage } from '@/types/ai'

export function AiRunUsageLine({ usage }: { usage: AiRunUsage }) {
  const { t } = useTranslation('sites')
  return <p className="font-mono text-xs text-muted-foreground">{t('aiWorkspace.runMeta.usage', { ...usage })}</p>
}

/** 会话消息下的运行元数据：用量 / 工具轨迹 / 生效 skills（仅后台观测）。 */
export function AiRunMetaSummary({ meta }: { meta: AiRunMeta }) {
  const { t } = useTranslation('sites')
  return (
    <div className="mt-2 space-y-1 border-t border-border pt-2">
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
        <p className="text-xs text-muted-foreground">
          {t('aiWorkspace.runMeta.skills', { ids: meta.skillsApplied.join(', ') })}
        </p>
      ) : null}
    </div>
  )
}
