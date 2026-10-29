/** CLI 入口存在库里的协议前缀。页面上展示模型 id 时去掉它。 */
export function modelIdFromSpec(spec: string) {
  return spec.startsWith('openai:') ? spec.slice('openai:'.length) : spec
}
