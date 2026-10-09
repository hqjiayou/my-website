export default {
  kicker: 'Admin Console',
  title: '管理者ログイン',
  description: '管理アカウントでログインしてください。',
  fields: {
    username: 'ユーザー名',
    password: 'パスワード',
    rememberUsername: 'ユーザー名を記憶',
  },
  actions: {
    submit: 'ログイン',
    submitting: 'ログイン中…',
    showPassword: 'パスワードを表示',
    hidePassword: 'パスワードを非表示',
    clearUsername: 'ユーザー名をクリア',
    clearPassword: 'パスワードをクリア',
  },
  errors: {
    usernameRequired: 'ユーザー名を入力してください',
    passwordRequired: 'パスワードを入力してください',
    generic: 'ログインに失敗しました。もう一度お試しください。',
    profileLoadFailed: 'ログインは成功しましたが、アカウント情報の取得に失敗しました。admin サービスが起動しているか確認してください。',
  },
} as const
