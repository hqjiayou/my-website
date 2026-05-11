import { templateMockService } from '@/mocks/services/template.mock'
import type { SiteTemplate, TemplateStatus } from '@/mocks/types/template'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

function notImplemented(name: string): never {
  throw new Error(`${name} is not implemented yet`)
}

export const templateApi = USE_MOCK
  ? templateMockService
  : {
      listTemplates: async () => notImplemented('site-service listTemplates'),
      getTemplate: async (_templateId: string) => notImplemented('site-service getTemplate'),
      getTemplateByCode: async (_code: string) => notImplemented('site-service getTemplateByCode'),
    }

export type { SiteTemplate, TemplateStatus }
