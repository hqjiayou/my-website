import { systemMockService } from '@/mocks/services/system.mock'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

function notImplemented(name: string): never {
  throw new Error(`${name} is not implemented yet`)
}

export const systemApi = USE_MOCK
  ? systemMockService
  : {
      listParams: async () => notImplemented('site-service listParams'),
      getDeveloperProfile: async () => notImplemented('site-service getDeveloperProfile'),
    }
