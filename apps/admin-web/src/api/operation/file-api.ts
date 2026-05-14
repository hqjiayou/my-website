/**
 * 功能注解：文件中心相关 API（文件列表查询）。
 */
import { fileMockService } from '@/mocks/services/file.mock'
import type { FileAsset } from '@/mocks/types/file'
import { httpClient } from '@/utils/http/axios'

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false'

const fileHttpService: typeof fileMockService = {
  listFiles: (siteId?: string) => httpClient.get<FileAsset[]>('/site/files', { params: { siteId } }),
}

export const fileApi = USE_MOCK ? fileMockService : fileHttpService
