export default {
  contents: {
    title: 'Page Contents',
    description: 'View page blocks, copy, and publish state across sites.',
    aggregateNotice: 'Data is aggregated from per-site REAL APIs. Edit in the site workspace.',
    empty: 'No cross-site content records',
    columns: {
      site: 'Site',
      title: 'Title',
      type: 'Type',
      status: 'Status',
      updatedAt: 'Updated',
      actions: 'Actions',
    },
    filters: {
      type: 'Content type',
      status: 'Status',
      allTypes: 'All types',
      allStatuses: 'All statuses',
      search: 'Filter',
      reset: 'Reset',
    },
    actions: {
      manage: 'Open agent',
    },
  },
  files: {
    title: 'File Assets',
    description: 'View media assets and upload state across sites.',
    aggregateNotice: 'Data is aggregated from per-site REAL APIs. Upload and manage in the site workspace.',
    empty: 'No cross-site media files',
    columns: {
      site: 'Site',
      filename: 'Filename',
      contentType: 'Type',
      fileSize: 'Size',
      status: 'Status',
      createdAt: 'Uploaded',
      actions: 'Actions',
    },
    actions: {
      manage: 'Open agent',
    },
  },
} as const
