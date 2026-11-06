import { ChevronDown } from 'lucide-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'

import { skillCatalogDescription } from './agentAiSkillCatalogText'

/**
 * Skill 只读行：默认折叠说明；ChevronDown + rotate-180（与侧栏／预览面板同交互）。
 * 勾选态由调用方另加 checkbox，不塞进本组件。
 */
export function SkillCatalogItem({
  id,
  version,
  description,
  titleClassName = 'font-mono text-xs font-medium text-foreground',
  descriptionClassName = 'mt-1 text-xs leading-5 text-muted-foreground',
  versionClassName = 'ml-1.5 font-normal text-muted-foreground',
}: {
  id: string
  version?: string | null
  description?: string | null
  titleClassName?: string
  descriptionClassName?: string
  versionClassName?: string
}) {
  const { t } = useTranslation('sites')
  const [expanded, setExpanded] = useState(false)
  const text = skillCatalogDescription(id, description, t)

  if (!text) {
    return (
      <p className={titleClassName}>
        {id}
        {version ? <span className={versionClassName}>v{version}</span> : null}
      </p>
    )
  }

  return (
    <div>
      <button
        type="button"
        className="admin-focus-ring flex w-full items-center gap-1 rounded-md text-left"
        aria-expanded={expanded}
        onClick={() => setExpanded((value) => !value)}
      >
        <span className={`min-w-0 flex-1 truncate ${titleClassName}`}>
          {id}
          {version ? <span className={versionClassName}>v{version}</span> : null}
        </span>
        <ChevronDown
          className={`h-3.5 w-3.5 shrink-0 text-muted-foreground transition-transform duration-150 ${
            expanded ? 'rotate-180' : ''
          }`}
          aria-hidden
        />
      </button>
      {expanded ? <p className={descriptionClassName}>{text}</p> : null}
    </div>
  )
}
