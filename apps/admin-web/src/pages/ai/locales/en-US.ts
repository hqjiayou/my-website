export default {
  mockNotice: 'ACTIVE MOCK data only (no real AI backend). With mock off, requests fail instead of faking success.',
  loading: 'Loading…',
  empty: 'No data',
  error: 'Failed to load',
  retry: 'Retry',
  settings: {
    title: 'AI Settings',
    description: 'Manage AI model, parameters, and enable state for each site.',
  },
  prompts: {
    title: 'Prompt Testing',
    description: 'Maintain prompt templates and validate output quality.',
  },
  knowledgeBases: {
    title: 'Knowledge Bases',
    description: 'Manage knowledge entries, indexing progress, and quality.',
  },
  conversations: {
    title: 'Conversations',
    description: 'Review site-level AI conversations and handling status.',
  },
} as const
