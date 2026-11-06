import { useQuery } from '@tanstack/react-query'
import { AlertCircle, RefreshCw, Sparkles } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { aiApi } from '@/api/apps/aiApi'
import { AdminPageHeader, StatusTag } from '@/components/admin'
import { adminSecondaryButtonSmClass } from '@/components/admin/controls'
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
      <article className="rounded-xl border border-admin bg-admin-surface p-5">
        <div className="flex items-center justify-between gap-3">
          <h3 className="text-base font-semibold text-admin">{t('skillsShelf.cardTitle')}</h3>
          {catalog && skills.length > 0 ? (
            <span className="text-xs text-admin-muted">
              {t('skillsShelf.count', { count: skills.length })}
            </span>
          ) : null}
        </div>
        <div className="mt-3 space-y-3">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
            <span className="text-sm font-semibold text-admin">{t('skillsShelf.selfSelect')}</span>
            {catalogQuery.isPending ? (
              <span
                className="h-[22px] w-14 animate-pulse rounded-full bg-admin-surface-muted"
                aria-hidden
              />
            ) : catalogQuery.isError ? (
              <StatusTag status="LOAD_FAILED" tone="danger" label={t('skillsShelf.loadFailed')} />
            ) : (
              <StatusTag
                status={selfSelectEnabled ? 'ACTIVE' : 'DISABLED'}
                label={selfSelectEnabled ? t('skillsShelf.selfSelectOn') : t('skillsShelf.selfSelectOff')}
              />
            )}
            <span className="text-xs text-admin-muted">{t('skillsShelf.selfSelectHint')}</span>
          </div>
          <p className="text-sm text-admin-muted">{t('skillsShelf.hint')}</p>

          {catalogQuery.isPending ? (
            <div className="space-y-2" aria-hidden>
              {[1, 2, 3, 4].map((n) => (
                <div key={n} className="h-14 animate-pulse rounded-lg bg-admin-surface-muted" />
              ))}
            </div>
          ) : catalogQuery.isError ? (
            <div className="flex items-center gap-3 rounded-xl border border-danger/35 bg-danger-muted px-4 py-3">
              <AlertCircle className="h-[18px] w-[18px] shrink-0 text-danger-emphasis" aria-hidden />
              <p className="min-w-0 flex-1 text-sm text-danger-emphasis">
                {t('skillsShelf.loadFailed')}
              </p>
              <button
                type="button"
                className={adminSecondaryButtonSmClass}
                onClick={() => void catalogQuery.refetch()}
              >
                <RefreshCw className="h-3.5 w-3.5" aria-hidden />
                {t('retry')}
              </button>
            </div>
          ) : skills.length === 0 ? (
            <div className="flex flex-col items-center justify-center gap-2 py-10 text-center">
              <span className="admin-empty-mark">
                <Sparkles className="h-6 w-6" aria-hidden />
              </span>
              <p className="text-sm font-medium text-admin">{t('skillsShelf.emptyTitle')}</p>
              <p className="max-w-md text-xs text-admin-muted">{t('skillsShelf.emptyHint')}</p>
            </div>
          ) : (
            <ul className="divide-y divide-border">
              {skills.map((skill) => {
                const description = skillCatalogDescription(skill.id, skill.description, tSites)
                return (
                  <li
                    key={skill.id}
                    className="flex min-h-14 flex-wrap items-center gap-x-3 gap-y-1 px-2 py-2 transition hover:bg-admin-surface-muted"
                  >
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-admin-md bg-[rgb(var(--chart-2)/.12)] text-chart-2">
                      <Sparkles className="h-4 w-4" aria-hidden />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium text-admin">{skill.id}</span>
                      <span
                        className="block truncate text-xs text-admin-muted"
                        title={description || undefined}
                      >
                        {description || t('skillsShelf.noDescription')}
                      </span>
                    </span>
                    <span className="flex flex-wrap items-center gap-1.5">
                      <StatusTag
                        status="SOURCE"
                        tone="info"
                        label={t('skillsShelf.sourceTag')}
                      />
                      {skill.version ? (
                        <StatusTag
                          status="VERSION"
                          tone="neutral"
                          label={t('skillsShelf.versionTag', { value: skill.version })}
                        />
                      ) : null}
                      {skill.allowedTools.length > 0 ? (
                        <span title={skill.allowedTools.join(', ')}>
                          <StatusTag
                            status="TOOLS"
                            tone="neutral"
                            label={t('skillsShelf.toolsTag', { count: skill.allowedTools.length })}
                          />
                        </span>
                      ) : null}
                      {skill.license ? (
                        <StatusTag status="LICENSE" tone="neutral" label={skill.license} />
                      ) : null}
                      {skill.compatibility ? (
                        <StatusTag
                          status="COMPATIBILITY"
                          tone="neutral"
                          label={skill.compatibility}
                        />
                      ) : null}
                    </span>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      </article>
    </section>
  )
}
