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

  if (appearanceQuery.isPending) {
    return (
      <article className="admin-content-form rounded-xl border border-border bg-card p-5 text-sm text-muted-foreground">
        {t('detail.theme.loading')}
      </article>
    )
  }

  if (appearanceQuery.isError || !appearanceQuery.data) {
    return (
      <article className="admin-content-form rounded-xl border border-border bg-card p-5">
        <h2 className="text-base font-semibold text-foreground">{t('detail.nav.theme')}</h2>
        <p className="mt-2 text-sm text-danger-emphasis">
          {appearanceQuery.error instanceof Error
            ? appearanceQuery.error.message
            : t('detail.theme.unavailable')}
        </p>
      </article>
    )
  }

  const { config, version, updatedAt, schemaVersion } = appearanceQuery.data

  return (
    <article className="admin-content-form rounded-xl border border-border bg-card p-5">
      <h2 className="text-base font-semibold text-foreground">{t('detail.nav.theme')}</h2>
      <p className="mt-1 text-sm text-muted-foreground">{t('detail.theme.description')}</p>

      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <div className="rounded-lg border border-border bg-card-muted p-3">
          <p className="text-xs text-muted-foreground">{t('detail.labels.primaryColor')}</p>
          <div className="mt-2 flex items-center gap-2">
            <span
              className="inline-block h-5 w-5 rounded border border-border"
              style={{ backgroundColor: config.primaryColor }}
            />
            <p className="text-sm font-medium text-foreground">{config.primaryColor}</p>
          </div>
        </div>
        <div className="rounded-lg border border-border bg-card-muted p-3">
          <p className="text-xs text-muted-foreground">{t('create.fields.fontStyle')}</p>
          <p className="mt-1 text-sm font-medium text-foreground">
            {t(`create.options.fontStyle.${config.fontStyle}`)}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-card-muted p-3">
          <p className="text-xs text-muted-foreground">{t('create.fields.buttonStyle')}</p>
          <p className="mt-1 text-sm font-medium text-foreground">
            {t(`create.options.buttonStyle.${config.buttonStyle}`)}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-card-muted p-3">
          <p className="text-xs text-muted-foreground">{t('create.fields.heroLayout')}</p>
          <p className="mt-1 text-sm font-medium text-foreground">
            {t(`create.options.heroLayout.${config.heroLayout}`)}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-card-muted p-3">
          <p className="text-xs text-muted-foreground">{t('detail.theme.meta')}</p>
          <p className="mt-1 text-sm font-medium text-foreground">
            v{schemaVersion} · {t('basic.versionLabel')} {version}
          </p>
          <p className="mt-1 text-xs text-muted-foreground">{updatedAt}</p>
        </div>
      </div>

      <div className="mt-4">
        <AgentAppearancePreview agentName={site.agentName} config={config} previewMode="desktop" />
      </div>
    </article>
  )
}
