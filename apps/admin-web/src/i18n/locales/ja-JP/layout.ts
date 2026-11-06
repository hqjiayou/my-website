export default {
  sidebar: {
    kicker: 'My Website',
    title: '管理コンソール',
    emptyMenus: '表示できるメニューがありません',
  },
  header: {
    subtitle: '管理コンソール',
    logout: 'ログアウト',
    anonymous: '管理者',
  },
  search: {
    placeholder: 'モジュールを検索…',
  },
  language: {
    label: '言語',
    'zh-CN': '中文',
    'en-US': 'English',
    'ja-JP': '日本語',
  },
  fallbackPageTitle: 'コンソール',
  emptyModule: {
    initLead: 'モジュールを完成させるための推奨ステップ：',
  },
  userMenu: {
    appearance: '表示設定',
    logout: 'ログアウト',
    logoutConfirm: 'ログアウトしますか？',
  },
  dialog: {
    cancel: 'キャンセル',
  },
  preferences: {
    title: '表示と環境設定',
    description: 'テーマ、文字サイズ、密度などのローカル UI 設定を変更します。',
    close: '設定パネルを閉じる',
    themeMode: {
      label: 'テーマ',
      light: 'ライト',
      dark: 'ダーク',
      system: 'システムに従う',
    },
    fontSize: {
      label: '文字サイズ',
      small: '小',
      medium: '中',
      large: '大',
    },
    pageDensity: {
      label: 'ページ密度',
      comfortable: 'ゆったり',
      default: '標準',
      compact: 'コンパクト',
    },
    primaryColor: {
      label: 'アクセントカラー',
      blue: 'ブルー',
      purple: 'パープル',
      green: 'グリーン',
      orange: 'オレンジ',
    },
    sidebar: {
      label: 'サイドバー',
      collapsed: 'サイドバーを折りたたむ',
    },
    clearLocal: 'ローカル設定をクリア',
    clearHint: 'UI 設定のみ削除します。ログイン状態やトークンは保持されます。',
    clearConfirm: 'すべてのローカル UI 設定を既定値に戻しますか？',
  },
} as const
