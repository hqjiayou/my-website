import Fastify from 'fastify'
import { registerChatRoutes } from './routes/chat.routes.js'
import { registerHealthRoutes } from './routes/health.routes.js'
import { registerReservationRoutes } from './routes/reservation.routes.js'
import { registerSiteRoutes } from './routes/site.routes.js'

async function buildServer() {
  const app = Fastify({
    logger: true,
  })

  await registerHealthRoutes(app)
  await registerSiteRoutes(app)
  await registerReservationRoutes(app)
  await registerChatRoutes(app)

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

start()
