import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import { appearanceApi } from '@/api'
import { AgentAppearancePreview } from '@/pages/apps/style/agentAppearancePreview'

import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

export function AgentThemePage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()

  const appearanceQuery = useQuery({
    queryKey: ['appearances', site.id],
    queryFn: () => appearanceApi.getAppearance(site.id),
  })

  const data = appearanceQuery.data

  return (
    <section className="w-full space-y-3">
      <div>
      {appearanceQuery.isPending ? (
        <article className="rounded-xl border border-admin bg-admin-surface p-5 text-sm text-admin-muted">
          {t('detail.theme.loading')}
        </article>
      ) : appearanceQuery.isError || !data ? (
        <article className="rounded-xl border border-admin bg-admin-surface p-5">
          <p className="text-sm text-danger-emphasis">
            {appearanceQuery.error instanceof Error
              ? appearanceQuery.error.message
              : t('detail.theme.unavailable')}
          </p>
        </article>
      ) : (
        <article className="rounded-xl border border-admin bg-admin-surface p-5">
          <div className="grid gap-3 md:grid-cols-2">
            <div className="rounded-lg border border-admin bg-admin-surface-muted p-3">
              <p className="text-xs text-admin-muted">{t('detail.labels.primaryColor')}</p>
              <div className="mt-2 flex items-center gap-2">
                <span
                  className="inline-block h-5 w-5 rounded border border-admin"
                  style={{ backgroundColor: data.config.primaryColor }}
                />
                <p className="text-sm font-medium text-admin">{data.config.primaryColor}</p>
              </div>
            </div>
            <div className="rounded-lg border border-admin bg-admin-surface-muted p-3">
              <p className="text-xs text-admin-muted">{t('create.fields.fontStyle')}</p>
              <p className="mt-1 text-sm font-medium text-admin">
                {t(`create.options.fontStyle.${data.config.fontStyle}`)}
              </p>
            </div>
            <div className="rounded-lg border border-admin bg-admin-surface-muted p-3">
              <p className="text-xs text-admin-muted">{t('create.fields.buttonStyle')}</p>
              <p className="mt-1 text-sm font-medium text-admin">
                {t(`create.options.buttonStyle.${data.config.buttonStyle}`)}
              </p>
            </div>
            <div className="rounded-lg border border-admin bg-admin-surface-muted p-3">
              <p className="text-xs text-admin-muted">{t('create.fields.heroLayout')}</p>
              <p className="mt-1 text-sm font-medium text-admin">
                {t(`create.options.heroLayout.${data.config.heroLayout}`)}
              </p>
            </div>
            <div className="rounded-lg border border-admin bg-admin-surface-muted p-3">
              <p className="text-xs text-admin-muted">{t('detail.theme.meta')}</p>
              <p className="mt-1 text-sm font-medium text-admin">
                v{data.schemaVersion} · {t('basic.versionLabel')} {data.version}
              </p>
              <p className="mt-1 text-xs text-admin-muted">{data.updatedAt}</p>
            </div>
          </div>

          <div className="mt-4">
            <AgentAppearancePreview agentName={site.agentName} config={data.config} previewMode="desktop" />
          </div>
        </article>
      )}
      </div>
    </section>
  )
}
