export default {
  kicker: 'Admin Console',
  title: '管理员登录',
  description: '使用后台账号登录以继续。',
  fields: {
    username: '用户名',
    password: '密码',
    rememberUsername: '记住用户名',
  },
  actions: {
    submit: '登录',
    submitting: '登录中…',
    showPassword: '显示密码',
    hidePassword: '隐藏密码',
    clearUsername: '清空用户名',
    clearPassword: '清空密码',
  },
  errors: {
    generic: '登录失败，请稍后重试',
    profileLoadFailed: '登录成功，但加载账号信息失败，请确认 admin 服务已启动',
  },
} as const
