import enUSCommon from './locales/en-US/common'
import enUSLayout from './locales/en-US/layout'
import enUSNav from './locales/en-US/nav'

import jaJPCommon from './locales/ja-JP/common'
import jaJPLayout from './locales/ja-JP/layout'
import jaJPNav from './locales/ja-JP/nav'

import zhCNCommon from './locales/zh-CN/common'
import zhCNLayout from './locales/zh-CN/layout'
import zhCNNav from './locales/zh-CN/nav'

import { PAGE_I18N_NAMESPACES, pageNamespacesFor } from './namespaces'

export const GLOBAL_I18N_NAMESPACES = ['common', 'layout', 'nav'] as const

export const I18N_NAMESPACES = [...GLOBAL_I18N_NAMESPACES, ...PAGE_I18N_NAMESPACES] as const

export const resources = {
  'zh-CN': {
    common: zhCNCommon,
    layout: zhCNLayout,
    nav: zhCNNav,
    ...pageNamespacesFor('zh-CN'),
  },
  'en-US': {
    common: enUSCommon,
    layout: enUSLayout,
    nav: enUSNav,
    ...pageNamespacesFor('en-US'),
  },
  'ja-JP': {
    common: jaJPCommon,
    layout: jaJPLayout,
    nav: jaJPNav,
    ...pageNamespacesFor('ja-JP'),
  },
} as const
