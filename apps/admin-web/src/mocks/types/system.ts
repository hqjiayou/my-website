export interface SystemParam {
  id: string
  key: string
  value: string
  description: string
  updatedAt: string
}

export interface DeveloperCapability {
  key: 'codeConsole' | 'moduleScaffold' | 'templateDebug' | 'apiPlayground'
  enabled: boolean
}

export interface DeveloperModuleProfile {
  source: 'mock'
  capabilities: DeveloperCapability[]
}
