export default {
  title: '数据概览',
  description: '多站点运营指标入口。聚合接口尚未接入，以下不为真实数据。',
  notConnected: {
    title: '运营指标尚未接入',
    description: '活跃站点、预约、AI 会话与可用率等 KPI 需要独立聚合 API；当前不展示占位数字，避免误判为已完成。',
  },
  trend: {
    title: '运营趋势',
    description: '图表待接入真实时序数据后展示。',
  },
  reminders: {
    title: '系统提醒',
    empty: '暂无真实系统提醒数据源。',
  },
} as const
