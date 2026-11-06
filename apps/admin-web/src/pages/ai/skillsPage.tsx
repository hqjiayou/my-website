import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import { AdminPageHeader, StatusTag } from '@/components/admin'
import { skillCatalogDescription } from '@/pages/agents/agentAiSkillCatalogText'

/**
 * Skills 货架页（Issue #214 UoW-6）：工具组只读页。技能本体是 agent-services
 * 的文件定义（skills/{id}/SKILL.md），目录经平台级端点直取、不再借某个
 * agent 的上下文（#213 既有缺口）。本页只展示目录元数据与 B 层自选开关的
 * 进程实际状态：不做在线编辑、不做启停写操作；智能体的技能选用在各自的
 * 提示词面板维护。说明文案与预览面同口径：优先 i18n catalog，缺省回退
 * SKILL.md 的 description。
 */
export function AiSkillsPage() {
  const { t } = useTranslation('aiAdmin')
  const { t: tSites } = useTranslation('sites')

  const catalogQuery = useQuery({
    queryKey: ['ai', 'skill-catalog'],
    queryFn: () => aiApi.getSkillCatalog(),
  })

  const catalog = catalogQuery.data
  const skills = catalog?.skills ?? []
  const selfSelectEnabled = catalog?.skillsSelfSelectEnabled ?? false

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('skillsShelf.title')} description={t('skillsShelf.description')} />
      <article className="space-y-3 rounded-xl border border-admin bg-admin-surface p-5">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
          <span className="text-sm font-semibold text-admin">{t('skillsShelf.selfSelect')}</span>
          {catalogQuery.isPending ? (
            <span className="text-sm text-admin-muted">{t('loading')}</span>
          ) : catalogQuery.isError ? (
            <span className="text-sm text-danger-emphasis">{t('skillsShelf.loadFailed')}</span>
          ) : (
            <StatusTag
              status={selfSelectEnabled ? 'ACTIVE' : 'DISABLED'}
              label={selfSelectEnabled ? t('skillsShelf.selfSelectOn') : t('skillsShelf.selfSelectOff')}
            />
          )}
          <span className="text-xs text-admin-muted">{t('skillsShelf.selfSelectHint')}</span>
          {catalog && skills.length > 0 ? (
            <span className="ml-auto text-xs text-admin-muted">
              {t('skillsShelf.count', { count: skills.length })}
            </span>
          ) : null}
        </div>
        <p className="text-sm text-admin-muted">{t('skillsShelf.hint')}</p>

        {catalogQuery.isPending ? (
          <p className="text-sm text-admin-muted">{t('loading')}</p>
        ) : catalogQuery.isError ? (
          <p className="text-sm text-danger-emphasis">{t('skillsShelf.loadFailed')}</p>
        ) : skills.length === 0 ? (
          <p className="text-sm text-admin-muted">{t('skillsShelf.empty')}</p>
        ) : (
          <ul className="space-y-2">
            {skills.map((skill) => {
              const description = skillCatalogDescription(skill.id, skill.description, tSites)
              return (
                <li
                  key={skill.id}
                  className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-admin bg-admin-surface-muted px-3 py-2"
                >
                  <span className="text-sm font-semibold text-admin">{skill.id}</span>
                  <span
                    className="min-w-0 max-w-xl flex-1 truncate text-xs text-admin-muted"
                    title={description || undefined}
                  >
                    {description || t('skillsShelf.noDescription')}
                  </span>
                  <span className="flex flex-wrap items-center gap-1 text-xs text-admin-muted">
                    <span className="rounded bg-admin-surface px-1.5 py-0.5">
                      {t('skillsShelf.sourceTag')}
                    </span>
                    {skill.version ? (
                      <span className="rounded bg-admin-surface px-1.5 py-0.5">
                        {t('skillsShelf.versionTag', { value: skill.version })}
                      </span>
                    ) : null}
                    {skill.allowedTools.length > 0 ? (
                      <span
                        className="rounded bg-admin-surface px-1.5 py-0.5"
                        title={skill.allowedTools.join(', ')}
                      >
                        {t('skillsShelf.toolsTag', { count: skill.allowedTools.length })}
                      </span>
                    ) : null}
                    {skill.license ? (
                      <span className="rounded bg-admin-surface px-1.5 py-0.5">{skill.license}</span>
                    ) : null}
                    {skill.compatibility ? (
                      <span className="rounded bg-admin-surface px-1.5 py-0.5">{skill.compatibility}</span>
                    ) : null}
                  </span>
                </li>
              )
            })}
          </ul>
        )}
      </article>
    </section>
  )
}
