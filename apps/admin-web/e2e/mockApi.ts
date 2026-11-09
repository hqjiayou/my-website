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

const MODEL_ENTRY = {
  id: 1,
  displayName: 'Mock Model',
  modelSpec: 'openai:gpt-test',
  mode: 'KEY',
  enabled: true,
  defaultEntry: true,
  derived: false,
  derivedSource: null,
  apiKeyConfigured: true,
  apiKeyHint: '****',
  baseUrl: null,
  alias: null,
  sourceAgentId: null,
  embeddingSpec: null,
  version: 1,
  updatedAt: '2026-11-09T00:00:00Z',
}

const KNOWLEDGE_BASE = {
  id: 7,
  agentId: 42,
  name: 'Demo KB',
  description: null,
  documentCount: 2,
  ownerAgentName: 'Demo Agent',
  linked: true,
  version: 1,
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
          records: [AGENT],
          total: 1,
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
      await json(route, ok(AGENT))
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
      await json(route, ok({ agentId: 42, keyEntryIds: [1], cliEntryIds: [] }))
      return
    }
    if (/\/ai\/chain\//.test(path)) {
      await json(route, ok({ agentId: 42, keyEntryIds: [1], cliEntryIds: [] }))
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
    if (path === '/admin/ai/model-entries' || /\/model-entries(\/|$)/.test(path)) {
      await json(route, ok([MODEL_ENTRY]))
      return
    }
    if (path === '/admin/ai/chain-presets' || /\/chain-presets(\/|$)/.test(path)) {
      await json(route, ok([]))
      return
    }
    if (path === '/admin/ai/knowledge-bases' && method === 'GET') {
      await json(route, ok([KNOWLEDGE_BASE]))
      return
    }
    if (/\/knowledge-bases/.test(path)) {
      await json(route, ok(method === 'GET' ? [] : null))
      return
    }
    if (path === '/admin/ai/prompt-templates' || /\/prompt-templates(\/|$)/.test(path)) {
      await json(route, ok([]))
      return
    }
    if (/\/ai\/prompt(\/|$)/.test(path)) {
      await json(
        route,
        ok({
          agentId: 42,
          systemPrompt: 'You are a helpful assistant.',
          temperature: 0.2,
          promptTemplateId: null,
          promptTemplateName: null,
          defaultSkillIds: [],
        }),
      )
      return
    }
    if (/\/knowledge/.test(path) || /\/documents/.test(path)) {
      await json(route, ok({ records: [], total: 0, pageNum: 1, pageSize: 20 }))
      return
    }
    if (/\/preview\/skills/.test(path) || /\/skills/.test(path)) {
      await json(route, ok([]))
      return
    }
    if (/\/mcp-selection/.test(path)) {
      await json(route, ok({ mode: 'INHERIT', serverNames: [] }))
      return
    }
    if (path === '/admin/ai/mcp-config' || /\/mcp-config/.test(path)) {
      await json(
        route,
        ok({
          servers: [],
          configVersion: 1,
          lastPushStatus: null,
          lastPushMessage: null,
          lastPushedAt: null,
          version: 1,
          updatedAt: null,
        }),
      )
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
