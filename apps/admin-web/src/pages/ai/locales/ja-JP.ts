export default {
  loading: '読み込み中…',
  empty: 'データがありません',
  error: '読み込みに失敗しました',
  retry: '再試行',
  noSites: 'サイトがありません。先にサイトを作成してください。',
  siteLabel: 'サイト',
  settings: {
    title: 'AI ランタイム設定',
    description: 'デプロイ環境でのモデル選択の説明です。編集可能な Java AI Config テーブルはありません。',
    body: 'モデルは agent-services の環境変数 AI_MODEL で決まります（Pydantic AI spec、デフォルトなし）。',
    envHint:
      'サイト単位の Prompt / 温度は「Prompt 設定」で管理します。Java が Python 呼び出し時に注入し、フロントから systemPrompt を直接送れません。',
    blockedHint:
      'AI_MODEL 未設定時、プレビューとサイトチャットは成功を偽装せず、誠実に失敗します（503 / BLOCKED）。',
  },
  prompts: {
    title: 'Prompt 設定',
    description: 'サイトごとのシステム Prompt と温度を管理します。保存後、Java が Python runtime に注入します。',
  },
  knowledgeBases: {
    title: 'ナレッジベース',
    description: 'サイトごとのナレッジ文書を管理します（Java が SoT、インデックスは Python 派生ストア）。',
  },
  conversations: {
    title: '会話記録',
    description: 'サイトユーザーと AI の会話・メッセージを読み取り専用で確認します（会話の真実は Java）。',
  },
} as const
