/**
 * 功能注解：内容管理相关 API（内容列表、分类型查询、创建与更新）。
 */
import { contentMockService } from '@/mocks/services/content.mock'
import type { ContentItem, ContentStatus, ContentType, CreateContentInput } from '@/mocks/types/content'
import { httpClient } from '@/utils/http/axios'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

const contentHttpService: typeof contentMockService = {
  listContents: (siteId?: string) => httpClient.get<ContentItem[]>('/site/contents', { params: { siteId } }),
  listContentsByType: (siteId: string, type: ContentType) =>
    httpClient.get<ContentItem[]>('/site/contents/by-type', { params: { siteId, type } }),
  getContent: (contentId: string) => httpClient.get<ContentItem | undefined>(`/site/contents/${contentId}`),
  createContent: (input: CreateContentInput) => httpClient.post<ContentItem, CreateContentInput>('/site/contents', input),
  updateContent: (contentId: string, input: Partial<CreateContentInput>) =>
    httpClient.patch<ContentItem | undefined, Partial<CreateContentInput>>(`/site/contents/${contentId}`, input),
}

export const contentApi = USE_MOCK ? contentMockService : contentHttpService

export type { ContentItem, ContentStatus, ContentType, CreateContentInput }
