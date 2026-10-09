export default {
  appName: 'My Website',
  table: {
    loading: 'Loading…',
    empty: 'No data',
    error: 'Failed to load',
    retry: 'Retry',
    refresh: 'Refresh',
    reset: 'Reset',
    searchPlaceholder: 'Search',
    prev: 'Previous',
    next: 'Next',
    paginationTotal: '{{total}} items',
  },
  actions: {
    copy: 'Copy',
    copied: 'Copied',
  },
  breadcrumb: 'Breadcrumb',
  error: {
    network: 'Network error. Please try again.',
    unauthorized: 'Session expired. Please sign in again.',
    forbidden: 'Access denied',
    server: 'Server error. Please try again.',
    unknown: 'Request failed',
  },
  forbidden: {
    title: 'Access denied',
    description: 'You do not have permission to view this page. Contact an administrator if you need access.',
    backHome: 'Back to home',
  },
  auth: {
    restoringSession: 'Restoring session…',
  },
  enum: {
    status: {
      ACTIVE: 'Active',
      DISABLED: 'Disabled',
    },
    resourceType: {
      MENU: 'Menu',
      ACTION: 'Action',
      API: 'API',
      OTHER: 'Other',
    },
  },
} as const
