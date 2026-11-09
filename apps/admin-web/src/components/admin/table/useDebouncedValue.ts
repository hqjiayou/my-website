import { useEffect, useState } from 'react'

/** 受控值的防抖镜像；Enter 提交应由调用方立即使用源值，不依赖本 hook。 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    if (delayMs <= 0) return
    const timer = window.setTimeout(() => setDebounced(value), delayMs)
    return () => window.clearTimeout(timer)
  }, [value, delayMs])

  if (delayMs <= 0) return value
  return debounced
}
