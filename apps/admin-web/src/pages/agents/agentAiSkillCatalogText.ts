/** 预览／运行状态／概览共用：优先 i18n catalog，缺省回退 API 英文 description。 */
export function skillCatalogDescription(
  id: string,
  fallback: string | null | undefined,
  t: (key: string, options?: { defaultValue?: string }) => string,
): string {
  const key = `aiWorkspace.preview.skills.catalog.${id}`
  const localized = t(key, { defaultValue: '' }).trim()
  if (localized) return localized
  return fallback?.trim() ?? ''
}
