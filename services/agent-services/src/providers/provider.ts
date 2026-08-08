export type ProviderStatus = 'LIVE' | 'BLOCKED'

export interface ProviderGenerateRequest {
  prompt: string
  temperature?: number
}

export interface ProviderGenerateSuccess {
  ok: true
  status: 'LIVE'
  text: string
}

export interface ProviderGenerateBlocked {
  ok: false
  status: 'BLOCKED'
  errorCode: 'EXTERNAL_DEPENDENCY_BLOCKED'
  message: string
}

export type ProviderGenerateResult = ProviderGenerateSuccess | ProviderGenerateBlocked

export interface AiProvider {
  readonly name: string
  generate(request: ProviderGenerateRequest): Promise<ProviderGenerateResult>
}
