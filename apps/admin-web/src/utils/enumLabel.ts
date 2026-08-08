import type { TFunction } from 'i18next'

/** 后端枚举 → 展示文案（走 common.enum 命名空间）。 */
export function formatResourceType(t: TFunction, resourceType: string) {
  return t(`enum.resourceType.${resourceType}`, { ns: 'common', defaultValue: resourceType })
}

export function formatEntityStatus(t: TFunction, status: string) {
  return t(`enum.status.${status}`, { ns: 'common', defaultValue: status })
}
