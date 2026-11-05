import { Info } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'

import { ModelEntriesCard } from './modelEntriesCard'

/** 模型入口独立页（Issue #213 UoW-C）：本体由 AI 设置页拆出，文案沿用 settings.*。 */
export function AiModelEntriesPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section className="admin-content-form">
      <AdminPageHeader title={t('settings.title')} description={t('settings.description')} />
      <div className="space-y-4">
        <div className="flex gap-2 rounded-xl border border-info/28 bg-[rgb(var(--info)/.07)] px-4 py-3 text-sm text-admin">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-info" aria-hidden />
          <p>{t('settings.fallbackHint')}</p>
        </div>
        <ModelEntriesCard />
      </div>
    </section>
  )
}
