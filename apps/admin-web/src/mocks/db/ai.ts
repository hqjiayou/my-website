import type { AiConfig, AiConversation, AiPrompt, KnowledgeBase } from '@/mocks/types/ai'

export const MOCK_AI_CONFIGS: AiConfig[] = [
  {
    id: 'ai-config-car',
    siteId: '1',
    model: 'gpt-4o-mini',
    temperature: 0.4,
    enabled: true,
    updatedAt: '2026-05-10T04:00:00Z',
  },
  {
    id: 'ai-config-cake',
    siteId: '3',
    model: 'gpt-4o-mini',
    temperature: 0.6,
    enabled: true,
    updatedAt: '2026-05-09T01:00:00Z',
  },
]

export const MOCK_AI_PROMPTS: AiPrompt[] = [
  {
    id: 'prompt-reservation-followup',
    scene: 'reservation-followup',
    title: '预约追踪',
    content: '请以友好语气确认用户预约时间，并补充所需材料。',
    updatedAt: '2026-05-09T03:00:00Z',
  },
  {
    id: 'prompt-visa-faq',
    scene: 'visa-faq',
    title: '签证咨询答复',
    content: '优先引用事务所提供的知识库内容，不可编造政策信息。',
    updatedAt: '2026-05-07T03:00:00Z',
  },
]

export const MOCK_KNOWLEDGE_BASES: KnowledgeBase[] = [
  {
    id: 'kb-car-services',
    siteId: '1',
    name: '维修服务知识库',
    documentCount: 24,
    status: 'ready',
    updatedAt: '2026-05-08T02:00:00Z',
  },
  {
    id: 'kb-visa-procedures',
    siteId: '5',
    name: '签证流程知识库',
    documentCount: 18,
    status: 'indexing',
    updatedAt: '2026-05-11T00:00:00Z',
  },
]

export const MOCK_AI_CONVERSATIONS: AiConversation[] = [
  {
    id: 'conv-001',
    siteId: '1',
    visitorName: 'Aki',
    question: '周末可以预约机油更换吗？',
    status: 'resolved',
    createdAt: '2026-05-11T02:10:00Z',
  },
  {
    id: 'conv-002',
    siteId: '5',
    visitorName: 'Chen',
    question: '更新签证需要提前多久准备？',
    status: 'open',
    createdAt: '2026-05-11T05:20:00Z',
  },
]
