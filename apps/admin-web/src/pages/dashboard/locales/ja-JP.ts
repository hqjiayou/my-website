export default {
  title: 'データ概要',
  description: 'マルチサイト運用指標の入口です。集計 API は未接続のため、以下は実データではありません。',
  notConnected: {
    title: '運用指標は未接続',
    description:
      '稼働サイト・予約・AI セッション・稼働率などの KPI には専用の集計 API が必要です。完了と誤認しないよう仮数字は表示しません。',
  },
  trend: {
    title: '運用トレンド',
    description: '実時系列データの接続後にチャートを表示します。',
  },
  reminders: {
    title: 'お知らせ',
    empty: '実データ源のシステム通知はまだありません。',
  },
} as const
