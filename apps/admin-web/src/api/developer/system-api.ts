/**
 * 功能注解：系统与开发者信息 API（系统参数、开发者资料）。
 */
import { systemMockService } from '@/mocks/services/system.mock'
import type { DeveloperModuleProfile, SystemParam } from '@/mocks/types/system'
import { httpClient } from '@/utils/http/axios'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

const systemHttpService: typeof systemMockService = {
  listParams: () => httpClient.get<SystemParam[]>('/site/system/params'),
  getDeveloperProfile: () => httpClient.get<DeveloperModuleProfile>('/site/system/developer-profile'),
}

export const systemApi = USE_MOCK ? systemMockService : systemHttpService
