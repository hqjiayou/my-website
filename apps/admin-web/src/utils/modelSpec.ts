/** CLI 入口存在库里的协议前缀。页面上展示模型 id 时去掉它。 */
export function modelIdFromSpec(spec: string) {
  return spec.startsWith('openai:') ? spec.slice('openai:'.length) : spec
}

/** 嵌入类登记上限，与后端 AgentEmbeddingClassUtils.MAX_EMBEDDING_CLASSES 一致：前端先拦，后端仍会校验。 */
export const MAX_EMBEDDING_CLASSES = 4

/**
 * 规范化类键，与后端 AgentEmbeddingClassUtils.className 同口径：
 * trim 后 google-gla: 并入 google:。只用来在登记添加时先拦同类重复，后端保存时仍按同口径判 409。
 */
export function embeddingClassName(spec: string | null | undefined): string | null {
  if (!spec || !spec.trim()) return null
  const text = spec.trim()
  return text.startsWith('google-gla:') ? `google:${text.slice('google-gla:'.length)}` : text
}
