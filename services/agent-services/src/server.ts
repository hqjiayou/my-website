import Fastify from 'fastify'
import { resolveInternalServiceToken } from './auth/internal-token.js'
import { createAiProvider } from './providers/create-provider.js'
import { registerDevOnlyRoutes } from './routes/dev-only.routes.js'
import { registerHealthRoutes } from './routes/health.routes.js'
import { registerInternalRoutes } from './routes/internal.routes.js'

export async function buildServer(env: NodeJS.ProcessEnv = process.env) {
  const app = Fastify({
    logger: true,
  })

  const provider = createAiProvider(env)
  const internalToken = resolveInternalServiceToken(env)

  await registerHealthRoutes(app)

  // 封装内部路由，避免 internal-token hook 污染公开 /health。
  await app.register(async (internal) => {
    await registerInternalRoutes(internal, provider, { internalToken })
  })

  // reservation / site-config 默认不暴露；仅显式 DEV 开关。
  if (env.ENABLE_DEV_MOCK_ROUTES === 'true') {
    await app.register(async (dev) => {
      await registerDevOnlyRoutes(dev)
    })
    app.log.warn(
      'ENABLE_DEV_MOCK_ROUTES=true: reservation/site-config mock routes are mounted (non-production).',
    )
  }

  return app
}

async function start() {
  const app = await buildServer()
  const port = Number(process.env.PORT ?? 3001)

  try {
    await app.listen({ port, host: '0.0.0.0' })
    app.log.info(`agent-service is listening on port ${port}`)
  } catch (error) {
    app.log.error(error)
    process.exit(1)
  }
}

const entry = process.argv[1]?.replace(/\\/g, '/') ?? ''
if (entry.endsWith('/server.ts') || entry.endsWith('/server.js')) {
  start()
}
