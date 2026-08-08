import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'

import { templateApi, TEMPLATE_STATUS_VALUES, type TemplateStatus } from '@/api'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { useHasPermission } from '@/router/permission'
import { ROUTES } from '@/router/config/routePaths'
import { PERMISSION_CODES } from '@/types/rbac'

function statusClassName(status: string) {
  switch (status as TemplateStatus) {
    case 'ENABLED':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'DISABLED':
      return 'border-rose-200 bg-rose-50 text-rose-700'
    default:
      return 'border-amber-200 bg-amber-50 text-amber-700'
  }
}

export function TemplateDetailPage() {
  const { t } = useTranslation('templates')
  const { t: tSites } = useTranslation('sites')
  const { templateId } = useParams()
  const queryClient = useQueryClient()
  const canStatus = useHasPermission(PERMISSION_CODES.templateStatus)

  const numericId = Number(templateId)
  const idValid = Number.isFinite(numericId) && numericId > 0

  const { data: template, isPending, isError, error, refetch } = useQuery({
    queryKey: ['templates', numericId],
    queryFn: () => templateApi.getTemplate(numericId),
    enabled: idValid,
  })

  const statusMutation = useMutation({
    mutationFn: ({ status, version }: { status: string; version: number }) =>
      templateApi.updateTemplateStatus(numericId, { status, version }),
    onSuccess: () => {
      toast.success(t('detail.messages.statusUpdated'))
      void queryClient.invalidateQueries({ queryKey: ['templates'] })
      void queryClient.invalidateQueries({ queryKey: ['templates', numericId] })
    },
    onError: (err: Error) => {
      toast.error(err.message)
      void queryClient.invalidateQueries({ queryKey: ['templates', numericId] })
    },
  })

  if (!idValid) {
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

  if (isPending) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
        {t('detail.loading')}
      </section>
    )
  }

  if (isError || !template) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">{t('detail.notFound.title')}</h1>
        <p className="mt-2 text-sm text-slate-600">
          {error instanceof Error ? error.message : t('detail.notFound.description')}
        </p>
        <div className="mt-4 flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => void refetch()}
            className="inline-flex rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
          >
            {t('detail.retry')}
          </button>
          <Link
            to={ROUTES.apps.templates}
            className="inline-flex rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
          >
            {t('detail.backToList')}
          </Link>
        </div>
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
          <div className="flex flex-wrap items-center gap-2">
            <span
              className={cn(
                'inline-flex rounded-md border px-2 py-1 text-xs font-medium',
                statusClassName(template.status),
              )}
            >
              {t(`status.${template.status}`, { defaultValue: template.status })}
            </span>
            {canStatus ? (
              <select
                className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-sm text-slate-700 outline-none focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                value={template.status}
                disabled={statusMutation.isPending}
                onChange={(event) => {
                  const next = event.target.value
                  if (next === template.status) return
                  statusMutation.mutate({ status: next, version: template.version })
                }}
              >
                {TEMPLATE_STATUS_VALUES.map((value) => (
                  <option key={value} value={value}>
                    {t(`status.${value}`)}
                  </option>
                ))}
              </select>
            ) : null}
          </div>
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-4">
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('common.siteType')}</p>
            <p className="mt-1 text-sm font-medium text-slate-900">
              {tSites(`siteType.${template.siteType}`, { defaultValue: template.siteType })}
            </p>
          </div>
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('common.templateCode')}</p>
            <p className="mt-1 text-sm font-medium text-slate-900">{template.code}</p>
          </div>
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('common.status')}</p>
            <p className="mt-1 text-sm font-medium text-slate-900">
              {t(`status.${template.status}`, { defaultValue: template.status })}
            </p>
          </div>
          <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
            <p className="text-xs text-slate-500">{t('common.defaultPrimaryColor')}</p>
            <div className="mt-1 flex items-center gap-2 text-sm font-medium text-slate-900">
              {template.defaultPrimaryColor ? (
                <>
                  <span
                    className="h-3.5 w-3.5 rounded-full border border-slate-200"
                    style={{ backgroundColor: template.defaultPrimaryColor }}
                  />
                  {template.defaultPrimaryColor}
                </>
              ) : (
                '—'
              )}
            </div>
          </div>
        </div>

        <div className="mt-4 rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-5">
          <p className="text-sm font-medium text-slate-700">{t('detail.previewTitle')}</p>
          <div className="mt-3 flex h-36 items-center justify-center overflow-hidden rounded-xl border border-slate-200 bg-white text-sm text-slate-400">
            {template.previewImageUrl ? (
              <img
                src={template.previewImageUrl}
                alt={template.name}
                className="h-full w-full object-cover"
              />
            ) : (
              t('detail.previewPlaceholder')
            )}
          </div>
        </div>

        <article className="mt-4 rounded-2xl border border-slate-200 bg-white p-4">
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
              <dt className="text-slate-500">{t('common.siteType')}</dt>
              <dd className="text-right font-medium text-slate-900">
                {tSites(`siteType.${template.siteType}`, { defaultValue: template.siteType })}
              </dd>
            </div>
            <div className="flex items-start justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
              <dt className="text-slate-500">{t('common.status')}</dt>
              <dd className="text-right font-medium text-slate-900">
                {t(`status.${template.status}`, { defaultValue: template.status })}
              </dd>
            </div>
            <div className="flex items-start justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
              <dt className="text-slate-500">{t('common.sortOrder')}</dt>
              <dd className="text-right font-medium text-slate-900">{template.sortOrder}</dd>
            </div>
            <div className="flex items-start justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
              <dt className="text-slate-500">{t('common.version')}</dt>
              <dd className="text-right font-medium text-slate-900">{template.version}</dd>
            </div>
            <div className="flex items-start justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
              <dt className="text-slate-500">{t('common.createdAt')}</dt>
              <dd className="text-right font-medium text-slate-900">
                {new Date(template.createdAt).toLocaleString()}
              </dd>
            </div>
            <div className="flex items-start justify-between gap-3 rounded-lg bg-slate-50 px-3 py-2">
              <dt className="text-slate-500">{t('common.updatedAt')}</dt>
              <dd className="text-right font-medium text-slate-900">
                {new Date(template.updatedAt).toLocaleString()}
              </dd>
            </div>
            <div className="rounded-lg bg-slate-50 px-3 py-2">
              <dt className="text-slate-500">{t('common.description')}</dt>
              <dd className="mt-1 font-medium text-slate-900">{template.description || '—'}</dd>
            </div>
          </dl>
        </article>

        <div className="mt-4">
          <Link
            to={ROUTES.apps.templates}
            className="inline-flex rounded-lg border border-slate-200 px-3 py-1.5 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
          >
            {t('detail.backToList')}
          </Link>
        </div>
      </article>
    </section>
  )
}
