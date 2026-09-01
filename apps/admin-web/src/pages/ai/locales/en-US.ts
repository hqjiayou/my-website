export default {
  loading: 'Loading…',
  empty: 'No data',
  error: 'Failed to load',
  retry: 'Retry',
  noSites: 'No sites yet. Create a site first.',
  siteLabel: 'Site',
  settings: {
    title: 'AI Runtime Settings',
    description: 'Explains how the deployed runtime picks a model. There is no Java AI Config table to edit.',
    body: 'The model is selected by the agent-services process environment variable AI_MODEL (Pydantic AI spec, no default).',
    envHint:
      'Per-site prompt and temperature live under Prompt settings. Java injects them when calling Python; the browser cannot send systemPrompt directly.',
    blockedHint:
      'Without AI_MODEL, preview and site chat fail honestly (503 / BLOCKED) instead of faking success.',
  },
  prompts: {
    title: 'Prompt Settings',
    description: 'Maintain per-site system prompt and temperature. Java injects them into the Python runtime.',
  },
  knowledgeBases: {
    title: 'Knowledge Bases',
    description: 'Maintain per-site knowledge documents (Java is source of truth; the index is a Python derived store).',
  },
  conversations: {
    title: 'Conversations',
    description: 'Read-only site user conversations and messages (conversation truth lives in Java).',
  },
} as const
