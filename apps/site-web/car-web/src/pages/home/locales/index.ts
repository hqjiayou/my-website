import type { Locale } from '../../../i18n/locale'

interface PortalLink {
  title: string
  description: string
}

interface ServiceCard {
  title: string
  description: string
}

interface CaseItem {
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
  casesTitle: string
  cases: CaseItem[]
  faqTitle: string
  faqs: FaqItem[]
  contactTitle: string
  contactDescription: string
  contactItems: string[]
  footerText: string
}

export const siteHomeLocales: Record<Locale, SiteHomeCopy> = {
  'zh-CN': {
    siteName: 'My Website · 车务门户',
    languageLabel: '语言',
    themeLabel: '主题',
    lightThemeLabel: '明亮',
    darkThemeLabel: '暗黑',
    templateLabel: 'My Website - 汽修模板',
    title: '一站式汽车服务门户',
    subtitle: '覆盖保养、维修、年检、道路救援与保险协同，给车主更清晰的服务体验。',
    spotlightTitle: '本月门户亮点',
    spotlightItems: ['预约转化率 +38%', '平均响应 2 分钟', '连锁门店接入 126 家'],
    primaryAction: '立即预约',
    secondaryAction: '在线咨询',
    portalNavTitle: '快速入口',
    portalLinks: [
      { title: '保养预约', description: '按车型和里程快速匹配保养套餐。' },
      { title: '维修估价', description: '上传问题描述，获取初步维修建议。' },
      { title: '年检代办', description: '查看流程与时间安排，在线提交资料。' },
      { title: '道路救援', description: '事故、抛锚场景下快速联络服务网点。' },
    ],
    servicesTitle: '核心模块',
    services: [
      { title: '维修服务模块', description: '统一管理故障诊断、工单进度与配件信息。' },
      { title: '预约调度模块', description: '支持门店时段管理、改约与提醒通知。' },
      { title: '客户运营模块', description: '沉淀客户画像，支持会员与二次触达。' },
    ],
    casesTitle: '近期案例',
    cases: [
      { title: '发动机异响排查并修复', meta: '丰田 Prius · 完成周期 1.5 天' },
      { title: '制动系统保养优化', meta: '日产 Serena · 完成周期 4 小时' },
      { title: '事故钣喷快速交付', meta: '本田 Fit · 完成周期 2 天' },
    ],
    faqTitle: '常见问题',
    faqs: [
      { question: '如何判断是否需要提前保养？', answer: '可按里程、驾驶环境和系统提示综合判断，门户会给出提醒。' },
      { question: '预约后可以改期吗？', answer: '支持在预约记录中一键改期，系统会自动同步门店排班。' },
      { question: '线上咨询是否收费？', answer: '基础故障咨询免费，复杂诊断可升级为专家服务。' },
    ],
    contactTitle: '联系我们',
    contactDescription: '工作日 08:30 - 20:00，支持电话、在线客服与门店到访。',
    contactItems: ['客服电话：400-820-0000', '邮箱：service@mywebsite.example', '地址：东京都港区示例路 12-3'],
    footerText: 'My Website Car Portal · 模块化门户框架示例',
  },
  'ja-JP': {
    siteName: 'My Website · カーサービスポータル',
    languageLabel: '言語',
    themeLabel: 'テーマ',
    lightThemeLabel: 'ライト',
    darkThemeLabel: 'ダーク',
    templateLabel: 'My Website - 整備工場テンプレート',
    title: '自動車サービスの統合ポータル',
    subtitle: '点検、修理、車検、ロードサポートまでを一つの導線で分かりやすく提供します。',
    spotlightTitle: '今月のハイライト',
    spotlightItems: ['予約成約率 +38%', '平均返信 2 分', '提携店舗 126 拠点'],
    primaryAction: '予約する',
    secondaryAction: '相談する',
    portalNavTitle: 'クイックメニュー',
    portalLinks: [
      { title: '点検予約', description: '車種・走行距離に合わせたメニューを提案します。' },
      { title: '修理見積もり', description: '症状を入力すると概算の対応案を表示します。' },
      { title: '車検サポート', description: '必要書類とスケジュールをオンラインで管理します。' },
      { title: 'ロードサービス', description: '故障時に近隣拠点へ迅速に連絡できます。' },
    ],
    servicesTitle: '主要モジュール',
    services: [
      { title: '整備管理モジュール', description: '診断、作業進捗、部品情報を一元化します。' },
      { title: '予約管理モジュール', description: '枠管理、日程変更、通知を自動化します。' },
      { title: '顧客運用モジュール', description: '顧客データを活用し継続来店を支援します。' },
    ],
    casesTitle: '最近の事例',
    cases: [
      { title: 'エンジン異音の原因特定と修理', meta: 'トヨタ Prius · 1.5日で完了' },
      { title: 'ブレーキ整備の最適化', meta: '日産 Serena · 4時間で完了' },
      { title: '事故後の板金塗装対応', meta: 'ホンダ Fit · 2日で完了' },
    ],
    faqTitle: 'よくある質問',
    faqs: [
      { question: '点検時期はどう判断しますか？', answer: '走行距離・利用状況・警告表示をもとに最適な時期を提案します。' },
      { question: '予約後の日程変更は可能ですか？', answer: 'マイページから簡単に変更でき、店舗側にも自動反映されます。' },
      { question: 'オンライン相談は無料ですか？', answer: '基本相談は無料で、詳細診断は有料プランに切り替え可能です。' },
    ],
    contactTitle: 'お問い合わせ',
    contactDescription: '平日 08:30 - 20:00。電話・チャット・来店で対応します。',
    contactItems: ['電話：03-1234-5678', 'メール：service@mywebsite.example', '住所：東京都港区サンプル 12-3'],
    footerText: 'My Website Car Portal · モジュール型ポータルフレーム',
  },
  'en-US': {
    siteName: 'My Website · Car Service Portal',
    languageLabel: 'Language',
    themeLabel: 'Theme',
    lightThemeLabel: 'Light',
    darkThemeLabel: 'Dark',
    templateLabel: 'My Website - Garage Template',
    title: 'A modular portal for car owners',
    subtitle: 'Unify maintenance, repair, inspection, roadside support, and consultation in one clear journey.',
    spotlightTitle: 'This Month Highlights',
    spotlightItems: ['+38% booking conversion', '2-minute average response', '126 connected service points'],
    primaryAction: 'Book Now',
    secondaryAction: 'Talk to Advisor',
    portalNavTitle: 'Quick Access',
    portalLinks: [
      { title: 'Maintenance Booking', description: 'Match plans by vehicle type and mileage.' },
      { title: 'Repair Estimate', description: 'Submit symptoms and get an initial recommendation.' },
      { title: 'Inspection Support', description: 'Track required documents and service schedule.' },
      { title: 'Roadside Help', description: 'Reach nearest support point during incidents.' },
    ],
    servicesTitle: 'Core Modules',
    services: [
      { title: 'Repair Operations Module', description: 'Centralize diagnostics, work orders, and parts status.' },
      { title: 'Scheduling Module', description: 'Manage time slots, reschedules, and reminders.' },
      { title: 'Customer Growth Module', description: 'Use customer profiles to improve retention.' },
    ],
    casesTitle: 'Recent Cases',
    cases: [
      { title: 'Engine noise diagnosis and repair', meta: 'Toyota Prius · completed in 1.5 days' },
      { title: 'Brake system optimization service', meta: 'Nissan Serena · completed in 4 hours' },
      { title: 'Fast body paint delivery after accident', meta: 'Honda Fit · completed in 2 days' },
    ],
    faqTitle: 'Frequently Asked Questions',
    faqs: [
      { question: 'How do I know when maintenance is needed?', answer: 'The portal evaluates mileage, usage pattern, and warning indicators.' },
      { question: 'Can I reschedule after booking?', answer: 'Yes, you can reschedule from your booking page and the slot is synced instantly.' },
      { question: 'Is online consultation free?', answer: 'Basic consultation is free; advanced diagnostics can be upgraded.' },
    ],
    contactTitle: 'Contact',
    contactDescription: 'Available on weekdays from 08:30 to 20:00 via phone, chat, and in-store support.',
    contactItems: ['Hotline: +81-3-1234-5678', 'Email: service@mywebsite.example', 'Address: 12-3 Sample Street, Minato, Tokyo'],
    footerText: 'My Website Car Portal · Modular portal foundation',
  },
}
