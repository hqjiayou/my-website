import type { Page, Route } from '@playwright/test'

/** 统一业务包装；与 unwrapData 契约一致。 */
function ok<T>(data: T) {
  return { code: 0, message: 'ok', data }
}

const AGENT = {
  id: 42,
  agentCode: 'demo-agent',
  agentName: 'Demo Agent',
  agentType: 'general',
  agentKind: 'AI_ONLY',
  status: 'ACTIVE',
  defaultDomain: null,
  description: 'mock',
  version: 1,
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-09T00:00:00Z',
  subscriptionCount: 1,
  chainKeyCount: 1,
  chainCliCount: 0,
  promptTemplateId: null,
  promptTemplateName: null,
  promptConfigured: true,
  defaultSkillCount: 1,
}

const FULL_PERMISSIONS = [
  'admin:agent:list',
  'admin:agent:status',
  'admin:agent:delete',
  'admin:ai-prompt:read',
  'admin:ai-prompt:update',
  'admin:ai-kb:list',
  'admin:ai-kb:update',
  'admin:ai-conversation:list',
  'admin:ai-model:read',
  'admin:ai-model:update',
]

const MODEL_ENTRY_KEY = {
  id: 1,
  displayName: 'Mock Model',
  modelSpec: 'openai:gpt-test',
  mode: 'KEY',
  enabled: true,
  defaultEntry: true,
  derived: false,
  derivedSource: null,
  apiKeyConfigured: true,
  apiKeyHint: 'sk-…test',
  baseUrl: 'https://api.openai.com/v1',
  alias: null,
  sourceAgentId: null,
  embeddingSpec: null,
  version: 1,
  updatedAt: '2026-11-09T00:00:00Z',
}

/** CLI 派生行：只读；mock 故意不带 apiKey 明文。 */
const MODEL_ENTRY_CLI = {
  id: 2,
  displayName: 'gpt-test-cli',
  modelSpec: 'openai:gpt-test-cli',
  mode: 'CLI',
  enabled: true,
  defaultEntry: false,
  derived: true,
  derivedSource: 'PROXY',
  apiKeyConfigured: true,
  apiKeyHint: 'cli-…key',
  baseUrl: 'http://127.0.0.1:8317/v1',
  alias: null,
  sourceAgentId: null,
  embeddingSpec: null,
  version: 1,
  updatedAt: '2026-11-09T00:00:00Z',
}

const MODEL_ENTRY_DISABLED = {
  ...MODEL_ENTRY_KEY,
  id: 3,
  displayName: 'Disabled Model',
  modelSpec: 'openai:gpt-disabled',
  enabled: false,
  defaultEntry: false,
  apiKeyHint: 'sk-…off',
}

const MODEL_ENTRIES = [MODEL_ENTRY_KEY, MODEL_ENTRY_CLI, MODEL_ENTRY_DISABLED]

const AGENT_B = {
  ...AGENT,
  id: 43,
  agentCode: 'other-agent',
  agentName: 'Other Agent',
  subscriptionCount: 0,
  chainKeyCount: 0,
}

const SKILL_CATALOG = {
  skills: [
    {
      id: 'orchestration',
      description: 'Orchestrate multi-step work with Todo.',
      version: '1.0.0',
      license: null,
      compatibility: null,
      metadata: {},
      allowedTools: [],
    },
    {
      id: 'doc-qa-cited',
      description: 'Answer with citations from knowledge docs.',
      version: '1.0.0',
      license: null,
      compatibility: null,
      metadata: {},
      allowedTools: [],
    },
  ],
  skillsSelfSelectEnabled: true,
}

const MCP_CONFIG = {
  servers: [
    {
      name: 'filesystem',
      transport: 'stdio',
      urlConfigured: false,
      urlHint: null,
      enabled: true,
      toolPrefix: null,
      initTimeoutSeconds: null,
      command: 'npx',
      args: [],
      cwd: null,
      headers: [],
      env: [],
    },
    {
      name: 'legacy-off',
      transport: 'stdio',
      urlConfigured: false,
      urlHint: null,
      enabled: false,
      toolPrefix: null,
      initTimeoutSeconds: null,
      command: 'npx',
      args: [],
      cwd: null,
      headers: [],
      env: [],
    },
  ],
  configVersion: 1,
  lastPushStatus: null,
  lastPushMessage: null,
  lastPushedAt: null,
  version: 1,
  updatedAt: null,
}

const PROXY_ACCOUNT = {
  name: 'codex-demo.json',
  provider: 'codex',
  disabled: false,
  unavailable: false,
  email: 'demo@example.com',
  success: 3,
  failed: 0,
  usedPercent: 12,
  modelUsedPercent: { 'gpt-test-cli': 8 },
}

const PROXY_STATUS = {
  name: 'CLIProxyAPI',
  baseUrl: 'http://127.0.0.1:8317',
  reachable: true,
  managementConfigured: true,
  accounts: [PROXY_ACCOUNT],
  accountPacing: null,
}

const PROXY_USAGE = {
  apiBaseUrl: 'http://127.0.0.1:8317/v1',
  callable: true,
  accounts: [
    {
      name: PROXY_ACCOUNT.name,
      provider: PROXY_ACCOUNT.provider,
      disabled: false,
      unavailable: false,
      modelIds: ['gpt-test-cli'],
    },
  ],
}

const KNOWLEDGE_BASE_OWNED = {
  id: 7,
  agentId: 42,
  ownerAgentName: 'Demo Agent',
  name: 'Demo KB',
  isDefault: true,
  owned: true,
  documentCount: 2,
  linkedAgentIds: [] as number[],
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-09T00:00:00Z',
}

const KNOWLEDGE_BASE_OTHER = {
  id: 8,
  agentId: 43,
  ownerAgentName: 'Other Agent',
  name: 'Shared KB',
  isDefault: false,
  owned: false,
  documentCount: 5,
  linkedAgentIds: [] as number[],
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-09T00:00:00Z',
}

/** 关联 POST 故意失败，用于 PR-4.1 部分成功冒烟。 */
const KNOWLEDGE_BASE_FAIL = {
  id: 9,
  agentId: 43,
  ownerAgentName: 'Other Agent',
  name: 'Fail KB',
  isDefault: false,
  owned: false,
  documentCount: 1,
  linkedAgentIds: [] as number[],
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-09T00:00:00Z',
}

export type MockAuthOptions = {
  /** 覆盖 /admin/auth/me 的 permissions；用于无权限冒烟。 */
  permissions?: string[]
}

function buildMe(permissions: string[]) {
  return {
    user: { id: 1, username: 'mock-admin', displayName: 'Mock Admin', status: 'ACTIVE' },
    roles: ['SUPER_ADMIN'],
    permissions,
    menus: [
      {
        id: 1,
        parentId: null,
        name: 'Agents',
        path: '/agent',
        icon: 'layers',
        sortOrder: 1,
        children: [],
      },
    ],
  }
}

async function json(route: Route, body: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  })
}

/**
 * Agent Console V2 浏览器冒烟用 Mock API。
 * 标注：此处不验证真实后端契约，只覆盖路由/布局/权限 UI。
 */
export async function installAgentConsoleMocks(page: Page, options: MockAuthOptions = {}) {
  const me = buildMe(options.permissions ?? FULL_PERMISSIONS)
  let chainState = { agentId: 42, keyEntryIds: [1], cliEntryIds: [] as number[] }
  let promptState = {
    agentId: 42,
    systemPrompt: 'You are a helpful assistant.',
    temperature: 0.2,
    promptTemplateId: null as number | null,
    promptTemplateName: null as string | null,
    defaultSkillIds: [] as string[],
  }
  let mcpSelection = { mode: 'INHERIT' as string, serverNames: [] as string[] }
  const knowledgeBases = [
    { ...KNOWLEDGE_BASE_OWNED, linkedAgentIds: [] as number[] },
    { ...KNOWLEDGE_BASE_OTHER, linkedAgentIds: [] as number[] },
    { ...KNOWLEDGE_BASE_FAIL, linkedAgentIds: [] as number[] },
  ]

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const method = request.method()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/api/, '')

    if (path === '/auth/admin/login' && method === 'POST') {
      await json(route, ok({ accessToken: 'mock-access-token', expiresIn: 3600 }))
      return
    }
    if (path === '/admin/auth/me' && method === 'GET') {
      await json(route, ok(me))
      return
    }
    if (path === '/admin/agents' && method === 'GET') {
      await json(
        route,
        ok({
          records: [AGENT, AGENT_B],
          total: 2,
          pageNum: Number(url.searchParams.get('pageNum') ?? 1),
          pageSize: Number(url.searchParams.get('pageSize') ?? 20),
        }),
      )
      return
    }
    if (path === '/admin/agents' && method === 'POST') {
      await json(route, ok({ ...AGENT, id: 99, agentName: 'Created Agent', agentCode: 'created-agent' }))
      return
    }
    if (/^\/admin\/agents\/\d+$/.test(path) && method === 'GET') {
      const id = Number(path.split('/').pop())
      await json(route, ok(id === 43 ? AGENT_B : AGENT))
      return
    }
    if (/^\/admin\/agents\/\d+$/.test(path) && (method === 'PUT' || method === 'PATCH')) {
      await json(route, ok({ ...AGENT, version: 2 }))
      return
    }
    if (/^\/admin\/agents\/\d+\/kind$/.test(path) && method === 'PUT') {
      await json(route, ok({ ...AGENT, agentKind: 'SITE', version: 2 }))
      return
    }
    if (/\/ai\/chain$/.test(path) && method === 'GET') {
      const agentId = Number(path.split('/')[3])
      await json(route, ok({ ...chainState, agentId }))
      return
    }
    if (/\/ai\/chain\/(KEY|CLI)$/.test(path) && method === 'PUT') {
      const mode = path.endsWith('/KEY') ? 'KEY' : 'CLI'
      const body = request.postDataJSON() as { entryIds?: number[] }
      const entryIds = body.entryIds ?? []
      chainState =
        mode === 'KEY'
          ? { ...chainState, keyEntryIds: entryIds }
          : { ...chainState, cliEntryIds: entryIds }
      await json(route, ok({ ...chainState }))
      return
    }
    if (/\/ai\/chain\//.test(path)) {
      await json(route, ok({ ...chainState }))
      return
    }
    if (/\/ai\/runtime$/.test(path)) {
      await json(
        route,
        ok({
          reachable: true,
          errorCode: null,
          status: {
            provider: 'openai:gpt-test',
            providerStatus: 'READY',
            fallbackProviders: [],
            mcpStatus: 'READY',
            mcpServers: [],
            limits: {
              maxRequests: null,
              maxToolCalls: null,
              maxTotalTokens: null,
              toolTimeoutSeconds: null,
              historyMaxChars: null,
            },
            retrievalStatus: 'READY',
            retrievalBlockedReason: null,
            harness: { background: true, guardrails: true, toolGuidance: true },
          },
        }),
      )
      return
    }
    if (path === '/admin/ai/model-entries/sync-cli' && method === 'POST') {
      await json(
        route,
        ok({
          status: 'OK',
          proxyReady: true,
          created: 0,
          reactivated: 0,
          deactivated: 0,
          unchanged: 1,
          skippedNoClientKey: 0,
        }),
      )
      return
    }
    if (path === '/admin/ai/model-entries' && method === 'GET') {
      // 永不回显密钥明文；仅脱敏 hint。
      await json(route, ok(MODEL_ENTRIES))
      return
    }
    if (/^\/admin\/ai\/model-entries\/\d+$/.test(path) && (method === 'PUT' || method === 'PATCH')) {
      const id = Number(path.split('/').pop())
      const existing = MODEL_ENTRIES.find((entry) => entry.id === id) ?? MODEL_ENTRY_KEY
      await json(route, ok({ ...existing, version: existing.version + 1 }))
      return
    }
    if (path === '/admin/ai/model-entries' || /\/model-entries(\/|$)/.test(path)) {
      await json(route, ok(MODEL_ENTRIES))
      return
    }
    if (path === '/admin/ai/proxy-services' && method === 'GET') {
      await json(route, ok(PROXY_STATUS))
      return
    }
    if (path === '/admin/ai/proxy-services/usage' && method === 'GET') {
      await json(route, ok(PROXY_USAGE))
      return
    }
    if (path === '/admin/ai/proxy-services/api-keys' && method === 'GET') {
      await json(route, ok({ apiKeys: ['mock-client-key'] }))
      return
    }
    if (/\/proxy-services\/api-keys/.test(path)) {
      await json(route, ok(method === 'POST' ? { apiKey: 'mock-client-key-created' } : null))
      return
    }
    if (/\/proxy-services\/login/.test(path)) {
      await json(route, ok({ provider: 'codex', url: 'https://example.com/oauth', state: 'mock' }))
      return
    }
    if (/\/proxy-services/.test(path)) {
      await json(route, ok(PROXY_STATUS))
      return
    }
    if (path === '/admin/ai/chain-presets' || /\/chain-presets(\/|$)/.test(path)) {
      await json(route, ok([]))
      return
    }
    if (path === '/admin/ai/knowledge-bases' && method === 'GET') {
      await json(route, ok(knowledgeBases))
      return
    }
    if (/\/knowledge-bases\/\d+\/links\/\d+$/.test(path) && method === 'POST') {
      const parts = path.split('/')
      const baseId = Number(parts[4])
      const targetAgentId = Number(parts[6])
      if (baseId === 9) {
        await json(route, { code: 50001, message: 'mock link failed', data: null }, 500)
        return
      }
      const base = knowledgeBases.find((item) => item.id === baseId)
      if (base && !base.linkedAgentIds.includes(targetAgentId)) {
        base.linkedAgentIds.push(targetAgentId)
      }
      await json(route, ok(null))
      return
    }
    if (/\/knowledge-bases\/\d+\/links\/\d+$/.test(path) && method === 'DELETE') {
      const parts = path.split('/')
      const baseId = Number(parts[4])
      const targetAgentId = Number(parts[6])
      const base = knowledgeBases.find((item) => item.id === baseId)
      if (base) {
        base.linkedAgentIds = base.linkedAgentIds.filter((id) => id !== targetAgentId)
      }
      await json(route, ok(null))
      return
    }
    if (/\/knowledge-bases/.test(path)) {
      await json(route, ok(method === 'GET' ? knowledgeBases : null))
      return
    }
    if (path === '/admin/ai/prompt-templates' || /\/prompt-templates(\/|$)/.test(path)) {
      await json(
        route,
        ok([
          {
            id: 11,
            name: 'Support template',
            systemPrompt: 'Copied from template.',
            temperature: 0.4,
            version: 1,
          },
        ]),
      )
      return
    }
    if (/\/ai\/prompt$/.test(path) && method === 'PUT') {
      const body = request.postDataJSON() as {
        systemPrompt?: string | null
        temperature?: number | null
        defaultSkillIds?: string[]
      }
      promptState = {
        ...promptState,
        systemPrompt: body.systemPrompt ?? promptState.systemPrompt,
        temperature: body.temperature ?? promptState.temperature,
        defaultSkillIds: body.defaultSkillIds ?? promptState.defaultSkillIds,
      }
      await json(route, ok(promptState))
      return
    }
    if (/\/ai\/prompt\/load-template$/.test(path) && method === 'POST') {
      promptState = {
        ...promptState,
        systemPrompt: 'Copied from template.',
        temperature: 0.4,
        promptTemplateId: 11,
        promptTemplateName: 'Support template',
      }
      await json(route, ok(promptState))
      return
    }
    if (/\/ai\/prompt(\/|$)/.test(path)) {
      await json(route, ok(promptState))
      return
    }
    if (/\/knowledge/.test(path) || /\/documents/.test(path)) {
      await json(route, ok({ records: [], total: 0, pageNum: 1, pageSize: 20 }))
      return
    }
    if (path === '/admin/ai/skills') {
      await json(route, ok(SKILL_CATALOG))
      return
    }
    if (/\/preview\/skills/.test(path) || /\/skills/.test(path)) {
      await json(route, ok(SKILL_CATALOG.skills.map(({ id, description, version }) => ({ id, description, version }))))
      return
    }
    if (/\/mcp-selection$/.test(path) && method === 'PUT') {
      const body = request.postDataJSON() as { mode?: string; serverNames?: string[] }
      mcpSelection = {
        mode: body.mode ?? mcpSelection.mode,
        serverNames: body.serverNames ?? mcpSelection.serverNames,
      }
      await json(route, ok(mcpSelection))
      return
    }
    if (/\/mcp-selection/.test(path)) {
      await json(route, ok(mcpSelection))
      return
    }
    if (path === '/admin/ai/mcp-config' || /\/mcp-config/.test(path)) {
      await json(route, ok(MCP_CONFIG))
      return
    }
    if (/\/mcp-ready/.test(path) || /\/mcp/.test(path)) {
      await json(route, ok({ reachable: true, errorCode: null, probe: null }))
      return
    }
    // DELETE 等可能要求 data 为 null；unwrapVoid 允许。
    if (method === 'DELETE') {
      await json(route, ok(null))
      return
    }
    await json(route, ok({}))
  })
}

export async function loginWithMocks(page: Page, options: MockAuthOptions = {}) {
  await page.addInitScript(() => {
    window.localStorage.setItem('admin-web.locale', 'en-US')
    window.localStorage.setItem('admin_access_token', 'mock-access-token')
  })
  await installAgentConsoleMocks(page, options)
  await page.goto('/dashboard')
  await page.waitForLoadState('domcontentloaded')
}
