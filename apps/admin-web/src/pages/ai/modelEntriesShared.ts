import type { AiModelEntry } from '@/types/ai'
import { modelIdFromSpec } from '@/utils/modelSpec'

/** URL / FilterTabs 双模式：非法值规范化为 CLI。 */
export type ModelEntryMode = 'CLI' | 'KEY'

export type ModelEntryDraft = {
  id: number | null
  displayName: string
  modelSpec: string
  apiKey: string
  baseUrl: string
  sourceAgentId: number | null
  embeddingSpec: string
  enabled: boolean
  defaultEntry: boolean
  mode: string
  alias: string
  version: number | null
  apiKeyHint: string | null
}

export const KEY_VENDOR_NAMES: Record<string, string> = {
  google: 'Google',
  openai: 'OpenAI',
  anthropic: 'Anthropic',
  azure: 'Azure',
}

export const KNOWN_KEY_VENDORS = ['google', 'openai', 'anthropic', 'azure']
export const CLI_VENDOR_ORDER = ['codex', 'claude', 'antigravity', 'meta']

export function parseModelEntryMode(raw: string | null | undefined): ModelEntryMode {
  return raw === 'KEY' ? 'KEY' : 'CLI'
}

export function emptyDraft(makeDefault: boolean, specPrefixValue = ''): ModelEntryDraft {
  return {
    id: null,
    displayName: '',
    modelSpec: specPrefixValue,
    apiKey: '',
    baseUrl: '',
    sourceAgentId: null,
    embeddingSpec: '',
    enabled: true,
    defaultEntry: makeDefault,
    mode: 'KEY',
    alias: '',
    version: null,
    apiKeyHint: null,
  }
}

export function draftFromEntry(entry: AiModelEntry): ModelEntryDraft {
  return {
    id: entry.id,
    displayName: entry.displayName,
    modelSpec: entry.modelSpec,
    apiKey: '',
    baseUrl: entry.baseUrl ?? '',
    sourceAgentId: entry.sourceAgentId,
    embeddingSpec: entry.embeddingSpec ?? '',
    enabled: entry.enabled,
    defaultEntry: entry.defaultEntry,
    mode: entry.mode ?? 'KEY',
    alias: entry.alias ?? '',
    version: entry.version,
    apiKeyHint: entry.apiKeyHint,
  }
}

export function keyVendorName(prefix: string) {
  return KEY_VENDOR_NAMES[prefix] ?? (prefix ? prefix.charAt(0).toUpperCase() + prefix.slice(1) : prefix)
}

/** 家族图标砖：按 spec 协议前缀映射色板 token。CLI 厂商砖走 ProviderIcon，不经这里。 */
export function familyTile(prefix: string): { letter: string; className: string } {
  if (prefix === 'google') return { letter: 'G', className: 'bg-[rgb(var(--info)/.12)] text-info' }
  if (prefix === 'openai') return { letter: 'O', className: 'bg-[rgb(var(--chart-2)/.12)] text-chart-2' }
  if (prefix === 'anthropic') return { letter: 'A', className: 'bg-[rgb(var(--chart-3)/.12)] text-chart-3' }
  if (prefix === 'azure') return { letter: 'Az', className: 'bg-[rgb(var(--chart-1)/.12)] text-chart-1' }
  return { letter: 'M', className: 'bg-admin-surface-muted text-admin-muted' }
}

export function cliModelSpec(modelId: string) {
  const trimmed = modelId.trim()
  if (!trimmed) return trimmed
  return trimmed.includes(':') ? trimmed : `openai:${trimmed}`
}

export function specPrefix(spec: string) {
  return spec.split(':')[0]?.trim().toLowerCase() || 'other'
}

/**
 * CLI 条目的厂商推断（账号匹配不到时用）：按模型名约定归类，与登录供应商
 * 的 provider id 对齐（claude→claude、gemini→antigravity、llama/meta→meta，
 * 其余默认 codex——CLI 派生 spec 由系统统一盖 openai 前缀，GPT 系为主）。
 */
export function inferredCliProvider(entry: AiModelEntry): string {
  const id = modelIdFromSpec(entry.modelSpec).toLowerCase()
  if (id.startsWith('claude')) return 'claude'
  if (id.startsWith('gemini') || id.startsWith('antigravity')) return 'antigravity'
  if (id.startsWith('llama') || id.startsWith('meta-')) return 'meta'
  return 'codex'
}

export function matchesLocalQuery(haystacks: Array<string | null | undefined>, query: string) {
  const needle = query.trim().toLowerCase()
  if (!needle) return true
  return haystacks.some((value) => (value ?? '').toLowerCase().includes(needle))
}

export function formatEntryUpdatedAt(value: string | null | undefined) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}
