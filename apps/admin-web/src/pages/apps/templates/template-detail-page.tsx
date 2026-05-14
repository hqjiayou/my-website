import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'

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

export function TemplateDetailPage() {
  const { t } = useTranslation('templates')
  const { t: tSites } = useTranslation('sites')
  const { templateId } = useParams()

  const { data: template, isPending } = useQuery({
    queryKey: ['templates', templateId],
    queryFn: () => templateApi.getTemplate(templateId!),
    enabled: Boolean(templateId),
  })

  if (isPending) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
        {t('detail.loading')}
      </section>
    )
  }

  if (!template) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">{t('detail.notFound.title')}</h1>
        <p className="mt-2 text-sm text-slate-600">{t('detail.notFound.description')}</p>
        <Link
          to={ROUTES.apps.templates}
          className="mt-4 inline-flex rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
        >
          {t('detail.backToList')}
        </Link>
      </section>
    )
  }

  return (
    <section>
      <PageHeader title={t('detail.title')} description={t('detail.description')} />

      <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-2xl font-semibold text-slate-900">{template.name}</h2>
            <p className="mt-1 text-sm text-slate-500">{template.code}</p>
          </div>
          <span className={cn('inline-flex rounded-md border px-2 py-1 text-xs font-medium', statusClassName(template.status))}>
            {t(`status.${template.status}`)}
          </span>
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-4">
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('common.industry')}</p>
            <p className="mt-1 text-sm font-medium text-slate-900">{tSites(`industry.${template.industry}`)}</p>
          </div>
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('common.templateCode')}</p>
            <p className="mt-1 text-sm font-medium text-slate-900">{template.code}</p>
          </div>
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('common.status')}</p>
            <p className="mt-1 text-sm font-medium text-slate-900">{t(`status.${template.status}`)}</p>
          </div>
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('common.defaultPrimaryColor')}</p>
            <div className="mt-1 flex items-center gap-2 text-sm font-medium text-slate-900">
              <span className="h-3.5 w-3.5 rounded-full border border-slate-200" style={{ backgroundColor: template.defaultPrimaryColor }} />
              {template.defaultPrimaryColor}
            </div>
          </div>
        </div>

        <div className="mt-4 rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-5">
          <p className="text-sm font-medium text-slate-700">{t('detail.previewTitle')}</p>
          <div className="mt-3 flex h-36 items-center justify-center rounded-xl border border-slate-200 bg-white text-sm text-slate-400">
            {template.previewImage ? template.previewImage : t('detail.previewPlaceholder')}
          </div>
        </div>

        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <article className="rounded-2xl border border-slate-200 bg-white p-4">
            <h3 className="text-sm font-semibold text-slate-900">{t('detail.sections.basicInfo')}</h3>
            <dl className="mt-3 space-y-2 text-sm">
              <div className="flex items-start justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
                <dt className="text-slate-500">{t('common.templateName')}</dt>
                <dd className="text-right font-medium text-slate-900">{template.name}</dd>
              </div>
              <div className="flex items-start justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
                <dt className="text-slate-500">{t('common.templateCode')}</dt>
                <dd className="text-right font-medium text-slate-900">{template.code}</dd>
              </div>
              <div className="flex items-start justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
                <dt className="text-slate-500">{t('common.industry')}</dt>
                <dd className="text-right font-medium text-slate-900">{tSites(`industry.${template.industry}`)}</dd>
              </div>
              <div className="flex items-start justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
                <dt className="text-slate-500">{t('common.status')}</dt>
                <dd className="text-right font-medium text-slate-900">{t(`status.${template.status}`)}</dd>
              </div>
              <div className="flex items-start justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
                <dt className="text-slate-500">{t('common.defaultPrimaryColor')}</dt>
                <dd className="text-right font-medium text-slate-900">{template.defaultPrimaryColor}</dd>
              </div>
              <div className="rounded-lg bg-slate-50 px-3 py-2">
                <dt className="text-slate-500">{t('common.description')}</dt>
                <dd className="mt-1 font-medium text-slate-900">{template.description}</dd>
              </div>
            </dl>
          </article>

          <div className="space-y-4">
            <article className="rounded-2xl border border-slate-200 bg-white p-4">
              <h3 className="text-sm font-semibold text-slate-900">{t('common.supportedPages')}</h3>
              <div className="mt-3 flex flex-wrap gap-2">
                {template.supportedPages.map((item) => (
                  <span key={item} className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs text-slate-700">
                    {item}
                  </span>
                ))}
              </div>
            </article>

            <article className="rounded-2xl border border-slate-200 bg-white p-4">
              <h3 className="text-sm font-semibold text-slate-900">{t('common.supportedBlocks')}</h3>
              <div className="mt-3 flex flex-wrap gap-2">
                {template.supportedBlocks.map((item) => (
                  <span key={item} className="rounded-lg border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs text-slate-700">
                    {item}
                  </span>
                ))}
              </div>
            </article>

            <article className="rounded-2xl border border-slate-200 bg-white p-4">
              <h3 className="text-sm font-semibold text-slate-900">{t('common.targetSiteTypes')}</h3>
              <div className="mt-3 flex flex-wrap gap-2">
                {template.targetSiteTypes.map((item) => (
                  <span key={item} className="rounded-lg border border-blue-200 bg-blue-50 px-2.5 py-1 text-xs text-blue-700">
                    {item}
                  </span>
                ))}
              </div>
            </article>
          </div>
        </div>

        <p className="mt-4 rounded-xl border border-dashed border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-700">
          {t('detail.placeholderNotice')}
        </p>
      </article>
    </section>
  )
}
