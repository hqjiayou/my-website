export default {
  contents: {
    title: 'ページコンテンツ',
    description: 'サイト単位でページブロック、文言、公開状態を管理します。',
    notConnected: {
      title: '運用コンテンツセンターは未接続',
      description:
        '横断コンテンツ一覧の実 API は未提供です。サイトワークスペースの「ページコンテンツ」で管理してください（/apps/sites/:siteId/contents）。',
    },
  },
  reservations: {
    title: '予約管理',
    description: '複数サイトの予約記録と対応ステータスを確認します。',
    notConnected: {
      title: '運用予約センターは未接続',
      description:
        '横断予約一覧の実 API は未提供です。サイトワークスペースの「予約相談」で管理してください（/apps/sites/:siteId/reservations）。',
    },
  },
  files: {
    title: 'ファイル管理',
    description: 'サイト素材、アップロード状態、利用状況を一元管理します。',
    notConnected: {
      title: '運用ファイルセンターは未接続',
      description:
        'グローバルファイル一覧の実 API は未提供です。サイトワークスペースの「ファイルリソース」でメディアを管理してください（/apps/sites/:siteId/files）。',
    },
  },
} as const
