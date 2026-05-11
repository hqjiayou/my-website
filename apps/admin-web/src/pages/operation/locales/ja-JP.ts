export default {
  contents: {
    title: 'ページコンテンツ',
    description: 'サイト単位でページブロック、文言、公開状態を管理します。',
  },
  reservations: {
    title: '予約管理',
    description: '複数サイトの予約記録と対応ステータスを確認します。',
    columns: {
      customer: '顧客',
      site: 'サイト',
      service: 'サービス種別',
      reservedAt: '予約日時',
      status: '状態',
    },
    status: {
      new: '新規',
      confirmed: '確認済み',
      done: '完了',
      cancelled: 'キャンセル',
    },
  },
  files: {
    title: 'ファイル管理',
    description: 'サイト素材、アップロード状態、利用状況を一元管理します。',
  },
} as const
