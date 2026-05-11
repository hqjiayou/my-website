import { FastifyInstance } from 'fastify'

interface SiteConfigParams {
  domain: string
}

export async function registerSiteRoutes(app: FastifyInstance) {
  app.get<{ Params: SiteConfigParams }>('/api/site/:domain/config', async (request) => {
    const { domain } = request.params

    return {
      success: true,
      data: {
        siteId: 'site-jp-garage-001',
        domain,
        siteName: 'Sakura Auto Garage',
        locale: 'ja-JP',
        templateKey: 'garage',
        sections: ['hero', 'services', 'cases', 'reservation', 'ai-entry'],
      },
    }
  })
}
