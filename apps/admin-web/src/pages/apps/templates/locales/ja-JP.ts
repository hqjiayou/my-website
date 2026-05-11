export default {
  list: {
    title: 'テンプレート管理',
    description:
      '複数業種サイト向けテンプレートのメタデータ、対応ページ、ブロック機能、デフォルトテーマと有効状態を管理します。',
    stats: {
      totalTemplates: 'テンプレート総数',
      enabled: '有効',
      draft: '下書き',
      disabled: '無効',
    },
    actions: {
      viewDetail: '詳細を見る',
    },
    empty: 'テンプレートデータがありません。',
  },
  detail: {
    title: 'テンプレート詳細',
    description: 'テンプレートのメタデータ、機能範囲、対象サイト情報を確認します。',
    loading: 'テンプレートデータを読み込み中...',
    backToList: 'テンプレート一覧へ戻る',
    notFound: {
      title: 'テンプレートが見つかりません',
      description: '現在の templateId に一致するテンプレートが存在しません。一覧から再選択してください。',
    },
    previewTitle: 'テンプレートプレビュー',
    previewPlaceholder: 'テンプレートプレビューのプレースホルダー',
    sections: {
      basicInfo: '基本情報',
    },
    placeholderNotice:
      '現在はテンプレート管理のプレースホルダー画面です。今後 site-service と連携してテンプレートメタデータと公開状態を管理します。',
  },
  common: {
    templateName: 'テンプレート名',
    templateCode: 'テンプレートコード',
    industry: '業種',
    status: 'ステータス',
    defaultPrimaryColor: 'デフォルトテーマカラー',
    supportedPages: '対応ページ',
    supportedBlocks: '対応ブロック',
    targetSiteTypes: '対象サイト種別',
    description: '説明',
    updatedAt: '更新日時',
  },
  status: {
    enabled: '有効',
    draft: '下書き',
    disabled: '無効',
  },
} as const
