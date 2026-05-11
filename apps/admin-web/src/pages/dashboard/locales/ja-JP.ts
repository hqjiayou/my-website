export default {
  title: 'データ概要',
  description: '主要指標を確認し、各モジュールへすばやく移動できます。',
  stats: {
    activeSites: { label: '稼働サイト', value: '24', trend: '先週比 +12%' },
    todayReservations: { label: '本日の予約', value: '67', trend: '前日比 +8%' },
    aiSessions: { label: 'AI セッション', value: '193', trend: '前日比 +21%' },
    uptime: { label: '稼働率', value: '99.95%', trend: '前日比 -0.02%' },
  },
  trend: {
    title: '運用トレンド',
    description: 'Recharts / Nivo でアクセス・予約・コンバージョンを可視化します。',
  },
  reminders: {
    title: 'お知らせ',
    items: [
      '公開レビュー待ちのサイトが 3 件',
      'ベクトル化待ちの AI ナレッジが 2 件',
      'モバイル表示のずれがあるテンプレートが 1 件',
    ],
  },
} as const
