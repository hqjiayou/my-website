import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'

import { ChainPresetsCard } from './chainPresetsCard'

/** 对话链预设独立页（Issue #213 UoW-C）：卡片本体复用，文案沿用 settings.chainPresets.*。 */
export function AiChainPresetsPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section className="admin-content-form">
      <AdminPageHeader
        title={t('settings.chainPresets.title')}
        description={t('settings.chainPresets.hint')}
      />
      <ChainPresetsCard />
    </section>
  )
}
