export default {
  title: '开发者选项',
  description: '用于未来提供代码控制台、模块生成器、模板调试、API 调试等开发辅助能力。',
  cards: {
    codeConsole: {
      title: '代码控制台',
      description: '通过自然语言生成页面、组件、路由、i18n 文案、接口 mock 等代码变更建议。',
    },
    moduleScaffold: {
      title: '模块生成器',
      description: '快速生成 admin-web 功能模块骨架，包括页面、路由、导航、多语言文案和占位接口。',
    },
    templateDebug: {
      title: '模板调试',
      description: '调试 car-web、cake-web 等行业模板的配置、区块、主题和预览数据。',
    },
    apiPlayground: {
      title: 'API 调试',
      description: '调试 site-service 与 agent-service 的接口请求、响应结构和 mock 数据。',
    },
  },
  safety: {
    title: '安全提示',
    description:
      '当前模块仅作为开发入口预留，不会直接修改生产代码。未来代码生成应采用变更预览、diff 确认、人工审核后再写入的流程。',
    accessPolicy: '权限预留：未来应仅对超级管理员与开发者开放，普通运营角色默认不可见。',
  },
} as const
