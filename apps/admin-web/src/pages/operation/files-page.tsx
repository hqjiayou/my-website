import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'

import { fileApi } from '@/api/file-api'
import { PageHeader } from '@/components/page'

export function OperationFilesPage() {
  const { t } = useTranslation('operation')
  const { data: files = [] } = useQuery({
    queryKey: ['operation', 'files'],
    queryFn: () => fileApi.listFiles(),
  })

  return (
    <section>
      <PageHeader title={t('files.title')} description={t('files.description')} />
      <div className="grid gap-3 md:grid-cols-2">
        {files.map((file) => (
          <article key={file.id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <h2 className="text-sm font-semibold text-slate-900">{file.name}</h2>
            <p className="mt-1 text-xs text-slate-600">{file.siteId}</p>
            <p className="mt-1 text-xs text-slate-500">{file.mimeType}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
