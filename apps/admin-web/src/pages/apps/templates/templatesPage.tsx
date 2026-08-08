import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import {
  SITE_TYPE_VALUES,
  templateApi,
  TEMPLATE_STATUS_VALUES,
  type TemplateListQuery,
  type TemplateStatus,
} from '@/api'
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

export function TemplatesPage() {
  const { t } = useTranslation('templates')
  const { t: tSites } = useTranslation('sites')
  const queryClient = useQueryClient()
  const canStatus = useHasPermission(PERMISSION_CODES.templateStatus)

  const [filters, setFilters] = useState<TemplateListQuery>({ pageNum: 1, pageSize: 20 })
  const [draft, setDraft] = useState<{ status?: string; siteType?: string }>({})

  const { data, isPending, isError, error, refetch } = useQuery({
    queryKey: ['templates', filters],
    queryFn: () => templateApi.listTemplates(filters),
  })

  const templates = data?.records ?? []
  const total = data?.total ?? 0
  const pageNum = filters.pageNum ?? 1
  const pageSize = filters.pageSize ?? 20
  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  const statusMutation = useMutation({
    mutationFn: ({ id, status, version }: { id: number; status: string; version: number }) =>
      templateApi.updateTemplateStatus(id, { status, version }),
    onSuccess: () => {
      toast.success(t('list.messages.statusUpdated'))
      void queryClient.invalidateQueries({ queryKey: ['templates'] })
    },
    onError: (err: Error) => {
      toast.error(err.message)
      void queryClient.invalidateQueries({ queryKey: ['templates'] })
    },
  })

  function applyFilters() {
    setFilters((prev) => ({
      ...prev,
      pageNum: 1,
      status: draft.status,
      siteType: draft.siteType,
    }))
  }

  function resetFilters() {
    setDraft({})
    setFilters({ pageNum: 1, pageSize: filters.pageSize ?? 20 })
  }

  return (
    <section>
      <PageHeader title={t('list.title')} description={t('list.description')} />

      <div className="mb-4 rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="grid gap-3 md:grid-cols-4">
          <select
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            value={draft.status ?? ''}
            onChange={(event) => setDraft((prev) => ({ ...prev, status: event.target.value || undefined }))}
          >
            <option value="">{t('list.filters.allStatus')}</option>
            {TEMPLATE_STATUS_VALUES.map((value) => (
              <option key={value} value={value}>
                {t(`status.${value}`)}
              </option>
            ))}
          </select>
          <select
            className="rounded-lg border border-slate-200 px-3 py-2 text-sm outline-none focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
            value={draft.siteType ?? ''}
            onChange={(event) => setDraft((prev) => ({ ...prev, siteType: event.target.value || undefined }))}
          >
            <option value="">{t('list.filters.allSiteType')}</option>
            {SITE_TYPE_VALUES.map((value) => (
              <option key={value} value={value}>
                {tSites(`siteType.${value}`)}
              </option>
            ))}
          </select>
          <div className="flex gap-2 md:col-span-2">
            <button
              type="button"
              onClick={applyFilters}
              className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-800"
            >
              {t('list.filters.search')}
            </button>
            <button
              type="button"
              onClick={resetFilters}
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
            >
              {t('list.filters.reset')}
            </button>
          </div>
        </div>
      </div>

      {isPending ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
          {t('list.loading')}
        </div>
      ) : null}

      {isError ? (
        <div className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700 shadow-sm">
          <p>{error instanceof Error ? error.message : t('list.error')}</p>
          <button
            type="button"
            onClick={() => void refetch()}
            className="mt-3 rounded-lg border border-rose-300 bg-white px-3 py-1.5 text-sm font-medium text-rose-700 hover:bg-rose-100"
          >
            {t('list.retry')}
          </button>
        </div>
      ) : null}

      {!isPending && !isError ? (
        <>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {templates.map((template) => (
              <article key={template.id} className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="text-base font-semibold text-slate-900">{template.name}</h2>
                    <p className="mt-1 text-xs text-slate-500">{template.code}</p>
                  </div>
                  <span
                    className={cn(
                      'inline-flex rounded-md border px-2 py-1 text-xs font-medium',
                      statusClassName(template.status),
                    )}
                  >
                    {t(`status.${template.status}`, { defaultValue: template.status })}
                  </span>
                </div>

                <dl className="mt-4 grid grid-cols-2 gap-3">
                  <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                    <dt className="text-xs text-slate-500">{t('common.siteType')}</dt>
                    <dd className="mt-1 text-sm font-medium text-slate-900">
                      {tSites(`siteType.${template.siteType}`, { defaultValue: template.siteType })}
                    </dd>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                    <dt className="text-xs text-slate-500">{t('common.defaultPrimaryColor')}</dt>
                    <dd className="mt-1 flex items-center gap-2 text-sm font-medium text-slate-900">
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
                    </dd>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                    <dt className="text-xs text-slate-500">{t('common.sortOrder')}</dt>
                    <dd className="mt-1 text-sm font-medium text-slate-900">{template.sortOrder}</dd>
                  </div>
                  <div className="rounded-xl border border-slate-100 bg-slate-50 p-3">
                    <dt className="text-xs text-slate-500">{t('common.updatedAt')}</dt>
                    <dd className="mt-1 text-sm font-medium text-slate-900">
                      {new Date(template.updatedAt).toLocaleDateString()}
                    </dd>
                  </div>
                </dl>

                <div className="mt-4 flex flex-wrap items-center gap-2">
                  <Link
                    to={ROUTES.apps.templateDetail(template.id)}
                    className="inline-flex rounded-lg border border-blue-200 bg-blue-50 px-3 py-1.5 text-sm font-medium text-blue-700 transition hover:bg-blue-100"
                  >
                    {t('list.actions.viewDetail')}
                  </Link>
                  {canStatus ? (
                    <select
                      className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-sm text-slate-700 outline-none focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
                      value={template.status}
                      disabled={statusMutation.isPending}
                      onChange={(event) => {
                        const next = event.target.value
                        if (next === template.status) return
                        statusMutation.mutate({
                          id: template.id,
                          status: next,
                          version: template.version,
                        })
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
              </article>
            ))}
          </div>

          {templates.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-6 text-sm text-slate-600 shadow-sm">
              {t('list.empty')}
            </div>
          ) : null}

          {total > 0 ? (
            <div className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-slate-600">
              <p>{t('list.pagination.summary', { total, page: pageNum, pageSize })}</p>
              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={pageNum <= 1}
                  onClick={() => setFilters((prev) => ({ ...prev, pageNum: Math.max(1, (prev.pageNum ?? 1) - 1) }))}
                  className="rounded-lg border border-slate-200 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-50 hover:bg-slate-50"
                >
                  {t('list.pagination.prev')}
                </button>
                <button
                  type="button"
                  disabled={pageNum >= totalPages}
                  onClick={() =>
                    setFilters((prev) => ({ ...prev, pageNum: Math.min(totalPages, (prev.pageNum ?? 1) + 1) }))
                  }
                  className="rounded-lg border border-slate-200 px-3 py-1.5 disabled:cursor-not-allowed disabled:opacity-50 hover:bg-slate-50"
                >
                  {t('list.pagination.next')}
                </button>
              </div>
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  )
}
