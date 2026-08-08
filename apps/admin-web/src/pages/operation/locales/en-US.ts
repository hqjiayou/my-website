export default {
  contents: {
    title: 'Page Contents',
    description: 'Manage page blocks, copy, and publish state per site.',
    notConnected: {
      title: 'Operation content center not connected',
      description:
        'There is no real API for a cross-site content list yet. Manage content in the site workspace Contents page (/apps/sites/:siteId/contents).',
    },
  },
  reservations: {
    title: 'Reservations',
    description: 'Track reservations and handling status across multiple sites.',
    notConnected: {
      title: 'Operation reservation center not connected',
      description:
        'There is no real API for a cross-site reservation list yet. Manage reservations in the site workspace Reservations page (/apps/sites/:siteId/reservations).',
    },
  },
  files: {
    title: 'File Assets',
    description: 'Manage media assets, upload state, and usage centrally.',
    notConnected: {
      title: 'Operation file center not connected',
      description:
        'There is no real API for a global file list yet. Manage site media in the site workspace Files page (/apps/sites/:siteId/files).',
    },
  },
} as const
