import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { useParams } from 'react-router-dom'

import { contentApi, type ContentStatus, type ContentType } from '@/api/content-api'
import { siteApi } from '@/api/site-api'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'

type TypeFilter = 'all' | ContentType
type StatusFilter = 'all' | ContentStatus

const CONTENT_TYPES: ContentType[] = ['hero', 'service', 'case', 'faq', 'news', 'about', 'cta']
const OVERVIEW_TYPES: ContentType[] = ['hero', 'service', 'case', 'faq', 'news', 'about']

function statusClassName(status: ContentStatus) {
  switch (status) {
    case 'published':
      return 'border border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'draft':
      return 'border border-amber-200 bg-amber-50 text-amber-700'
    default:
      return 'border border-slate-200 bg-slate-100 text-slate-500'
  }
}

export function SiteContentsPage() {
  const { t } = useTranslation('sites')
  const { siteId } = useParams()
  const [typeFilter, setTypeFilter] = useState<TypeFilter>('all')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all')

  const { data: site, isPending: isSitePending } = useQuery({
    queryKey: ['sites', siteId, 'contents-page'],
    queryFn: () => siteApi.getSite(siteId!),
    enabled: Boolean(siteId),
  })

  const { data: contents = [], isPending: isContentsPending } = useQuery({
    queryKey: ['contents', siteId],
    queryFn: () => contentApi.listContents(siteId!),
    enabled: Boolean(siteId),
  })

  const filteredContents = useMemo(
    () =>
      contents.filter((item) => {
        if (typeFilter !== 'all' && item.type !== typeFilter) return false
        if (statusFilter !== 'all' && item.status !== statusFilter) return false
        return true
      }),
    [contents, statusFilter, typeFilter],
  )

  const totalCount = contents.length
  const publishedCount = contents.filter((item) => item.status === 'published').length
  const draftCount = contents.filter((item) => item.status === 'draft').length
  const hiddenCount = contents.filter((item) => item.status === 'hidden').length

  if (isSitePending) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
        {t('contentManagement.loadingSite')}
      </section>
    )
  }

  if (!site) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">{t('contentManagement.notFound.title')}</h1>
        <p className="mt-2 text-sm text-slate-600">{t('contentManagement.notFound.description')}</p>
      </section>
    )
  }

  return (
    <section>
      <PageHeader
        title={t('contentManagement.title')}
        description={t('contentManagement.description')}
        action={
          <button
            type="button"
            onClick={() => toast.info(t('contentManagement.messages.addPlaceholder'))}
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700"
          >
            <Plus className="h-4 w-4" />
            {t('contentManagement.addContent')}
          </button>
        }
      />

      <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <p className="text-xs text-slate-500">{t('contentManagement.currentSite')}</p>
        <p className="mt-1 text-base font-semibold text-slate-900">{site.name}</p>
      </article>

      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('contentManagement.totalContent')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{totalCount}</p>
        </article>
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('contentManagement.published')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{publishedCount}</p>
        </article>
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('contentManagement.draft')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{draftCount}</p>
        </article>
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs text-slate-500">{t('contentManagement.hidden')}</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{hiddenCount}</p>
        </article>
      </div>

      <article className="mt-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <h2 className="text-base font-semibold text-slate-900">{t('contentManagement.typeOverview.title')}</h2>
        <div className="mt-3 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {OVERVIEW_TYPES.map((type) => {
            const total = contents.filter((item) => item.type === type).length
            const published = contents.filter((item) => item.type === type && item.status === 'published').length
            return (
              <article key={type} className="rounded-xl border border-slate-200 bg-slate-50 p-3">
                <p className="text-sm font-semibold text-slate-900">{t(`contentManagement.typeLabels.${type}`)}</p>
                <p className="mt-1 text-xs text-slate-500">{t(`contentManagement.typeDescriptions.${type}`)}</p>
                <div className="mt-2 flex items-center gap-4 text-xs text-slate-600">
                  <span>
                    {t('contentManagement.totalContent')}: <b className="text-slate-800">{total}</b>
                  </span>
                  <span>
                    {t('contentManagement.published')}: <b className="text-slate-800">{published}</b>
                  </span>
                </div>
              </article>
            )
          })}
        </div>
      </article>

      <article className="mt-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="grid gap-3 md:grid-cols-2">
          <label className="space-y-1.5 text-sm">
            <span className="font-medium text-slate-700">{t('contentManagement.contentType')}</span>
            <select
              value={typeFilter}
              onChange={(event) => setTypeFilter(event.target.value as TypeFilter)}
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
            >
              <option value="all">{t('contentManagement.all')}</option>
              {CONTENT_TYPES.map((type) => (
                <option key={type} value={type}>
                  {t(`contentManagement.typeLabels.${type}`)}
                </option>
              ))}
            </select>
          </label>

          <label className="space-y-1.5 text-sm">
            <span className="font-medium text-slate-700">{t('contentManagement.status')}</span>
            <select
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
              className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
            >
              <option value="all">{t('contentManagement.all')}</option>
              <option value="published">{t('contentManagement.published')}</option>
              <option value="draft">{t('contentManagement.draft')}</option>
              <option value="hidden">{t('contentManagement.hidden')}</option>
            </select>
          </label>
        </div>
      </article>

      <article className="mt-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="overflow-x-auto">
          <table className="min-w-full text-left text-sm">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-3 py-2">{t('contentManagement.titleLabel')}</th>
                <th className="px-3 py-2">{t('contentManagement.contentType')}</th>
                <th className="px-3 py-2">{t('contentManagement.status')}</th>
                <th className="px-3 py-2">{t('contentManagement.sortOrder')}</th>
                <th className="px-3 py-2">{t('contentManagement.updatedAt')}</th>
                <th className="px-3 py-2">{t('contentManagement.descriptionLabel')}</th>
                <th className="px-3 py-2">{t('contentManagement.actions')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredContents.map((item) => (
                <tr key={item.id}>
                  <td className="px-3 py-3 font-medium text-slate-900">{item.title}</td>
                  <td className="px-3 py-3 text-slate-700">{t(`contentManagement.typeLabels.${item.type}`)}</td>
                  <td className="px-3 py-3">
                    <span className={cn('inline-flex rounded-md px-2 py-1 text-xs font-medium', statusClassName(item.status))}>
                      {t(`contentManagement.statusLabels.${item.status}`)}
                    </span>
                  </td>
                  <td className="px-3 py-3 text-slate-700">{item.sortOrder}</td>
                  <td className="px-3 py-3 text-slate-700">{new Date(item.updatedAt).toLocaleDateString()}</td>
                  <td className="max-w-[320px] truncate px-3 py-3 text-slate-600">{item.description}</td>
                  <td className="px-3 py-3">
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={() => toast.info(t('contentManagement.messages.editPlaceholder'))}
                        className="rounded-md border border-slate-200 px-2.5 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
                      >
                        {t('contentManagement.edit')}
                      </button>
                      <button
                        type="button"
                        onClick={() => toast.info(t('contentManagement.messages.previewPlaceholder'))}
                        className="rounded-md border border-blue-200 bg-blue-50 px-2.5 py-1 text-xs font-medium text-blue-700 transition hover:bg-blue-100"
                      >
                        {t('contentManagement.preview')}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {!isContentsPending && filteredContents.length === 0 && (
          <div className="mt-3 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-3 py-4 text-sm text-slate-600">
            {t('contentManagement.empty')}
          </div>
        )}
      </article>

      <article className="mt-4 rounded-2xl border border-dashed border-blue-200 bg-blue-50 p-4 shadow-sm">
        <h3 className="text-sm font-semibold text-blue-900">{t('contentManagement.editorPlaceholderTitle')}</h3>
        <p className="mt-1 text-sm text-blue-700">{t('contentManagement.editorPlaceholderDescription')}</p>
      </article>
    </section>
  )
}
