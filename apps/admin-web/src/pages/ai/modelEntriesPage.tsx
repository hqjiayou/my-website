import { Info } from 'lucide-react'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'

import { AdminPageHeader } from '@/components/admin'

import { ChainPresetsCard } from './chainPresetsCard'
import { ModelEntriesCard } from './modelEntriesCard'
import { parseModelEntryMode, type ModelEntryMode } from './modelEntriesShared'

/**
 * 模型 API 管理页：URL `?mode=CLI|KEY` 为模式真相源；
 * 非法 mode replace 规范化为 CLI。列表与同步下沉到 ModelEntriesCard。
 */
export function AiModelEntriesPage() {
  const { t } = useTranslation('aiAdmin')
  const [searchParams, setSearchParams] = useSearchParams()
  const rawMode = searchParams.get('mode')
  const mode = parseModelEntryMode(rawMode)

  useEffect(() => {
    if (rawMode === 'CLI' || rawMode === 'KEY') return
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.set('mode', 'CLI')
        return next
      },
      { replace: true },
    )
  }, [rawMode, setSearchParams])

  function setMode(nextMode: ModelEntryMode) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      next.set('mode', nextMode)
      return next
    })
  }

  return (
    <section className="admin-content-data">
      <AdminPageHeader title={t('modelManagement.title')} description={t('modelManagement.description')} />
      <div className="space-y-4">
        <div className="flex gap-2 rounded-xl border border-info/28 bg-[rgb(var(--info)/.07)] px-4 py-3 text-sm text-admin">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-info" aria-hidden />
          <p>{t('settings.fallbackHint')}</p>
        </div>
        <ModelEntriesCard mode={mode} onModeChange={setMode} />
        <div className="space-y-2 pt-2">
          <h2 className="text-base font-semibold text-admin">{t('modelManagement.chainSectionTitle')}</h2>
          <p className="text-sm text-admin-muted">{t('modelManagement.chainSectionHint')}</p>
        </div>
        <ChainPresetsCard />
      </div>
    </section>
  )
}
