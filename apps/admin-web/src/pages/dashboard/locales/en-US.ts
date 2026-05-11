export default {
  title: 'Overview',
  description: 'Track key metrics across sites and jump into modules quickly.',
  stats: {
    activeSites: { label: 'Active sites', value: '24', trend: '+12% vs last week' },
    todayReservations: { label: 'Reservations today', value: '67', trend: '+8% vs yesterday' },
    aiSessions: { label: 'AI sessions', value: '193', trend: '+21% vs yesterday' },
    uptime: { label: 'Service uptime', value: '99.95%', trend: '-0.02% vs yesterday' },
  },
  trend: {
    title: 'Trends',
    description: 'Wire up Recharts or Nivo for traffic, bookings, and conversion.',
  },
  reminders: {
    title: 'Reminders',
    items: [
      '3 sites pending publish review',
      '2 AI knowledge files awaiting embedding',
      '1 template has mobile layout drift',
    ],
  },
} as const
