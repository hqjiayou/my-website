export default {
  title: '风格设置',
  description: '编辑当前站点的外观配置（schema v1：主题色、字体、按钮、Hero 布局）。',
  currentSite: '当前站点',
  saveConfig: '保存配置',
  saving: '保存中…',
  resetDefault: '重置为已加载',
  previewSite: '预览',
  editorTitle: '外观字段',
  versionLabel: '版本',
  fields: {
    primaryColor: '主色',
    fontStyle: '字体风格',
    buttonStyle: '按钮风格',
    heroLayout: 'Hero 布局',
  },
  previewMode: {
    desktop: '桌面预览',
    mobile: '移动预览',
  },
  preview: {
    headerMenu: '导航菜单',
    heroTitle: '站点主视觉',
    heroSubtitle: '根据 schema v1 配置预览 Hero 与按钮风格。',
    primaryAction: '立即咨询',
  },
  options: {
    fontStyle: {
      modern: '现代',
      classic: '经典',
      soft: '柔和',
      japanese: '日式',
    },
    heroLayout: {
      'left-text-right-visual': '左文右图',
      centered: '居中',
      'full-bleed': '全宽',
    },
    buttonStyle: {
      rounded: '圆角',
      pill: '胶囊',
      square: '直角',
    },
  },
  messages: {
    loadingSites: '正在加载站点列表...',
    loadingConfig: '正在加载站点风格配置...',
    configUnavailable: '当前站点暂无风格配置。',
    noSites: '暂无站点，请先创建站点。',
    saved: '配置已保存',
    invalidPrimaryColor: '主题色须为 #RRGGBB',
  },
} as const
