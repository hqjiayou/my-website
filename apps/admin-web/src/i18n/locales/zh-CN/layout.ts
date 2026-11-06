export default {
  sidebar: {
    kicker: 'My Website',
    title: '管理控制台',
    emptyMenus: '暂无可见菜单',
  },
  header: {
    subtitle: '管理后台',
    logout: '退出登录',
    anonymous: '管理员',
  },
  search: {
    placeholder: '搜索模块…',
  },
  language: {
    label: '语言',
    'zh-CN': '中文',
    'en-US': 'English',
    'ja-JP': '日本語',
  },
  fallbackPageTitle: '控制台',
  emptyModule: {
    initLead: '建议先完成以下模块初始化：',
  },
  userMenu: {
    appearance: '外观设置',
    logout: '退出登录',
    logoutConfirm: '确定要退出登录吗？',
  },
  dialog: {
    cancel: '取消',
  },
  preferences: {
    title: '外观与偏好',
    description: '调整主题、字号、密度等本地显示设置。',
    close: '关闭设置面板',
    themeMode: {
      label: '主题模式',
      light: '浅色',
      dark: '深色',
      system: '跟随系统',
    },
    fontSize: {
      label: '字号',
      small: '小',
      medium: '中',
      large: '大',
    },
    pageDensity: {
      label: '页面密度',
      comfortable: '宽松',
      default: '默认',
      compact: '紧凑',
    },
    primaryColor: {
      label: '主色调',
      blue: '蓝色',
      purple: '紫色',
      green: '绿色',
      orange: '橙色',
    },
    sidebar: {
      label: '侧边栏',
      collapsed: '折叠侧边栏',
    },
    clearLocal: '清除本地配置',
    clearHint: '仅清除 UI 偏好，不会退出登录或清除认证信息。',
    clearConfirm: '确定清除所有本地 UI 配置并恢复默认？',
  },
} as const
