export default {
  title: '权限管理',
  description: '查看系统权限点。权限码和接口检查绑在一起，由后端种子维护。',
  hint: '本页不能新增或移除权限点。要把权限交给某个角色，请到角色管理里分配。',
  assignLink: '去角色管理分配',
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
