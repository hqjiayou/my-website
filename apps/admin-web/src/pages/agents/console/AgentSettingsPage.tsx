import { ChevronRight } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Link, useOutletContext } from 'react-router-dom'

import { ROUTES } from '@/router/config/routePaths'

import { AgentAccessPage } from '../agentAccessPage'
import { AgentBasicPage } from '../agentBasicPage'
import type { AgentWorkspaceOutletContext } from '../agentWorkspaceLayout'

/**
 * 设置聚合页：Basic + Access（只读平台入口参考）就地展示；
 * Theme / Contents / Files 以高级入口保留，避免丢功能。
 * 生命周期（启停/删除）保留在工作区「更多」菜单，确认文案含 Agent 名。
 */
export function AgentSettingsPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()

  const advanced = [
    { key: 'theme', to: ROUTES.apps.agentTheme(site.id), labelKey: 'workspace.nav.theme' },
    { key: 'contents', to: ROUTES.apps.agentContents(site.id), labelKey: 'workspace.nav.contents' },
    { key: 'files', to: ROUTES.apps.agentFiles(site.id), labelKey: 'workspace.nav.files' },
  ] as const

  return (
    <div className="space-y-4">
      {/* Agent 名/状态已在工作区信息带；此处 Basic 区块承载可编辑基础信息，避免再叠一层大标题。 */}
      <section className="space-y-1.5">
        <h2 className="text-sm font-semibold text-admin">{t('workspace.settings.basicSection')}</h2>
        <AgentBasicPage />
      </section>

      <section className="space-y-1.5">
        <h2 className="text-sm font-semibold text-admin">{t('workspace.settings.accessSection')}</h2>
        <p className="text-xs text-admin-muted">{t('workspace.settings.accessHint')}</p>
        <AgentAccessPage />
      </section>

      <section className="rounded-xl border border-admin bg-admin-surface px-4 py-3 sm:px-5">
        <h2 className="text-sm font-semibold text-admin">{t('workspace.settings.advancedSection')}</h2>
        <p className="mt-0.5 text-xs text-admin-muted">{t('workspace.settings.advancedHint')}</p>
        <ul className="mt-2 divide-y divide-border">
          {advanced.map((item) => (
            <li key={item.key}>
              <Link
                to={item.to}
                className="flex items-center justify-between gap-3 py-2.5 text-sm font-medium text-admin transition hover:text-primary"
              >
                <span>{t(item.labelKey)}</span>
                <ChevronRight className="h-4 w-4 text-admin-muted" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
