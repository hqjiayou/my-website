export default {
  appName: 'My Website',
  table: {
    loading: '加载中…',
    empty: '暂无数据',
    error: '加载失败',
    retry: '重试',
    refresh: '刷新',
    reset: '重置',
    searchPlaceholder: '搜索',
    prev: '上一页',
    next: '下一页',
    paginationTotal: '共 {{total}} 条',
  },
  actions: {
    copy: '复制',
    copied: '已复制',
  },
  error: {
    network: '网络错误，请稍后重试',
    unauthorized: '登录已过期，请重新登录',
    forbidden: '无权限访问',
    server: '服务器错误，请稍后重试',
    unknown: '请求失败',
  },
  forbidden: {
    title: '无权限访问',
    description: '您没有访问此页面的权限。如需开通，请联系系统管理员。',
    backHome: '返回首页',
  },
  auth: {
    restoringSession: '正在恢复登录状态…',
  },
  enum: {
    status: {
      ACTIVE: '启用',
      DISABLED: '禁用',
    },
    resourceType: {
      MENU: '菜单',
      ACTION: '操作',
      API: '接口',
      OTHER: '其他',
    },
  },
} as const
