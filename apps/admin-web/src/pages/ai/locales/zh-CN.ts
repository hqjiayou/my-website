export default {
  mockNotice: '当前为 ACTIVE MOCK 数据（无真实 AI 后端）。关闭 mock 后请求会失败，不会伪装成成功。',
  loading: '加载中…',
  empty: '暂无数据',
  error: '加载失败',
  retry: '重试',
  settings: {
    title: 'AI 配置',
    description: '管理各站点 AI 模型、参数与启用状态。',
  },
  prompts: {
    title: 'Prompt 测试',
    description: '维护 Prompt 模板并验证输出效果。',
  },
  knowledgeBases: {
    title: '知识库',
    description: '管理知识库条目、索引进度与质量。',
  },
  conversations: {
    title: '对话记录',
    description: '查看站点 AI 会话记录与处理状态。',
  },
} as const
