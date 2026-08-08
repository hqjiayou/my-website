export default {
  title: 'Overview',
  description: 'Entry point for multi-site ops metrics. Aggregation APIs are not wired yet; nothing below is live data.',
  notConnected: {
    title: 'Ops metrics not connected',
    description:
      'KPIs such as active sites, reservations, AI sessions, and uptime need a dedicated aggregation API. Placeholder numbers are not shown to avoid false completion.',
  },
  trend: {
    title: 'Trends',
    description: 'Charts will appear after real time-series data is connected.',
  },
  reminders: {
    title: 'Reminders',
    empty: 'No real reminder data source yet.',
  },
} as const
