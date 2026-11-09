import { useEffect, useState, useSyncExternalStore } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { FormDialog } from '@/components/admin'
import { adminFormActionsClass, adminPrimaryButtonClass, adminSecondaryButtonClass } from '@/components/admin/controls'

import {
  getUnsavedDirtySnapshot,
  setUnsavedDirtyKey,
  subscribeUnsavedDirty,
} from './unsavedDirtyStore'

/** 将本地 dirty 登记到配置区聚合 store；卸载时清除。 */
export function useUnsavedDirtyKey(key: string, dirty: boolean) {
  useEffect(() => {
    setUnsavedDirtyKey(key, dirty)
    return () => setUnsavedDirtyKey(key, false)
  }, [key, dirty])
}

function sameLocation(next: string) {
  const url = new URL(next, window.location.origin)
  const current = window.location.pathname + window.location.search + window.location.hash
  return url.pathname + url.search + url.hash === current
}

/**
 * 未保存离开保护（兼容 BrowserRouter，不依赖 data-router useBlocker）：
 * - beforeunload：刷新/关页
 * - 捕获同站 <a> 点击（Tabs / 配置子导航）
 * - tryNavigate：Agent 切换等编程式导航
 */
export function useUnsavedChangesGuard() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const when = useSyncExternalStore(subscribeUnsavedDirty, getUnsavedDirtySnapshot, () => false)
  const [pendingTo, setPendingTo] = useState<string | null>(null)

  useEffect(() => {
    if (!when) return
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [when])

  useEffect(() => {
    if (!when) return
    const onClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0) return
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
      const target = event.target
      if (!(target instanceof Element)) return
      const anchor = target.closest('a[href]')
      if (!(anchor instanceof HTMLAnchorElement)) return
      if (anchor.target === '_blank' || anchor.hasAttribute('download')) return
      const url = new URL(anchor.href, window.location.href)
      if (url.origin !== window.location.origin) return
      const next = `${url.pathname}${url.search}${url.hash}`
      if (sameLocation(next)) return
      event.preventDefault()
      event.stopPropagation()
      setPendingTo(next)
    }
    document.addEventListener('click', onClick, true)
    return () => document.removeEventListener('click', onClick, true)
  }, [when])

  function tryNavigate(to: string) {
    if (!when || sameLocation(to)) {
      navigate(to)
      return
    }
    setPendingTo(to)
  }

  const dialog =
    pendingTo != null ? (
      <FormDialog
        title={t('unsavedChanges.title')}
        closeLabel={t('unsavedChanges.stay')}
        onClose={() => setPendingTo(null)}
        size="s"
        danger
      >
        <p className="text-sm text-admin">{t('unsavedChanges.body')}</p>
        <div className={adminFormActionsClass}>
          <button type="button" className={adminSecondaryButtonClass} onClick={() => setPendingTo(null)}>
            {t('unsavedChanges.stay')}
          </button>
          <button
            type="button"
            className={adminPrimaryButtonClass}
            onClick={() => {
              const target = pendingTo
              setPendingTo(null)
              if (target) navigate(target)
            }}
          >
            {t('unsavedChanges.leave')}
          </button>
        </div>
      </FormDialog>
    ) : null

  return { when, tryNavigate, dialog }
}
