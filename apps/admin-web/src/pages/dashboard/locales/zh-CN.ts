export default {
  title: '数据概览',
  description: '查看多站点核心运营指标，并快速进入各业务模块。',
  stats: {
    activeSites: { label: '活跃站点', value: '24', trend: '+12% 较上周' },
    todayReservations: { label: '今日预约', value: '67', trend: '+8% 较昨日' },
    aiSessions: { label: 'AI 会话', value: '193', trend: '+21% 较昨日' },
    uptime: { label: '服务可用率', value: '99.95%', trend: '-0.02% 较昨日' },
  },
  trend: {
    title: '运营趋势',
    description: '后续可接入 Recharts 或 Nivo 展示访问、预约与转化走势。',
  },
  reminders: {
    title: '系统提醒',
    items: ['3 个站点待发布审核', '2 个 AI 知识库文件等待向量化', '1 个模板存在移动端布局偏移'],
  },
} as const
