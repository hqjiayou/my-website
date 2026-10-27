import { createContext, useContext, type ReactNode } from 'react'

/**
 * 侧边栏扩展区通道：页面（如聊天页的会话列表）把内容注入 AppShell 的侧边栏，
 * 并可在移动端关闭抽屉。导航本体仍只有 AppShell 一处。
 */
export const SidebarExtraContext = createContext<{
  setExtra: (node: ReactNode) => void
  closeDrawer: () => void
}>({
  setExtra: () => undefined,
  closeDrawer: () => undefined,
})

export function useSidebarExtra() {
  return useContext(SidebarExtraContext)
}
