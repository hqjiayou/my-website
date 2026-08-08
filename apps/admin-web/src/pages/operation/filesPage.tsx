import { useTranslation } from 'react-i18next'

import { PageHeader } from '@/components/page'

/**
 * 运营全局文件中心尚未接入真实后端。
 * 站点级媒体管理见 `/apps/sites/:siteId/files`（mediaApi）。
 */
export function OperationFilesPage() {
  const { t } = useTranslation('operation')

  return (
    <section>
      <PageHeader title={t('files.title')} description={t('files.description')} />
      <article className="rounded-2xl border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center text-sm text-slate-600">
        <p className="font-medium text-slate-800">{t('files.notConnected.title')}</p>
        <p className="mt-1">{t('files.notConnected.description')}</p>
      </article>
    </section>
  )
}
