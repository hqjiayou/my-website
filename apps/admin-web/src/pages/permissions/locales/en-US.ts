export default {
  title: 'Permissions',
  description: 'View system permission points. Codes are tied to API checks and maintained by backend seed data.',
  hint: 'Permission points cannot be created or removed here. Assign them to a role instead.',
  assignLink: 'Assign on Roles',
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
