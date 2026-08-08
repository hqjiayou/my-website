export default {
  title: 'Permissions',
  description: 'View system permission points (read-only). Permissions are maintained by backend seed data.',
  hint: 'Permissions are seeded on the backend. This page is read-only.',
  tabs: {
    menu: 'Menu',
    action: 'Action',
    other: 'Other',
  },
  searchPlaceholder: 'Search code / name / resource key',
  columns: {
    permissionCode: 'Permission code',
    permissionName: 'Permission name',
    resourceType: 'Resource type',
    resourceKey: 'Resource key',
    status: 'Status',
  },
  empty: 'No permissions match your filters',
} as const
