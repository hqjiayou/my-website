export default {
  title: '権限管理',
  description: 'システム権限ポイントの参照（読み取り専用）。権限はバックエンドのシードデータで管理されます。',
  hint: '権限はバックエンドのシードで管理されます。本ページは参照・検索のみです。',
  tabs: {
    menu: 'メニュー',
    action: '操作',
    other: 'その他',
  },
  searchPlaceholder: '権限コード / 名称 / リソースキーで検索',
  columns: {
    permissionCode: '権限コード',
    permissionName: '権限名称',
    resourceType: 'リソース種別',
    resourceKey: 'リソースキー',
    status: '状態',
  },
  empty: '条件に一致する権限がありません',
} as const
