export default {
  contents: {
    title: '页面内容',
    description: '按站点管理页面区块、文案内容与发布状态。',
  },
  reservations: {
    title: '预约管理',
    description: '查看并跟进多站点预约记录与处理状态。',
    columns: {
      customer: '客户',
      site: '站点',
      service: '服务类型',
      reservedAt: '预约时间',
      status: '状态',
    },
    status: {
      new: '新建',
      confirmed: '已确认',
      done: '已完成',
      cancelled: '已取消',
    },
  },
  files: {
    title: '文件管理',
    description: '统一管理站点素材、上传状态与使用情况。',
  },
} as const
