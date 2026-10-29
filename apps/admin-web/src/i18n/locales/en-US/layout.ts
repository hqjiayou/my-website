export default {
  sidebar: {
    kicker: 'My Website',
    title: 'Admin Console',
    emptyMenus: 'No menus available',
  },
  header: {
    subtitle: 'Admin',
    logout: 'Sign out',
    anonymous: 'Admin',
  },
  search: {
    placeholder: 'Search modules…',
  },
  language: {
    label: 'Language',
    'zh-CN': '中文',
    'en-US': 'English',
    'ja-JP': '日本語',
  },
  fallbackPageTitle: 'Console',
  emptyModule: {
    initLead: 'Suggested next steps to ship this module:',
  },
  userMenu: {
    appearance: 'Appearance',
    logout: 'Sign out',
    logoutConfirm: 'Are you sure you want to sign out?',
  },
  preferences: {
    title: 'Appearance & preferences',
    description: 'Customize theme, font size, density, and other local UI settings.',
    close: 'Close settings panel',
    themeMode: {
      label: 'Theme',
      light: 'Light',
      dark: 'Dark',
      system: 'System',
    },
    fontSize: {
      label: 'Font size',
      small: 'Small',
      medium: 'Medium',
      large: 'Large',
    },
    pageDensity: {
      label: 'Density',
      comfortable: 'Comfortable',
      default: 'Default',
      compact: 'Compact',
    },
    primaryColor: {
      label: 'Accent color',
      blue: 'Blue',
      purple: 'Purple',
      green: 'Green',
      orange: 'Orange',
    },
    sidebar: {
      label: 'Sidebar',
      collapsed: 'Collapse sidebar',
    },
    clearLocal: 'Reset local settings',
    clearHint: 'Clears UI preferences only. Sign-in and tokens are not affected.',
    clearConfirm: 'Reset all local UI settings to defaults?',
  },
} as const
