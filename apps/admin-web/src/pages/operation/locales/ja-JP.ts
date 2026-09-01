export default {
  contents: {
    title: 'ページコンテンツ',
    description: 'サイト横断でページブロック、文言、公開状態を確認します。',
    aggregateNotice: '各サイト REAL API から集約しています。編集はサイトワークスペースで行ってください。',
    empty: '横断コンテンツはありません',
    columns: {
      site: 'サイト',
      title: 'タイトル',
      type: '種別',
      status: '状態',
      updatedAt: '更新日時',
      actions: '操作',
    },
    filters: {
      type: 'コンテンツ種別',
      status: '状態',
      allTypes: 'すべての種別',
      allStatuses: 'すべての状態',
      search: '絞り込み',
      reset: 'リセット',
    },
    actions: {
      manage: 'サイト管理へ',
    },
  },
  reservations: {
    title: '予約管理',
    description: 'サイト横断で予約記録と対応ステータスを確認します。',
    aggregateNotice: '各サイト REAL API から集約しています。対応はサイトワークスペースで行ってください。',
    empty: '横断予約はありません',
    columns: {
      site: 'サイト',
      customer: '顧客',
      serviceType: 'サービス',
      reservedAt: '予約日時',
      status: '状態',
      actions: '操作',
    },
    filters: {
      status: '状態',
      allStatuses: 'すべての状態',
      search: '絞り込み',
      reset: 'リセット',
    },
    actions: {
      manage: 'サイト管理へ',
    },
  },
  files: {
    title: 'ファイル管理',
    description: 'サイト横断でメディア素材とアップロード状態を確認します。',
    aggregateNotice: '各サイト REAL API から集約しています。アップロードと管理はサイトワークスペースで行ってください。',
    empty: '横断メディアはありません',
    columns: {
      site: 'サイト',
      filename: 'ファイル名',
      contentType: '種別',
      fileSize: 'サイズ',
      status: '状態',
      createdAt: 'アップロード日時',
      actions: '操作',
    },
    actions: {
      manage: 'サイト管理へ',
    },
  },
} as const
