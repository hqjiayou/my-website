import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'
import type { HomeSectionKey, SiteAppearanceConfig } from '@/api/appearance-api'

interface SiteAppearancePreviewProps {
  siteName: string
  config: SiteAppearanceConfig
  previewMode: 'desktop' | 'mobile'
}

function radiusClass(style: SiteAppearanceConfig['theme']['radiusStyle']) {
  if (style === 'sharp') return 'rounded-none'
  if (style === 'soft') return 'rounded-lg'
  return 'rounded-2xl'
}

function shadowClass(style: SiteAppearanceConfig['theme']['shadowStyle']) {
  if (style === 'flat') return 'shadow-none'
  if (style === 'soft') return 'shadow-sm'
  return 'shadow-md'
}

function buttonRadiusClass(style: SiteAppearanceConfig['components']['buttonStyle']) {
  if (style === 'pill') return 'rounded-full'
  if (style === 'square') return 'rounded-none'
  return 'rounded-lg'
}

function imageRadiusClass(style: SiteAppearanceConfig['components']['imageStyle']) {
  if (style === 'square') return 'rounded-none'
  if (style === 'soft') return 'rounded-lg'
  return 'rounded-2xl'
}

function cardClass(style: SiteAppearanceConfig['components']['cardStyle']) {
  if (style === 'bordered') return 'border border-slate-200 bg-white'
  if (style === 'flat') return 'border border-transparent bg-slate-50'
  return 'border border-slate-100 bg-white shadow-sm'
}

function titleClass(style: SiteAppearanceConfig['components']['titleStyle']) {
  if (style === 'with-line') return 'border-l-4 border-current pl-2'
  if (style === 'accent-mark') return 'inline-flex items-center gap-2 before:h-2 before:w-2 before:rounded-full before:bg-current'
  return ''
}

function ctaClass(style: SiteAppearanceConfig['components']['ctaStyle']) {
  if (style === 'outlined') return 'border border-current bg-transparent'
  if (style === 'gradient-lite') return 'bg-gradient-to-r from-sky-500 to-indigo-500 text-white'
  return 'bg-current text-white'
}

export function SiteAppearancePreview({ siteName, config, previewMode }: SiteAppearancePreviewProps) {
  const { t } = useTranslation('styleSettings')

  const orderedSections = useMemo(
    () =>
      [...config.homeSections]
        .sort((a, b) => a.order - b.order)
        .filter((section) => section.enabled)
        .map((section) => section.key),
    [config.homeSections],
  )

  function hasSection(key: HomeSectionKey) {
    return orderedSections.includes(key)
  }

  const baseRadiusClass = radiusClass(config.theme.radiusStyle)
  const baseShadowClass = shadowClass(config.theme.shadowStyle)
  const previewCardClass = cardClass(config.components.cardStyle)
  const previewImageClass = imageRadiusClass(config.components.imageStyle)
  const previewButtonClass = buttonRadiusClass(config.components.buttonStyle)
  const previewTitleClass = titleClass(config.components.titleStyle)
  const previewCtaClass = ctaClass(config.components.ctaStyle)

  const spacingClass =
    config.layout.sectionSpacing === 'compact'
      ? 'space-y-3'
      : config.layout.sectionSpacing === 'spacious'
        ? 'space-y-6'
        : 'space-y-4'

  return (
    <div className={cn(previewMode === 'mobile' ? 'mx-auto w-full max-w-[380px]' : 'w-full')}>
      <div
        className={cn('rounded-2xl border border-slate-200 p-3', baseShadowClass)}
        style={{ backgroundColor: config.theme.backgroundColor, color: config.theme.textColor }}
      >
        <div
          className={cn(
            'mb-3 flex items-center justify-between border-b px-3 py-2 text-xs',
            config.layout.headerStyle === 'solid' && 'border-transparent text-white',
            config.layout.headerStyle === 'transparent' && 'border-slate-200',
            config.layout.headerStyle === 'minimal' && 'border-slate-100 bg-white',
            baseRadiusClass,
          )}
          style={{
            backgroundColor: config.layout.headerStyle === 'solid' ? config.theme.primaryColor : config.theme.surfaceColor,
            color: config.layout.headerStyle === 'solid' ? '#ffffff' : config.theme.textColor,
          }}
        >
          <span className="font-semibold">{siteName}</span>
          <span style={{ color: config.theme.mutedTextColor }}>{t('preview.headerMenu')}</span>
        </div>

        <div className={cn('px-1', spacingClass)}>
          {hasSection('hero') && (
            <section
              className={cn(
                'border border-slate-200 p-4',
                config.layout.heroLayout === 'full-bleed' ? 'text-white' : previewCardClass,
                baseRadiusClass,
              )}
              style={{
                backgroundColor: config.layout.heroLayout === 'full-bleed' ? config.theme.primaryColor : config.theme.surfaceColor,
              }}
            >
              {config.layout.heroLayout === 'centered' ? (
                <div className="space-y-3 text-center">
                  <h3 className={cn('text-base font-semibold', previewTitleClass)}>{t('preview.heroTitle')}</h3>
                  <p className="text-xs" style={{ color: config.theme.mutedTextColor }}>
                    {t('preview.heroSubtitle')}
                  </p>
                  <div className={cn('mx-auto h-20 w-full max-w-[240px] border border-dashed border-current/30', previewImageClass)} />
                </div>
              ) : config.layout.heroLayout === 'full-bleed' ? (
                <div className="space-y-3">
                  <h3 className={cn('text-base font-semibold', previewTitleClass)}>{t('preview.heroTitle')}</h3>
                  <p className="text-xs text-blue-100">{t('preview.heroSubtitle')}</p>
                  <div className={cn('h-20 w-full border border-dashed border-blue-200/60', previewImageClass)} />
                </div>
              ) : (
                <div className="grid gap-3 md:grid-cols-2">
                  <div className="space-y-2">
                    <h3 className={cn('text-base font-semibold', previewTitleClass)}>{t('preview.heroTitle')}</h3>
                    <p className="text-xs" style={{ color: config.theme.mutedTextColor }}>
                      {t('preview.heroSubtitle')}
                    </p>
                    <button
                      type="button"
                      className={cn('inline-flex px-3 py-1.5 text-xs font-medium', previewButtonClass, previewCtaClass)}
                      style={{ color: config.components.ctaStyle === 'outlined' ? config.theme.primaryColor : undefined }}
                    >
                      {t('preview.primaryAction')}
                    </button>
                  </div>
                  <div className={cn('h-20 border border-dashed border-slate-300', previewImageClass)} />
                </div>
              )}
            </section>
          )}

          {hasSection('services') && (
            <section className={cn('p-3', previewCardClass, baseRadiusClass)}>
              <h4 className={cn('text-sm font-semibold', previewTitleClass)}>{t('preview.sections.services')}</h4>
              <div className="mt-2 grid gap-2 sm:grid-cols-2">
                {[1, 2, 3].map((index) => (
                  <article key={index} className={cn('border border-slate-200 p-2 text-xs', previewCardClass, baseRadiusClass)}>
                    {t('preview.serviceCard')} {index}
                  </article>
                ))}
              </div>
            </section>
          )}

          {hasSection('cases') && (
            <section className={cn('p-3', previewCardClass, baseRadiusClass)}>
              <h4 className={cn('text-sm font-semibold', previewTitleClass)}>{t('preview.sections.cases')}</h4>
              <div className="mt-2 space-y-2">
                {[1, 2].map((index) => (
                  <div key={index} className={cn('flex items-center gap-2 border border-slate-200 p-2 text-xs', baseRadiusClass)}>
                    <div className={cn('h-8 w-10 border border-dashed border-slate-300', previewImageClass)} />
                    <span>{t('preview.caseItem')} {index}</span>
                  </div>
                ))}
              </div>
            </section>
          )}

          {hasSection('faq') && (
            <section className={cn('p-3', previewCardClass, baseRadiusClass)}>
              <h4 className={cn('text-sm font-semibold', previewTitleClass)}>{t('preview.sections.faq')}</h4>
              <div className="mt-2 space-y-2 text-xs">
                {[1, 2].map((index) => (
                  <div key={index} className={cn('border border-slate-200 p-2', baseRadiusClass)}>
                    Q{index}. {t('preview.faqItem')}
                  </div>
                ))}
              </div>
            </section>
          )}

          {hasSection('reservationCta') && (
            <section className={cn('p-3 text-center', previewCardClass, baseRadiusClass)}>
              <h4 className="text-sm font-semibold">{t('preview.sections.reservationCta')}</h4>
              <button
                type="button"
                className={cn('mt-2 inline-flex px-3 py-1.5 text-xs font-medium', previewButtonClass, previewCtaClass)}
                style={{ color: config.components.ctaStyle === 'outlined' ? config.theme.primaryColor : undefined }}
              >
                {t('preview.primaryAction')}
              </button>
            </section>
          )}
        </div>
      </div>
    </div>
  )
}
