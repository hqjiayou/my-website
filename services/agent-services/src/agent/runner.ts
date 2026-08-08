import type { AiProvider } from '../providers/provider.js'
import { buildPrompt, type PromptProfile } from './prompt-builder.js'

export interface RunAgentChatInput {
  siteId: string
  message: string
  promptProfile?: PromptProfile | null
}

export async function runAgentChat(provider: AiProvider, input: RunAgentChatInput) {
  const prompt = buildPrompt(input.siteId, input.message, input.promptProfile)
  return provider.generate({
    prompt,
    temperature: input.promptProfile?.temperature,
  })
}
