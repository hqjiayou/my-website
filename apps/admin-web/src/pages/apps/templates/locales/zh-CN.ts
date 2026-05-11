export default {
  list: {
    title: '模板管理',
    description: '管理多行业站点模板，包括模板元数据、支持页面、区块能力、默认主题与启用状态。',
    stats: {
      totalTemplates: '模板总数',
      enabled: '已启用',
      draft: '草稿',
      disabled: '已停用',
    },
    actions: {
      viewDetail: '查看详情',
    },
    empty: '暂无模板数据',
  },
  detail: {
    title: '模板详情',
    description: '查看模板元数据、能力范围和适用站点信息。',
    loading: '模板数据加载中...',
    backToList: '返回模板列表',
    notFound: {
      title: '未找到模板',
      description: '当前 templateId 对应的模板不存在，请返回模板列表重新选择。',
    },
    previewTitle: '模板预览',
    previewPlaceholder: '模板预览占位',
    sections: {
      basicInfo: '基础信息',
    },
    placeholderNotice: '当前为模板管理占位页面，后续将接入 site-service 管理模板元数据与模板发布状态。',
  },
  common: {
    templateName: '模板名称',
    templateCode: '模板编码',
    industry: '行业',
    status: '状态',
    defaultPrimaryColor: '默认主题色',
    supportedPages: '支持页面',
    supportedBlocks: '支持区块',
    targetSiteTypes: '适用站点类型',
    description: '描述',
    updatedAt: '更新时间',
  },
  status: {
    enabled: '已启用',
    draft: '草稿',
    disabled: '已停用',
  },
} as const
