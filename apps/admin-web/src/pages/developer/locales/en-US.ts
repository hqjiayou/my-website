export default {
  title: 'Developer Options',
  description:
    'This module is planned as an entry for future developer-assist capabilities such as code console, module scaffolding, template debugging, and API debugging.',
  cards: {
    codeConsole: {
      title: 'Code Console',
      description:
        'Generate code change proposals for pages, components, routes, i18n copy, and API mocks from natural language instructions.',
    },
    moduleScaffold: {
      title: 'Module Scaffolder',
      description:
        'Quickly scaffold an admin-web feature module including page, route, navigation, i18n copy, and placeholder API contracts.',
    },
    templateDebug: {
      title: 'Template Debug',
      description:
        'Debug configuration, blocks, themes, and preview data for industry templates such as car-web and cake-web.',
    },
    apiPlayground: {
      title: 'API Playground',
      description: 'Debug request flows, response shapes, and mock data for site-service and agent-service.',
    },
  },
  safety: {
    title: 'Safety Notice',
    description:
      'This module is reserved as a developer entry point and will not directly modify production code. Future code generation should follow a flow of change preview, diff confirmation, and manual review before writing files.',
    accessPolicy:
      'Access policy (reserved): this area should be available to super admins and developers only, while regular operation roles stay hidden by default.',
  },
} as const
