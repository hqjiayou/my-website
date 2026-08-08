export interface PromptProfile {
  systemPrompt?: string
  temperature?: number
}

export function buildPrompt(
  siteId: string,
  userMessage: string,
  promptProfile?: PromptProfile | null,
): string {
  const system =
    promptProfile?.systemPrompt?.trim() ||
    `You are the assistant for site: ${siteId}. Answer in a concise and helpful way.`

  return [system, `User message: ${userMessage}`].join('\n')
}
