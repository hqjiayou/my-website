import { useTranslation } from 'react-i18next'
import { useOutletContext } from 'react-router-dom'

import type { AgentWorkspaceOutletContext } from './agentWorkspaceLayout'

export function AgentDomainPage() {
  const { t } = useTranslation('sites')
  const { site } = useOutletContext<AgentWorkspaceOutletContext>()

  return (
    <section className="w-full space-y-3">
      <p className="text-sm text-muted-foreground">{t('detail.placeholders.domainFuture')}</p>
      <article className="rounded-xl border border-border bg-card p-5">
        <div className="grid gap-3 md:grid-cols-2">
          <div className="rounded-lg border border-admin bg-admin-surface-muted p-3">
            <p className="text-xs text-admin-muted">{t('detail.labels.domain')}</p>
            <p className="mt-1 text-sm font-medium text-admin">{site.defaultDomain || '—'}</p>
          </div>
          <div className="rounded-lg border border-admin bg-admin-surface-muted p-3">
            <p className="text-xs text-admin-muted">{t('detail.labels.status')}</p>
            <p className="mt-1 text-sm font-medium text-admin">
              {t(`status.${site.status}`, { defaultValue: site.status })}
            </p>
          </div>
          <div className="rounded-lg border border-admin bg-admin-surface-muted p-3">
            <p className="text-xs text-admin-muted">{t('detail.labels.dnsStatus')}</p>
            <p className="mt-1 text-sm font-medium text-admin">{t('detail.placeholders.dnsHint')}</p>
          </div>
          <div className="rounded-lg border border-admin bg-admin-surface-muted p-3">
            <p className="text-xs text-admin-muted">{t('detail.labels.sslStatus')}</p>
            <p className="mt-1 text-sm font-medium text-admin">{t('detail.placeholders.cdnHint')}</p>
          </div>
        </div>
      </article>
    </section>
  )
}
