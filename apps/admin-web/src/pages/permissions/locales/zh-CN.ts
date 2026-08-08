export default {
  title: '权限管理',
  description: '查看系统权限点（只读）。权限由后端种子维护，前端不提供增删改。',
  hint: '权限由后端种子数据维护，本页仅支持查看与检索。',
  tabs: {
    menu: '菜单',
    action: '操作',
    other: '其他',
  },
  searchPlaceholder: '搜索权限编码 / 名称 / 资源键',
  columns: {
    permissionCode: '权限编码',
    permissionName: '权限名称',
    resourceType: '资源类型',
    resourceKey: '资源键',
    status: '状态',
  },
  empty: '暂无匹配的权限数据',
} as const
