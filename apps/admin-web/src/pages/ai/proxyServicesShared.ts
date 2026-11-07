import type { ProxyLoginProvider, ProxyServicesAccount } from '@/types/ai'

/**
 * CLI 代理的共享纯函数（供应商标签、图标砖色、账号标题/状态、模型过滤）：
 * 模型页的条目卡（modelEntriesCard）与代理组件（proxyServicesPage）共用，
 * 独立成非组件模块以免触发 react-refresh 的 only-export-components 规则。
 */
export const LOGIN_OPTIONS: { id: ProxyLoginProvider; titleKey: string; noteKey: string }[] = [
  { id: 'codex', titleKey: 'proxyServices.providerCodex', noteKey: 'proxyServices.loginCodexNote' },
  { id: 'claude', titleKey: 'proxyServices.providerClaude', noteKey: 'proxyServices.loginClaudeNote' },
  { id: 'antigravity', titleKey: 'proxyServices.providerGemini', noteKey: 'proxyServices.loginGeminiNote' },
  { id: 'meta', titleKey: 'proxyServices.providerMuse', noteKey: 'proxyServices.loginMuseNote' },
]

export function isLoginProvider(provider: string): provider is ProxyLoginProvider {
  return LOGIN_OPTIONS.some((option) => option.id === provider)
}

export function providerLabel(provider: string, t: (key: string) => string) {
  if (provider === 'codex') return t('proxyServices.providerCodex')
  if (provider === 'claude') return t('proxyServices.providerClaude')
  if (provider === 'antigravity') return t('proxyServices.providerGemini')
  if (provider === 'meta') return t('proxyServices.providerMuse')
  return provider
}

export function isReady(account: { disabled: boolean; unavailable: boolean }) {
  return !account.disabled && !account.unavailable
}

/** 家族色映射（设计稿）：GPT=chart-2、Claude=chart-3、Gemini=info、Muse/其他=chart-4。 */
export function providerTileClass(provider: string) {
  if (provider === 'codex') return 'bg-[rgb(var(--chart-2)/.13)] text-[rgb(var(--chart-2))]'
  if (provider === 'claude') return 'bg-[rgb(var(--chart-3)/.14)] text-[rgb(var(--chart-3))]'
  if (provider === 'antigravity') return 'bg-[rgb(var(--info)/.12)] text-[rgb(var(--info))]'
  if (provider === 'meta') return 'bg-[rgb(var(--chart-4)/.12)] text-[rgb(var(--chart-4))]'
  return 'bg-admin-surface-muted text-admin-muted'
}

export function accountTitle(account: ProxyServicesAccount) {
  if (account.email) return account.email
  const stripped = account.name.replace(/\.json$/i, '')
  const found = stripped.match(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i)
  if (!found) return stripped.replace(/^(codex|claude|antigravity|meta)-/i, '')
  const at = found[0].lastIndexOf('@')
  const local = found[0].slice(0, at).split('-').pop()
  return `${local}@${found[0].slice(at + 1)}`
}

export function accountStatus(account: ProxyServicesAccount, t: (key: string) => string) {
  if (account.disabled) return { tone: 'disabled' as const, label: t('proxyServices.disabled') }
  if (account.unavailable) return { tone: 'warning' as const, label: t('proxyServices.unavailable') }
  return { tone: 'active' as const, label: t('proxyServices.accountReady') }
}

export function shownModelIds(provider: string, modelIds: string[] | null | undefined) {
  if (modelIds == null || provider === 'claude') return modelIds
  return modelIds.filter((id) => !id.toLowerCase().startsWith('claude'))
}
