export default {
  title: '権限管理',
  description: 'システム権限ポイントを表示します。権限コードは API チェックと紐づき、バックエンドのシードで管理されます。',
  hint: 'このページでは権限ポイントを追加・削除できません。ロール管理で割り当ててください。',
  assignLink: 'ロール管理で割り当て',
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
