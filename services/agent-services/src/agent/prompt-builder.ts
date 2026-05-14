export function buildPrompt(siteId: string, userMessage: string): string {
  return [
    `You are the assistant for site: ${siteId}.`,
    'Answer in a concise and helpful way.',
    `User message: ${userMessage}`,
  ].join('\n')
}
