/**
 * 功能注解：站点模板相关 API（模板列表与模板详情查询）。
 */
import { templateMockService } from '@/mocks/services/template.mock'
import type { SiteTemplate, TemplateStatus } from '@/mocks/types/template'
import { httpClient } from '@/utils/http/axios'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

const templateHttpService: typeof templateMockService = {
  listTemplates: () => httpClient.get<SiteTemplate[]>('/site/templates'),
  getTemplate: (templateId: string) => httpClient.get<SiteTemplate | undefined>(`/site/templates/${templateId}`),
  getTemplateByCode: (code: string) =>
    httpClient.get<SiteTemplate | undefined>(`/site/templates/by-code/${encodeURIComponent(code)}`),
}

export const templateApi = USE_MOCK ? templateMockService : templateHttpService

export type { SiteTemplate, TemplateStatus }
