export default {
  mockNotice: '現在は ACTIVE MOCK データです（実 AI バックエンドなし）。mock をオフにすると失敗し、成功を偽装しません。',
  loading: '読み込み中…',
  empty: 'データがありません',
  error: '読み込みに失敗しました',
  retry: '再試行',
  settings: {
    title: 'AI 設定',
    description: 'サイトごとの AI モデル、パラメータ、利用状態を管理します。',
  },
  prompts: {
    title: 'Prompt テスト',
    description: 'Prompt テンプレートを管理し、出力結果を確認します。',
  },
  knowledgeBases: {
    title: 'ナレッジベース',
    description: 'ナレッジ項目、インデックス進捗、品質を管理します。',
  },
  conversations: {
    title: '会話記録',
    description: 'サイト別 AI 会話記録と処理状態を確認します。',
  },
} as const
