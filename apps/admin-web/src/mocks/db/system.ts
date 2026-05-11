import type { DeveloperModuleProfile, SystemParam } from '@/mocks/types/system'

export const MOCK_SYSTEM_PARAMS: SystemParam[] = [
  {
    id: 'sys-env',
    key: 'platform.env',
    value: 'mock',
    description: '当前环境标识',
    updatedAt: '2026-05-10T01:20:00Z',
  },
  {
    id: 'sys-timezone',
    key: 'platform.timezone',
    value: 'Asia/Tokyo',
    description: '后台默认时区',
    updatedAt: '2026-05-09T04:20:00Z',
  },
]

export const MOCK_DEVELOPER_PROFILE: DeveloperModuleProfile = {
  source: 'mock',
  capabilities: [
    { key: 'codeConsole', enabled: true },
    { key: 'moduleScaffold', enabled: true },
    { key: 'templateDebug', enabled: true },
    { key: 'apiPlayground', enabled: true },
  ],
}
