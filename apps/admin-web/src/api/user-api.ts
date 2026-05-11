import { userMockService } from '@/mocks/services/user.mock'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

function notImplemented(name: string): never {
  throw new Error(`${name} is not implemented yet`)
}

export const userApi = USE_MOCK
  ? userMockService
  : {
      listUsers: async (_siteId?: string) => notImplemented('site-service listUsers'),
      listRoles: async () => notImplemented('site-service listRoles'),
    }
