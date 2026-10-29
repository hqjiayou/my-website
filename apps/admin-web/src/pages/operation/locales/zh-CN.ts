export default {
  contents: {
    title: '页面内容',
    description: '跨站点查看页面区块、文案内容与发布状态。',
    aggregateNotice: '数据来自各站点 REAL API 聚合；编辑请进入对应站点工作台。',
    empty: '暂无跨站内容记录',
    columns: {
      site: '站点',
      title: '标题',
      type: '类型',
      status: '状态',
      updatedAt: '更新时间',
      actions: '操作',
    },
    filters: {
      type: '内容类型',
      status: '状态',
      allTypes: '全部类型',
      allStatuses: '全部状态',
      search: '筛选',
      reset: '重置',
    },
    actions: {
      manage: '打开智能体',
    },
  },
  files: {
    title: '文件管理',
    description: '跨站点查看媒体素材与上传状态。',
    aggregateNotice: '数据来自各站点 REAL API 聚合；上传与管理请进入对应站点工作台。',
    empty: '暂无跨站媒体文件',
    columns: {
      site: '站点',
      filename: '文件名',
      contentType: '类型',
      fileSize: '大小',
      status: '状态',
      createdAt: '上传时间',
      actions: '操作',
    },
    actions: {
      manage: '打开智能体',
    },
  },
} as const
