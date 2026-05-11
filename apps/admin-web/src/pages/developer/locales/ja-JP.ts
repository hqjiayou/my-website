export default {
  title: '開発者オプション',
  description:
    '将来的にコードコンソール、モジュール生成、テンプレートデバッグ、API デバッグなどの開発支援機能を提供するための領域です。',
  cards: {
    codeConsole: {
      title: 'コードコンソール',
      description:
        '自然言語からページ、コンポーネント、ルート、i18n 文言、API mock などのコード変更提案を生成します。',
    },
    moduleScaffold: {
      title: 'モジュールジェネレーター',
      description:
        'ページ、ルート、ナビゲーション、多言語文言、プレースホルダー API を含む admin-web モジュール雛形を素早く作成します。',
    },
    templateDebug: {
      title: 'テンプレートデバッグ',
      description: 'car-web、cake-web など業種テンプレートの設定、ブロック、テーマ、プレビューデータを検証します。',
    },
    apiPlayground: {
      title: 'API デバッグ',
      description: 'site-service と agent-service のリクエスト、レスポンス構造、mock データを検証します。',
    },
  },
  safety: {
    title: 'セキュリティ注意',
    description:
      'このモジュールは開発入口としての予約領域であり、本番コードを直接変更しません。将来的なコード生成では、変更内容のプレビュー、diff 確認、手動レビュー後に書き込むフローを採用します。',
    accessPolicy:
      '権限予約: 将来はスーパー管理者と開発者のみ利用可能とし、一般運用ロールからは非表示にする想定です。',
  },
} as const
