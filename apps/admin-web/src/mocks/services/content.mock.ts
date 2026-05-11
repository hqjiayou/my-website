import { MOCK_CONTENTS } from '@/mocks/db/contents'
import type { ContentItem, ContentType, CreateContentInput } from '@/mocks/types/content'
import { mockDelay } from '@/mocks/utils/delay'
import { createMockId } from '@/mocks/utils/id'

const contentsStore: ContentItem[] = [...MOCK_CONTENTS]

export const contentMockService = {
  async listContents(siteId?: string) {
    await mockDelay()
    if (!siteId) return [...contentsStore]
    return contentsStore.filter((item) => item.siteId === siteId)
  },
  async listContentsByType(siteId: string, type: ContentType) {
    await mockDelay()
    return contentsStore.filter((item) => item.siteId === siteId && item.type === type)
  },
  async getContent(contentId: string) {
    await mockDelay()
    return contentsStore.find((item) => item.id === contentId)
  },
  async createContent(input: CreateContentInput) {
    await mockDelay()
    const now = new Date().toISOString()
    const maxSortOrder = contentsStore
      .filter((item) => item.siteId === input.siteId && item.type === input.type)
      .reduce((max, item) => Math.max(max, item.sortOrder), 0)

    const created: ContentItem = {
      id: createMockId('content'),
      siteId: input.siteId,
      type: input.type,
      title: input.title,
      subtitle: input.subtitle,
      description: input.description,
      status: input.status,
      sortOrder: input.sortOrder ?? maxSortOrder + 10,
      imageUrl: input.imageUrl,
      tags: input.tags,
      createdAt: now,
      updatedAt: now,
    }
    contentsStore.unshift(created)
    return created
  },
  async updateContent(contentId: string, input: Partial<CreateContentInput>) {
    await mockDelay()
    const target = contentsStore.find((item) => item.id === contentId)
    if (!target) return undefined

    Object.assign(target, input, { updatedAt: new Date().toISOString() })
    return { ...target }
  },
}
