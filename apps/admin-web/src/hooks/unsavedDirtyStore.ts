type Listener = () => void

const dirtyKeys = new Map<string, boolean>()
const listeners = new Set<Listener>()

function emit() {
  listeners.forEach((listener) => listener())
}

/** 登记某一编辑域是否有未保存草稿（跨组件聚合，供配置区统一拦截）。 */
export function setUnsavedDirtyKey(key: string, dirty: boolean) {
  const wasDirty = dirtyKeys.get(key) === true
  if (dirty === wasDirty) return
  if (dirty) dirtyKeys.set(key, true)
  else dirtyKeys.delete(key)
  emit()
}

export function subscribeUnsavedDirty(listener: Listener) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function getUnsavedDirtySnapshot() {
  for (const dirty of dirtyKeys.values()) {
    if (dirty) return true
  }
  return false
}

/** 会话过期强制退出等场景：清空全部草稿标记，避免 useBlocker 卡住登出导航。 */
export function clearAllUnsavedDirtyKeys() {
  if (dirtyKeys.size === 0) return
  dirtyKeys.clear()
  emit()
}
