import type { AiProvider, ProviderGenerateRequest, ProviderGenerateResult } from './provider.js'

export type BlockedReason = 'missing_api_key' | 'provider_not_implemented'

const BLOCKED_MESSAGES: Record<BlockedReason, string> = {
  missing_api_key: 'AI provider is not configured (missing API key).',
  provider_not_implemented:
    'AI provider API key is set but the provider SDK is not implemented yet.',
}

/**
 * 默认 Provider：诚实失败，禁止伪造 LIVE 成功。
 * 区分「无 Key」与「Key 已设但 SDK 未接入」。
 */
export class BlockedProvider implements AiProvider {
  readonly name = 'blocked'
  private readonly message: string

  constructor(reason: BlockedReason = 'missing_api_key') {
    this.message = BLOCKED_MESSAGES[reason]
  }

  async generate(_request: ProviderGenerateRequest): Promise<ProviderGenerateResult> {
    return {
      ok: false,
      status: 'BLOCKED',
      errorCode: 'EXTERNAL_DEPENDENCY_BLOCKED',
      message: this.message,
    }
  }
}
