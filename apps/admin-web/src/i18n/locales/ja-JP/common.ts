export default {
  appName: 'My Website',
  table: {
    loading: '読み込み中…',
    empty: 'データがありません',
    error: '読み込みに失敗しました',
    retry: '再試行',
    refresh: '更新',
    reset: 'リセット',
    searchPlaceholder: '検索',
    prev: '前へ',
    next: '次へ',
    paginationTotal: '全 {{total}} 件',
  },
  actions: {
    copy: 'コピー',
    copied: 'コピーしました',
  },
  error: {
    network: 'ネットワークエラー。しばらくしてから再試行してください。',
    unauthorized: 'ログインの有効期限が切れました。再度ログインしてください。',
    forbidden: 'アクセス権限がありません',
    server: 'サーバーエラー。しばらくしてから再試行してください。',
    unknown: 'リクエストに失敗しました',
  },
  forbidden: {
    title: 'アクセス権限がありません',
    description: 'このページを表示する権限がありません。必要な場合は管理者にお問い合わせください。',
    backHome: 'ホームに戻る',
  },
  auth: {
    restoringSession: 'ログイン状態を復元しています…',
  },
  enum: {
    status: {
      ACTIVE: '有効',
      DISABLED: '無効',
    },
    resourceType: {
      MENU: 'メニュー',
      ACTION: '操作',
      API: 'API',
      OTHER: 'その他',
    },
  },
} as const
