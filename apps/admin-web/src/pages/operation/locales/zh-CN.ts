export default {
  contents: {
    title: '页面内容',
    description: '按站点管理页面区块、文案内容与发布状态。',
    notConnected: {
      title: '运营内容中心未接入',
      description:
        '跨站内容列表尚无真实 API。请在站点工作台「页面内容」中管理（/apps/sites/:siteId/contents）。',
    },
  },
  reservations: {
    title: '预约管理',
    description: '查看并跟进多站点预约记录与处理状态。',
    notConnected: {
      title: '运营预约中心未接入',
      description:
        '跨站预约列表尚无真实 API。请在站点工作台「预约咨询」中管理（/apps/sites/:siteId/reservations）。',
    },
  },
  files: {
    title: '文件管理',
    description: '统一管理站点素材、上传状态与使用情况。',
    notConnected: {
      title: '运营文件中心未接入',
      description:
        '全局文件列表尚无真实 API。请在站点工作台「文件资源」中管理站点媒体（/apps/sites/:siteId/files）。',
    },
  },
} as const
