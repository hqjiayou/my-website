import { contentMockService } from '@/mocks/services/content.mock'
import type { ContentItem, ContentStatus, ContentType, CreateContentInput } from '@/mocks/types/content'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

function notImplemented(name: string): never {
  throw new Error(`${name} is not implemented yet`)
}

export const contentApi = USE_MOCK
  ? contentMockService
  : {
      listContents: async (_siteId?: string) => notImplemented('site-service listContents'),
      listContentsByType: async (_siteId: string, _type: ContentType) => notImplemented('site-service listContentsByType'),
      getContent: async (_contentId: string) => notImplemented('site-service getContent'),
      createContent: async (_input: CreateContentInput) => notImplemented('site-service createContent'),
      updateContent: async (_contentId: string, _input: Partial<CreateContentInput>) =>
        notImplemented('site-service updateContent'),
    }

export type { ContentItem, ContentStatus, ContentType, CreateContentInput }
