import { ChevronDown, Info } from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useSearchParams } from 'react-router-dom'

import { AdminPageHeader } from '@/components/admin'
import { cn } from '@/lib/cn'

import { ChainPresetsCard } from './chainPresetsCard'
import { ModelEntriesCard } from './modelEntriesCard'
import { parseModelEntryMode, type ModelEntryMode } from './modelEntriesShared'
import { ProxyClientKeysPane } from './proxyServicesPage'

/**
 * 模型 API 管理页：URL `?mode=CLI|KEY` 为模式真相源；
 * 非法 mode replace 规范化为 CLI。列表与同步下沉到 ModelEntriesCard。
 * Client Keys / 链模板为次级管理，默认折叠，不新增路由。
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
        <div className="flex gap-2 rounded-xl border border-info/28 bg-info-muted px-4 py-3 text-sm text-admin">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-info" aria-hidden />
          <p>{t('settings.fallbackHint')}</p>
        </div>
        <ModelEntriesCard mode={mode} onModeChange={setMode} />

        <nav
          className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-admin-muted"
          aria-label={t('modelManagement.secondaryNavLabel')}
        >
          <a href="#model-client-keys" className="underline-offset-2 hover:text-admin hover:underline">
            {t('proxyServices.usageKeysTitle')}
          </a>
          <a href="#model-chain-presets" className="underline-offset-2 hover:text-admin hover:underline">
            {t('modelManagement.chainSectionTitle')}
          </a>
        </nav>

        <SecondaryCollapsible
          id="model-client-keys"
          title={t('proxyServices.usageKeysTitle')}
          hint={t('modelManagement.keysPaneHint')}
        >
          <section className="overflow-hidden rounded-xl border border-admin bg-admin-surface">
            <ProxyClientKeysPane />
          </section>
        </SecondaryCollapsible>

        <SecondaryCollapsible
          id="model-chain-presets"
          title={t('modelManagement.chainSectionTitle')}
          hint={t('modelManagement.chainSectionHint')}
        >
          <ChainPresetsCard />
        </SecondaryCollapsible>
      </div>
    </section>
  )
}

/** 次级管理块：默认折叠，带锚点；展开不等于新建路由。 */
function SecondaryCollapsible({
  id,
  title,
  hint,
  children,
}: {
  id: string
  title: string
  hint: string
  children: ReactNode
}) {
  const { t } = useTranslation('aiAdmin')
  const [open, setOpen] = useState(false)

  useEffect(() => {
    if (typeof window === 'undefined') return
    const syncHash = () => {
      if (window.location.hash === `#${id}`) setOpen(true)
    }
    syncHash()
    window.addEventListener('hashchange', syncHash)
    return () => window.removeEventListener('hashchange', syncHash)
  }, [id])

  return (
    <section id={id} className="scroll-mt-20 space-y-2">
      <button
        type="button"
        aria-expanded={open}
        className="flex w-full items-start justify-between gap-3 rounded-xl border border-admin bg-admin-surface px-4 py-3 text-left transition hover:bg-admin-surface-muted"
        onClick={() => setOpen((value) => !value)}
      >
        <span className="min-w-0">
          <span className="block text-base font-semibold text-admin">{title}</span>
          <span className="mt-1 block text-sm text-admin-muted">{hint}</span>
        </span>
        <span className="flex shrink-0 items-center gap-1 text-xs text-admin-muted">
          {open ? t('modelManagement.secondaryCollapse') : t('modelManagement.secondaryExpand')}
          <ChevronDown
            className={cn('h-4 w-4 transition-transform duration-150', open && 'rotate-180')}
            aria-hidden
          />
        </span>
      </button>
      {open ? children : null}
    </section>
  )
}
