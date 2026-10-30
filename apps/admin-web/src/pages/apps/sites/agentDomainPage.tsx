import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

export function AgentDomainPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()

  return (
    <article className="admin-content-form rounded-xl border border-border bg-card p-5">
      <h2 className="text-base font-semibold text-foreground">{t('detail.nav.domain')}</h2>
      <p className="mt-1 text-sm text-muted-foreground">{t('detail.placeholders.domainFuture')}</p>

      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <div className="rounded-lg border border-border bg-card-muted p-3">
          <p className="text-xs text-muted-foreground">{t('detail.labels.domain')}</p>
          <p className="mt-1 text-sm font-medium text-foreground">{site.defaultDomain || '—'}</p>
        </div>
        <div className="rounded-lg border border-border bg-card-muted p-3">
          <p className="text-xs text-muted-foreground">{t('detail.labels.status')}</p>
          <p className="mt-1 text-sm font-medium text-foreground">
            {t(`status.${site.status}`, { defaultValue: site.status })}
          </p>
        </div>
        <div className="rounded-lg border border-border bg-card-muted p-3">
          <p className="text-xs text-muted-foreground">{t('detail.labels.dnsStatus')}</p>
          <p className="mt-1 text-sm font-medium text-foreground">{t('detail.placeholders.dnsHint')}</p>
        </div>
        <div className="rounded-lg border border-border bg-card-muted p-3">
          <p className="text-xs text-muted-foreground">{t('detail.labels.sslStatus')}</p>
          <p className="mt-1 text-sm font-medium text-foreground">{t('detail.placeholders.cdnHint')}</p>
        </div>
      </div>
    </article>
  )
}
