export default {
  list: {
    title: 'テンプレート管理',
    description: 'プラットフォーム標準のサイトテンプレート一覧を確認します。サイト種別・デフォルトテーマ色・状態を含みます。',
    loading: 'テンプレート一覧を読み込み中...',
    error: 'テンプレート一覧の読み込みに失敗しました',
    retry: '再試行',
    empty: 'テンプレートデータがありません。',
    filters: {
      allStatus: 'すべてのステータス',
      allSiteType: 'すべての種別',
      search: '検索',
      reset: 'リセット',
    },
    actions: {
      viewDetail: '詳細を見る',
    },
    messages: {
      statusUpdated: 'テンプレートのステータスを更新しました',
    },
    pagination: {
      summary: '全 {{total}} 件、{{page}} ページ目（1 ページ {{pageSize}} 件）',
      prev: '前へ',
      next: '次へ',
    },
  },
  detail: {
    title: 'テンプレート詳細',
    description: 'テンプレートのメタデータとステータスを確認します。',
    loading: 'テンプレートデータを読み込み中...',
    retry: '再試行',
    backToList: 'テンプレート一覧へ戻る',
    notFound: {
      title: 'テンプレートが見つかりません',
      description: 'テンプレートが存在しないか、アクセス権限がありません。一覧から再選択してください。',
    },
    previewTitle: 'テンプレートプレビュー',
    previewPlaceholder: 'プレビュー画像がありません',
    sections: {
      basicInfo: '基本情報',
    },
    messages: {
      statusUpdated: 'テンプレートのステータスを更新しました',
    },
  },
  common: {
    templateName: 'テンプレート名',
    templateCode: 'テンプレートコード',
    siteType: 'サイト種別',
    status: 'ステータス',
    defaultPrimaryColor: 'デフォルトテーマカラー',
    sortOrder: '並び順',
    version: 'バージョン',
    description: '説明',
    createdAt: '作成日時',
    updatedAt: '更新日時',
  },
  status: {
    ENABLED: '有効',
    DRAFT: '下書き',
    DISABLED: '無効',
  },
} as const
