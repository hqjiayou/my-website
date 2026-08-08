import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/page'

/**
 * 运营全局内容中心尚未接入真实后端。
 * 站点级内容管理见 `/apps/sites/:siteId/contents`（contentApi）。
 */
export function OperationContentsPage() {
  const { t } = useTranslation('operation')

  return (
    <section>
      <PageHeader title={t('contents.title')} description={t('contents.description')} />
      <article className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-600">
        <p className="font-medium text-slate-800">{t('contents.notConnected.title')}</p>
        <p className="mt-1">{t('contents.notConnected.description')}</p>
      </article>
    </section>
  )
}
