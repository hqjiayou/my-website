/** 判断当前路由是否属于某个导航叶子（含子路径） */
export function pathMatchesLeaf(pathname: string, path: string) {
  return pathname === path || pathname.startsWith(`${path}/`)
}
