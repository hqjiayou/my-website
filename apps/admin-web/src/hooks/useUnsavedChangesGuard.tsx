import { useEffect, useSyncExternalStore } from 'react'
import { useTranslation } from 'react-i18next'
import { useBlocker, useNavigate } from 'react-router-dom'

import { FormDialog } from '@/components/admin'
import { adminFormActionsClass, adminPrimaryButtonClass, adminSecondaryButtonClass } from '@/components/admin/controls'
import { ROUTES } from '@/router/config/routePaths'

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

/**
 * 未保存离开保护（需 data router：main.tsx 的 createBrowserRouter + RouterProvider）：
 * - useBlocker：拦截所有经 router 的离开——Link / NavLink 点击、navigate()、浏览器后退/前进；
 *   仅 pathname 变化才拦（同页 search / hash 变化不丢草稿）。后退/前进的历史回滚由 router 内部处理，无需手补。
 * - beforeunload：刷新 / 关页 / 跳出站外。
 * - tryNavigate：Agent 切换等编程式导航入口；实际拦截由 blocker 完成，这里只负责 navigate。
 * 用户选「放弃并离开」走 blocker.proceed()，「继续编辑」走 blocker.reset()，不再次触发 blocker。
 */
export function useUnsavedChangesGuard() {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const when = useSyncExternalStore(subscribeUnsavedDirty, getUnsavedDirtySnapshot, () => false)

  const blocker = useBlocker(({ currentLocation, nextLocation }) => {
    if (!when || currentLocation.pathname === nextLocation.pathname) return false
    // 401 / 主动登出进登录页不得被草稿永久卡住。
    if (nextLocation.pathname === ROUTES.login) return false
    return true
  })

  useEffect(() => {
    if (!when) return
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [when])

  // 拦截期间草稿被清空（例如保存成功）：不再有未保存内容，直接放行，避免对话框悬挂。
  useEffect(() => {
    if (blocker.state === 'blocked' && !when) blocker.proceed()
  }, [blocker, when])

  function tryNavigate(to: string) {
    navigate(to)
  }

  const dialog =
    blocker.state === 'blocked' && when ? (
      <FormDialog
        title={t('unsavedChanges.title')}
        closeLabel={t('unsavedChanges.stay')}
        onClose={() => blocker.reset()}
        size="s"
        danger
      >
        <p className="text-sm text-admin">{t('unsavedChanges.body')}</p>
        <div className={adminFormActionsClass}>
          <button type="button" className={adminSecondaryButtonClass} onClick={() => blocker.reset()}>
            {t('unsavedChanges.stay')}
          </button>
          <button type="button" className={adminPrimaryButtonClass} onClick={() => blocker.proceed()}>
            {t('unsavedChanges.leave')}
          </button>
        </div>
      </FormDialog>
    ) : null

  return { when, tryNavigate, dialog }
}
