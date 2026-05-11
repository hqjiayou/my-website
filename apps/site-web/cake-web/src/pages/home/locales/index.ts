import type { Locale } from '../../../i18n/locale'

interface PortalLink {
  title: string
  description: string
}

interface ServiceCard {
  title: string
  description: string
}

interface ProductItem {
  title: string
  meta: string
}

interface FaqItem {
  question: string
  answer: string
}

export interface SiteHomeCopy {
  siteName: string
  languageLabel: string
  themeLabel: string
  lightThemeLabel: string
  darkThemeLabel: string
  templateLabel: string
  title: string
  subtitle: string
  spotlightTitle: string
  spotlightItems: string[]
  primaryAction: string
  secondaryAction: string
  portalNavTitle: string
  portalLinks: PortalLink[]
  servicesTitle: string
  services: ServiceCard[]
  productsTitle: string
  products: ProductItem[]
  faqTitle: string
  faqs: FaqItem[]
  contactTitle: string
  contactDescription: string
  contactItems: string[]
  footerText: string
}

export const siteHomeLocales: Record<Locale, SiteHomeCopy> = {
  'zh-CN': {
    siteName: 'My Website · 蛋糕门户',
    languageLabel: '语言',
    themeLabel: '主题',
    lightThemeLabel: '明亮',
    darkThemeLabel: '暗黑',
    templateLabel: 'My Website - 蛋糕模板',
    title: '烘焙品牌一站式门户',
    subtitle: '从商品展示、在线预订到会员运营，帮助烘焙门店快速搭建统一对外入口。',
    spotlightTitle: '本月门户亮点',
    spotlightItems: ['节日订单量 +42%', '新品复购率 +27%', '会员活跃门店 89 家'],
    primaryAction: '立即下单',
    secondaryAction: '定制咨询',
    portalNavTitle: '快速入口',
    portalLinks: [
      { title: '生日蛋糕', description: '支持尺寸、口味、配件和配送时间选择。' },
      { title: '门店自提', description: '按门店库存和营业时间快速预约。' },
      { title: '企业团购', description: '按预算推荐组合套餐并统一开票。' },
      { title: '节日礼盒', description: '整合节日主题包装和批量配送。' },
    ],
    servicesTitle: '核心模块',
    services: [
      { title: '商品管理模块', description: '统一管理商品、规格、库存与上下架状态。' },
      { title: '订单履约模块', description: '覆盖下单、生产排期、配送与售后流程。' },
      { title: '会员运营模块', description: '支持积分、优惠券、复购提醒与活动营销。' },
    ],
    productsTitle: '热门单品',
    products: [
      { title: '草莓奶油生日蛋糕', meta: '6 寸起售 · 当日可约' },
      { title: '法式巧克力慕斯', meta: '经典畅销 · 支持加字牌' },
      { title: '季节限定水果塔', meta: '周末限定 · 数量有限' },
    ],
    faqTitle: '常见问题',
    faqs: [
      { question: '最晚提前多久下单？', answer: '常规款建议提前 24 小时，定制款建议提前 48 小时。' },
      { question: '可以指定配送时间吗？', answer: '支持时间段预约，节假日高峰建议尽早下单。' },
      { question: '支持无接触配送吗？', answer: '支持，可在订单备注中填写无接触交付需求。' },
    ],
    contactTitle: '联系我们',
    contactDescription: '每日 09:00 - 21:00 在线服务，支持电话与门店到店咨询。',
    contactItems: ['客服热线：400-810-2222', '邮箱：cake@mywebsite.example', '地址：东京都港区示例路 9-2'],
    footerText: 'My Website Cake Portal · 模块化门户框架示例',
  },
  'ja-JP': {
    siteName: 'My Website · ケーキポータル',
    languageLabel: '言語',
    themeLabel: 'テーマ',
    lightThemeLabel: 'ライト',
    darkThemeLabel: 'ダーク',
    templateLabel: 'My Website - ケーキテンプレート',
    title: 'ベーカリーブランド向け統合ポータル',
    subtitle: '商品紹介、オンライン予約、会員運用までを一つの体験として提供します。',
    spotlightTitle: '今月のハイライト',
    spotlightItems: ['季節注文 +42%', '新作リピート率 +27%', '会員稼働店舗 89 店舗'],
    primaryAction: '今すぐ注文',
    secondaryAction: 'カスタム相談',
    portalNavTitle: 'クイックメニュー',
    portalLinks: [
      { title: 'バースデーケーキ', description: 'サイズ・味・装飾を細かく指定できます。' },
      { title: '店頭受け取り', description: '店舗在庫と営業時間に合わせて予約可能です。' },
      { title: '法人まとめ注文', description: '予算に応じたセット提案と請求対応を行います。' },
      { title: '季節ギフト', description: '季節限定パッケージと配送をまとめて管理します。' },
    ],
    servicesTitle: '主要モジュール',
    services: [
      { title: '商品管理モジュール', description: '商品、規格、在庫、公開状態を一元管理します。' },
      { title: '注文管理モジュール', description: '受注から製造、配送、アフター対応までを連携します。' },
      { title: '会員運用モジュール', description: 'ポイント、クーポン、再購入施策を支援します。' },
    ],
    productsTitle: '人気商品',
    products: [
      { title: 'いちごショートケーキ', meta: '6号から対応 · 当日受取可' },
      { title: 'チョコレートムース', meta: '定番人気 · メッセージプレート可' },
      { title: '季節フルーツタルト', meta: '週末限定 · 数量限定' },
    ],
    faqTitle: 'よくある質問',
    faqs: [
      { question: '注文は何日前までに必要ですか？', answer: '通常商品は24時間前、カスタム商品は48時間前が目安です。' },
      { question: '配達時間の指定はできますか？', answer: '時間帯指定に対応しています。繁忙期は早めの予約を推奨します。' },
      { question: '置き配は可能ですか？', answer: '可能です。注文備考にご希望の受け取り方法をご記入ください。' },
    ],
    contactTitle: 'お問い合わせ',
    contactDescription: '毎日 09:00 - 21:00 に対応。電話・チャット・店頭相談が可能です。',
    contactItems: ['電話：03-2233-4455', 'メール：cake@mywebsite.example', '住所：東京都港区サンプル 9-2'],
    footerText: 'My Website Cake Portal · モジュール型ポータルフレーム',
  },
  'en-US': {
    siteName: 'My Website · Cake Portal',
    languageLabel: 'Language',
    themeLabel: 'Theme',
    lightThemeLabel: 'Light',
    darkThemeLabel: 'Dark',
    templateLabel: 'My Website - Cake Template',
    title: 'A unified portal for bakery brands',
    subtitle: 'Present products, manage online orders, and run member growth from one portal experience.',
    spotlightTitle: 'This Month Highlights',
    spotlightItems: ['+42% seasonal orders', '+27% repeat purchase on new items', '89 active member stores'],
    primaryAction: 'Order Now',
    secondaryAction: 'Custom Inquiry',
    portalNavTitle: 'Quick Access',
    portalLinks: [
      { title: 'Birthday Cakes', description: 'Choose size, flavor, and decoration options easily.' },
      { title: 'Store Pickup', description: 'Reserve by branch availability and pickup slots.' },
      { title: 'Corporate Orders', description: 'Get bundled recommendations based on your budget.' },
      { title: 'Seasonal Gifts', description: 'Manage themed packaging and batch delivery.' },
    ],
    servicesTitle: 'Core Modules',
    services: [
      { title: 'Catalog Module', description: 'Manage products, options, inventory, and availability.' },
      { title: 'Order Fulfillment Module', description: 'Connect ordering, production, delivery, and support.' },
      { title: 'Member Growth Module', description: 'Support points, coupons, and repeat-purchase campaigns.' },
    ],
    productsTitle: 'Featured Products',
    products: [
      { title: 'Strawberry Cream Birthday Cake', meta: 'From 6-inch · same-day pickup available' },
      { title: 'Signature Chocolate Mousse', meta: 'Best seller · custom message plate supported' },
      { title: 'Seasonal Fruit Tart', meta: 'Weekend limited · while supplies last' },
    ],
    faqTitle: 'Frequently Asked Questions',
    faqs: [
      { question: 'How far in advance should I order?', answer: '24 hours for regular products and 48 hours for custom designs.' },
      { question: 'Can I select a delivery time?', answer: 'Yes, time-slot delivery is available. Book early during holidays.' },
      { question: 'Do you support contactless delivery?', answer: 'Yes, add your preference in the order note.' },
    ],
    contactTitle: 'Contact',
    contactDescription: 'Support is available daily from 09:00 to 21:00 by phone, chat, or in-store visit.',
    contactItems: ['Hotline: +81-3-2233-4455', 'Email: cake@mywebsite.example', 'Address: 9-2 Sample Street, Minato, Tokyo'],
    footerText: 'My Website Cake Portal · Modular portal foundation',
  },
}
