import { mockLlmReply } from '../adapters/llm-client.js'
import { buildPrompt } from './prompt-builder.js'

export async function runAgentChat(siteId: string, userMessage: string): Promise<string> {
  const prompt = buildPrompt(siteId, userMessage)
  return mockLlmReply(prompt)
}
