export default {
  title: 'メニュー管理',
  description: '管理画面のメニューツリーを表示します（読み取り専用）。書き込みはバックエンド完成後に接続します。',
  hint: '追加・編集・削除・並び替え・権限バインドはまだ利用できません。',
  loading: '読み込み中…',
  empty: 'メニューデータがありません',
  error: 'メニューの読み込みに失敗しました',
  retry: '再試行',
  labels: {
    path: 'path',
    icon: 'icon',
    sort: 'sort',
  },
} as const
