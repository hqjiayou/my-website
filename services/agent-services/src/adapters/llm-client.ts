export async function mockLlmReply(prompt: string): Promise<string> {
  const snippet = prompt.slice(0, 80).replace(/\s+/g, ' ')
  return `【Mock AI 回复】已收到你的咨询：${snippet}...`
}
