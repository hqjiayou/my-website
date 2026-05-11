import { fileMockService } from '@/mocks/services/file.mock'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

function notImplemented(name: string): never {
  throw new Error(`${name} is not implemented yet`)
}

export const fileApi = USE_MOCK
  ? fileMockService
  : {
      listFiles: async (_siteId?: string) => notImplemented('site-service listFiles'),
    }
