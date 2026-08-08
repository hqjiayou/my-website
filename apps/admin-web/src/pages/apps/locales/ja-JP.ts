export default {
  title: 'スタイル設定',
  description: 'サイト外観（schema v1：テーマカラー、フォント、ボタン、Hero レイアウト）を編集します。',
  currentSite: '現在のサイト',
  saveConfig: '保存',
  saving: '保存中…',
  resetDefault: '読み込み値に戻す',
  previewSite: 'プレビュー',
  editorTitle: '外観フィールド',
  versionLabel: 'バージョン',
  fields: {
    primaryColor: 'メインカラー',
    fontStyle: 'フォントスタイル',
    buttonStyle: 'ボタンスタイル',
    heroLayout: 'Hero レイアウト',
  },
  previewMode: {
    desktop: 'デスクトップ',
    mobile: 'モバイル',
  },
  preview: {
    headerMenu: 'メニュー',
    heroTitle: 'メインビジュアル',
    heroSubtitle: 'schema v1 設定に基づく Hero とボタンのプレビューです。',
    primaryAction: '相談する',
  },
  options: {
    fontStyle: {
      modern: 'モダン',
      classic: 'クラシック',
      soft: 'ソフト',
      japanese: '和風',
    },
    heroLayout: {
      'left-text-right-visual': '左テキスト / 右ビジュアル',
      centered: '中央寄せ',
      'full-bleed': 'フルブリード',
    },
    buttonStyle: {
      rounded: '角丸',
      pill: 'ピル',
      square: '角型',
    },
  },
  messages: {
    loadingSites: 'サイト一覧を読み込み中...',
    loadingConfig: 'スタイル設定を読み込み中...',
    configUnavailable: '選択中サイトのスタイル設定がありません。',
    noSites: 'サイトがありません。先にサイトを作成してください。',
    saved: '設定を保存しました',
    invalidPrimaryColor: 'テーマカラーは #RRGGBB 形式である必要があります',
  },
} as const
