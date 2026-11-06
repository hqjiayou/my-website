import { Info } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { AdminPageHeader } from '@/components/admin'

import { ChainPresetsCard } from './chainPresetsCard'
import { ModelEntriesCard } from './modelEntriesCard'

/**
 * 模型 API 管理页（Issue #213 UoW-C 独立页；Issue #214 UoW-2 重构）：
 * 第一节是来源×模型（ModelEntriesCard，含 CLI 派生同步与代理账号管理），
 * 第二节是对话链模板（ChainPresetsCard；其独立页已于 UoW-3 撤除）。
 * 文案：modelManagement.* 与 settings.*。
 */
export function AiModelEntriesPage() {
  const { t } = useTranslation('aiAdmin')

  return (
    <section className="admin-content-form">
      <AdminPageHeader title={t('modelManagement.title')} description={t('modelManagement.description')} />
      <div className="space-y-4">
        <div className="flex gap-2 rounded-xl border border-info/28 bg-[rgb(var(--info)/.07)] px-4 py-3 text-sm text-admin">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-info" aria-hidden />
          <p>{t('settings.fallbackHint')}</p>
        </div>
        <ModelEntriesCard />
        <div className="space-y-2 pt-2">
          <h2 className="text-base font-semibold text-admin">{t('modelManagement.chainSectionTitle')}</h2>
          <p className="text-sm text-admin-muted">{t('modelManagement.chainSectionHint')}</p>
        </div>
        <ChainPresetsCard />
      </div>
    </section>
  )
}
