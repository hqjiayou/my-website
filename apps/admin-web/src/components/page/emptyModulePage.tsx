import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'

/** 与 `EmptyModulePage` 各路由模块的 i18n 命名空间一致（见 `src/i18n/page-namespaces.ts`）。 */
export type EmptyModuleNamespace = 'paramsConfig' | 'analytics' | 'systemSettings' | 'developer'

interface EmptyModulePageProps {
  namespace: EmptyModuleNamespace
}

export function EmptyModulePage({ namespace }: EmptyModulePageProps) {
  const { t } = useTranslation(namespace)
  const { t: tLayout } = useTranslation('layout')
  const hintsUnknown = t('hints', { returnObjects: true })
  const hints = Array.isArray(hintsUnknown) ? (hintsUnknown as string[]) : []

  return (
    <section className="admin-content-form">
      <AdminPageHeader title={t('title')} description={t('description')} />
      <div className="rounded-xl border border-dashed border-border bg-card p-6">
        <p className="text-sm text-muted-foreground">{tLayout('emptyModule.initLead')}</p>
        <ul className="mt-3 list-disc space-y-2 pl-5 text-sm text-foreground">
          {hints.map((hint) => (
            <li key={hint}>{hint}</li>
          ))}
        </ul>
      </div>
    </section>
  )
}
