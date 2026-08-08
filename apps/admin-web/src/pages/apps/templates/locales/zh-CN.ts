export default {
  list: {
    title: '模板管理',
    description: '查看平台预置站点模板目录，包括站点类型、默认主题色与启用状态。',
    loading: '模板列表加载中...',
    error: '模板列表加载失败',
    retry: '重试',
    empty: '暂无模板数据',
    filters: {
      allStatus: '全部状态',
      allSiteType: '全部类型',
      search: '查询',
      reset: '重置',
    },
    actions: {
      viewDetail: '查看详情',
    },
    messages: {
      statusUpdated: '模板状态已更新',
    },
    pagination: {
      summary: '共 {{total}} 条，第 {{page}} 页（每页 {{pageSize}} 条）',
      prev: '上一页',
      next: '下一页',
    },
  },
  detail: {
    title: '模板详情',
    description: '查看模板元数据与状态信息。',
    loading: '模板数据加载中...',
    retry: '重试',
    backToList: '返回模板列表',
    notFound: {
      title: '未找到模板',
      description: '当前模板不存在或无权访问，请返回模板列表重新选择。',
    },
    previewTitle: '模板预览',
    previewPlaceholder: '暂无预览图',
    sections: {
      basicInfo: '基础信息',
    },
    messages: {
      statusUpdated: '模板状态已更新',
    },
  },
  common: {
    templateName: '模板名称',
    templateCode: '模板编码',
    siteType: '站点类型',
    status: '状态',
    defaultPrimaryColor: '默认主题色',
    sortOrder: '排序',
    version: '版本',
    description: '描述',
    createdAt: '创建时间',
    updatedAt: '更新时间',
  },
  status: {
    ENABLED: '已启用',
    DRAFT: '草稿',
    DISABLED: '已停用',
  },
} as const
