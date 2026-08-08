export default {
  list: {
    title: 'Template Management',
    description:
      'Browse platform-preset site templates, including site type, default theme color, and activation status.',
    loading: 'Loading templates...',
    error: 'Failed to load templates',
    retry: 'Retry',
    empty: 'No template data yet.',
    filters: {
      allStatus: 'All statuses',
      allSiteType: 'All types',
      search: 'Search',
      reset: 'Reset',
    },
    actions: {
      viewDetail: 'View Detail',
    },
    messages: {
      statusUpdated: 'Template status updated',
    },
    pagination: {
      summary: '{{total}} total, page {{page}} ({{pageSize}} per page)',
      prev: 'Previous',
      next: 'Next',
    },
  },
  detail: {
    title: 'Template Detail',
    description: 'Inspect template metadata and status.',
    loading: 'Loading template data...',
    retry: 'Retry',
    backToList: 'Back to Template List',
    notFound: {
      title: 'Template Not Found',
      description: 'This template does not exist or you do not have access. Please return to the list.',
    },
    previewTitle: 'Template Preview',
    previewPlaceholder: 'No preview image',
    sections: {
      basicInfo: 'Basic Information',
    },
    messages: {
      statusUpdated: 'Template status updated',
    },
  },
  common: {
    templateName: 'Template Name',
    templateCode: 'Template Code',
    siteType: 'Site Type',
    status: 'Status',
    defaultPrimaryColor: 'Default Theme Color',
    sortOrder: 'Sort Order',
    version: 'Version',
    description: 'Description',
    createdAt: 'Created At',
    updatedAt: 'Updated At',
  },
  status: {
    ENABLED: 'Enabled',
    DRAFT: 'Draft',
    DISABLED: 'Disabled',
  },
} as const
