import { useQuery } from '@tanstack/react-query'
import type { LucideIcon } from 'lucide-react'
import { AlertTriangle, Code2, Palette, Settings2, SquareStack } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { systemApi } from '@/api'
import { PageHeader } from '@/components/page'

type DeveloperCardKey = 'codeConsole' | 'moduleScaffold' | 'templateDebug' | 'apiPlayground'

const DEVELOPER_CARDS: Array<{ key: DeveloperCardKey; icon: LucideIcon }> = [
  { key: 'codeConsole', icon: Code2 },
  { key: 'moduleScaffold', icon: SquareStack },
  { key: 'templateDebug', icon: Palette },
  { key: 'apiPlayground', icon: Settings2 },
]

export function DeveloperPage() {
  const { t } = useTranslation('developer')
  const { data } = useQuery({
    queryKey: ['developer-profile'],
    queryFn: systemApi.getDeveloperProfile,
  })

  return (
    <section>
      <PageHeader title={t('title')} description={t('description')} />

      <div className="grid gap-4 md:grid-cols-2">
        {DEVELOPER_CARDS.map(({ key, icon: Icon }) => (
          <article key={key} className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center gap-2 text-slate-900">
              <Icon className="h-4 w-4" />
              <h2 className="text-base font-semibold">{t(`cards.${key}.title`)}</h2>
            </div>
            <p className="mt-2 text-sm leading-6 text-slate-600">{t(`cards.${key}.description`)}</p>
          </article>
        ))}
      </div>

      <article className="mt-6 rounded-xl border border-amber-200 bg-amber-50 p-5">
        <div className="flex items-center gap-2 text-amber-900">
          <AlertTriangle className="h-4 w-4" />
          <h2 className="text-base font-semibold">{t('safety.title')}</h2>
        </div>
        <p className="mt-2 text-sm leading-6 text-amber-900/90">{t('safety.description')}</p>
        <p className="mt-2 text-sm leading-6 text-amber-900/90">{t('safety.accessPolicy')}</p>
        {data ? <p className="mt-2 text-xs text-amber-900/80">mock source: {data.source}</p> : null}
      </article>
    </section>
  )
}
