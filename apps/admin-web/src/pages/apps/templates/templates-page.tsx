import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import { templateApi, type TemplateStatus } from '@/api'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/route-paths'

function statusClassName(status: TemplateStatus) {
  switch (status) {
    case 'enabled':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'disabled':
      return 'border-rose-200 bg-rose-50 text-rose-700'
    default:
      return 'border-amber-200 bg-amber-50 text-amber-700'
  }
}

export function TemplatesPage() {
  const { t } = useTranslation('templates')
  const { t: tSites } = useTranslation('sites')
  const { data: templates = [] } = useQuery({
    queryKey: ['templates'],
    queryFn: templateApi.listTemplates,
  })

  const enabledCount = templates.filter((item) => item.status === 'enabled').length
  const draftCount = templates.filter((item) => item.status === 'draft').length
  const disabledCount = templates.filter((item) => item.status === 'disabled').length

  return (
    <section>
      <PageHeader title={t('list.title')} description={t('list.description')} />

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('list.stats.totalTemplates')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{templates.length}</p>
        </article>
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('list.stats.enabled')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{enabledCount}</p>
        </article>
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('list.stats.draft')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{draftCount}</p>
        </article>
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('list.stats.disabled')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{disabledCount}</p>
        </article>
      </div>

      <div className="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {templates.map((template) => (
          <article key={template.id} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-base font-semibold text-slate-900">{template.name}</h2>
                <p className="mt-1 text-xs text-slate-500">{template.code}</p>
              </div>
              <span className={cn('inline-flex rounded-md border px-2 py-1 text-xs font-medium', statusClassName(template.status))}>
                {t(`status.${template.status}`)}
              </span>
            </div>

            <dl className="mt-4 grid grid-cols-2 gap-3">
              <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                <dt className="text-xs text-slate-500">{t('common.industry')}</dt>
                <dd className="mt-1 text-sm font-medium text-slate-900">{tSites(`industry.${template.industry}`)}</dd>
              </div>
              <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                <dt className="text-xs text-slate-500">{t('common.defaultPrimaryColor')}</dt>
                <dd className="mt-1 flex items-center gap-2 text-sm font-medium text-slate-900">
                  <span className="h-3.5 w-3.5 rounded-full border border-slate-200" style={{ backgroundColor: template.defaultPrimaryColor }} />
                  {template.defaultPrimaryColor}
                </dd>
              </div>
              <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                <dt className="text-xs text-slate-500">{t('common.supportedPages')}</dt>
                <dd className="mt-1 text-sm font-medium text-slate-900">{template.supportedPages.length}</dd>
              </div>
              <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                <dt className="text-xs text-slate-500">{t('common.supportedBlocks')}</dt>
                <dd className="mt-1 text-sm font-medium text-slate-900">{template.supportedBlocks.length}</dd>
              </div>
            </dl>

            <p className="mt-4 text-xs text-slate-500">
              {t('common.updatedAt')}: {new Date(template.updatedAt).toLocaleDateString()}
            </p>

            <Link
              to={ROUTES.apps.templateDetail(template.id)}
              className="mt-4 inline-flex rounded-lg border border-blue-200 bg-blue-50 px-3 py-1.5 text-sm font-medium text-blue-700 transition hover:bg-blue-100"
            >
              {t('list.actions.viewDetail')}
            </Link>
          </article>
        ))}
      </div>
      {templates.length === 0 && (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-6 text-sm text-slate-600 shadow-sm">
          {t('list.empty')}
        </div>
      )}
    </section>
  )
}
