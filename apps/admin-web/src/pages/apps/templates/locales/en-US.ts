export default {
  list: {
    title: 'Template Management',
    description:
      'Manage multi-industry site templates including metadata, supported pages, block capabilities, default theme, and activation status.',
    stats: {
      totalTemplates: 'Total Templates',
      enabled: 'Enabled',
      draft: 'Draft',
      disabled: 'Disabled',
    },
    actions: {
      viewDetail: 'View Detail',
    },
    empty: 'No template data yet.',
  },
  detail: {
    title: 'Template Detail',
    description: 'Inspect template metadata, capability scope, and target site information.',
    loading: 'Loading template data...',
    backToList: 'Back to Template List',
    notFound: {
      title: 'Template Not Found',
      description: 'No template matches the current templateId. Please return to the template list and choose again.',
    },
    previewTitle: 'Template Preview',
    previewPlaceholder: 'Template preview placeholder',
    sections: {
      basicInfo: 'Basic Information',
    },
    placeholderNotice:
      'This is a placeholder page for template management. It will later connect to site-service to manage template metadata and publishing status.',
  },
  common: {
    templateName: 'Template Name',
    templateCode: 'Template Code',
    industry: 'Industry',
    status: 'Status',
    defaultPrimaryColor: 'Default Theme Color',
    supportedPages: 'Supported Pages',
    supportedBlocks: 'Supported Blocks',
    targetSiteTypes: 'Target Site Types',
    description: 'Description',
    updatedAt: 'Updated At',
  },
  status: {
    enabled: 'Enabled',
    draft: 'Draft',
    disabled: 'Disabled',
  },
} as const
