import { useTranslation } from 'react-i18next'

import { cn } from '@/lib/cn'
import type { AppearanceConfigV1, ButtonStyle, HeroLayout } from '@/types/appearance'

interface AgentAppearancePreviewProps {
  agentName: string
  config: AppearanceConfigV1
  previewMode: 'desktop' | 'mobile'
}

function buttonRadiusClass(style: ButtonStyle) {
  if (style === 'pill') return 'rounded-full'
  if (style === 'square') return 'rounded-none'
  return 'rounded-lg'
}

function heroLayoutClass(layout: HeroLayout) {
  if (layout === 'centered') return 'text-center'
  if (layout === 'full-bleed') return 'text-white'
  return ''
}

export function AgentAppearancePreview({ agentName, config, previewMode }: AgentAppearancePreviewProps) {
  const { t } = useTranslation('styleSettings')
  const buttonClass = buttonRadiusClass(config.buttonStyle)

  return (
    <div className={cn(previewMode === 'mobile' ? 'mx-auto w-full max-w-[380px]' : 'w-full')}>
      <div className="rounded-xl border border-border bg-card-muted p-3">
        <div
          className="mb-3 flex items-center justify-between rounded-xl px-3 py-2 text-xs text-white"
          style={{ backgroundColor: config.primaryColor }}
        >
          <span className="font-semibold">{agentName}</span>
          <span className="text-white/80">{t('preview.headerMenu')}</span>
        </div>

        <section
          className={cn(
            'rounded-xl border border-slate-200 p-4',
            config.heroLayout === 'full-bleed' ? '' : 'bg-white',
            heroLayoutClass(config.heroLayout),
          )}
          style={{
            backgroundColor: config.heroLayout === 'full-bleed' ? config.primaryColor : undefined,
            fontFamily:
              config.fontStyle === 'classic'
                ? 'Georgia, "Times New Roman", serif'
                : config.fontStyle === 'soft'
                  ? '"Segoe UI", "Hiragino Sans", sans-serif'
                  : config.fontStyle === 'japanese'
                    ? '"Noto Sans JP", "Hiragino Sans", sans-serif'
                    : 'ui-sans-serif, system-ui, sans-serif',
          }}
        >
          {config.heroLayout === 'left-text-right-visual' ? (
            <div className="grid gap-3 md:grid-cols-2">
              <div className="space-y-2">
                <h3 className="text-base font-semibold text-slate-900">{t('preview.heroTitle')}</h3>
                <p className="text-xs text-slate-500">{t('preview.heroSubtitle')}</p>
                <button
                  type="button"
                  className={cn('inline-flex px-3 py-1.5 text-xs font-medium text-white', buttonClass)}
                  style={{ backgroundColor: config.primaryColor }}
                >
                  {t('preview.primaryAction')}
                </button>
              </div>
              <div className="h-20 rounded-lg border border-dashed border-slate-300 bg-slate-50" />
            </div>
          ) : (
            <div className="space-y-3">
              <h3
                className={cn(
                  'text-base font-semibold',
                  config.heroLayout === 'full-bleed' ? 'text-white' : 'text-slate-900',
                )}
              >
                {t('preview.heroTitle')}
              </h3>
              <p className={cn('text-xs', config.heroLayout === 'full-bleed' ? 'text-white/80' : 'text-slate-500')}>
                {t('preview.heroSubtitle')}
              </p>
              <div
                className={cn(
                  'mx-auto h-20 w-full max-w-[240px] border border-dashed',
                  config.heroLayout === 'full-bleed' ? 'border-white/40' : 'border-slate-300',
                )}
              />
              <button
                type="button"
                className={cn(
                  'inline-flex px-3 py-1.5 text-xs font-medium',
                  buttonClass,
                  config.heroLayout === 'full-bleed' ? 'bg-white' : 'text-white',
                )}
                style={
                  config.heroLayout === 'full-bleed'
                    ? { color: config.primaryColor }
                    : { backgroundColor: config.primaryColor }
                }
              >
                {t('preview.primaryAction')}
              </button>
            </div>
          )}
        </section>
      </div>
    </div>
  )
}
