import { BlockedProvider } from './blocked-provider.js'
import type { AiProvider } from './provider.js'

/**
 * 仅当显式配置 Provider Key 时才启用真实 Provider；否则一律 BlockedProvider。
 * Phase 10 不实现具体 LLM SDK，避免伪造 LIVE。
 */
export function createAiProvider(env: NodeJS.ProcessEnv = process.env): AiProvider {
  const apiKey = env.AI_PROVIDER_API_KEY?.trim()
  if (!apiKey) {
    return new BlockedProvider('missing_api_key')
  }

  // Key 存在但本阶段未接入具体 SDK：仍不得伪造 LIVE，继续 blocked（文案与无 Key 区分）。
  // 后续接入 OpenAI 等时在此分支返回真实实现。
  return new BlockedProvider('provider_not_implemented')
}
