import { lazy, type ComponentType, type LazyExoticComponent } from 'react'

/**
 * 将 named export 页面适配为 React.lazy（仅支持 default）。
 * 路由表继续用具名页面组件，避免整库改 default export。
 */
export function lazyNamed<T extends ComponentType>(
  loader: () => Promise<Record<string, T>>,
  exportName: string,
): LazyExoticComponent<T> {
  return lazy(async () => {
    const mod = await loader()
    const Component = mod[exportName]
    if (!Component) {
      throw new Error(`lazyNamed: missing export "${exportName}"`)
    }
    return { default: Component }
  })
}
