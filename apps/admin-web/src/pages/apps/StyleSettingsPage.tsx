import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Monitor, Smartphone } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import { siteApi } from '@/api'
import { appearanceApi, type HomeSectionKey, type SiteAppearanceConfig } from '@/api/appearance-api'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { SiteAppearancePreview } from '@/pages/apps/style/site-appearance-preview'

type AppearanceTab = 'theme' | 'layout' | 'homeSections' | 'components'

const APPEARANCE_TABS: AppearanceTab[] = ['theme', 'layout', 'homeSections', 'components']

function cloneAppearance(config: SiteAppearanceConfig): SiteAppearanceConfig {
  return JSON.parse(JSON.stringify(config)) as SiteAppearanceConfig
}

export function StyleSettingsPage() {
  const { t } = useTranslation('styleSettings')
  const queryClient = useQueryClient()
  const [activeTab, setActiveTab] = useState<AppearanceTab>('theme')
  const [previewMode, setPreviewMode] = useState<'desktop' | 'mobile'>('desktop')
  const [selectedSiteId, setSelectedSiteId] = useState<string>('')
  const [draftConfig, setDraftConfig] = useState<SiteAppearanceConfig | null>(null)
  const [initialConfig, setInitialConfig] = useState<SiteAppearanceConfig | null>(null)

  const { data: sites = [], isPending: isSitesPending } = useQuery({
    queryKey: ['sites', 'style-settings'],
    queryFn: siteApi.listSites,
  })

  useEffect(() => {
    if (!selectedSiteId && sites.length > 0) {
      setSelectedSiteId(sites[0].id)
    }
  }, [selectedSiteId, sites])

  const { data: appearanceConfig, isPending: isAppearancePending } = useQuery({
    queryKey: ['appearances', selectedSiteId],
    queryFn: () => appearanceApi.getAppearance(selectedSiteId),
    enabled: Boolean(selectedSiteId),
  })

  useEffect(() => {
    if (!appearanceConfig) {
      setDraftConfig(null)
      setInitialConfig(null)
      return
    }
    setDraftConfig(cloneAppearance(appearanceConfig))
    setInitialConfig(cloneAppearance(appearanceConfig))
  }, [appearanceConfig])

  const selectedSite = useMemo(() => sites.find((site) => site.id === selectedSiteId) ?? null, [selectedSiteId, sites])

  const sortedSections = useMemo(() => {
    if (!draftConfig) return []
    return [...draftConfig.homeSections].sort((a, b) => a.order - b.order)
  }, [draftConfig])

  const saveMutation = useMutation({
    mutationFn: ({ siteId, input }: { siteId: string; input: Partial<SiteAppearanceConfig> }) =>
      appearanceApi.updateAppearance(siteId, input),
    onSuccess: async (updatedConfig) => {
      if (!updatedConfig) return
      setDraftConfig(cloneAppearance(updatedConfig))
      setInitialConfig(cloneAppearance(updatedConfig))
      await queryClient.invalidateQueries({ queryKey: ['appearances', updatedConfig.siteId] })
      toast.success(t('messages.saved'))
    },
  })

  function updateTheme<K extends keyof SiteAppearanceConfig['theme']>(key: K, value: SiteAppearanceConfig['theme'][K]) {
    setDraftConfig((prev) => (prev ? { ...prev, theme: { ...prev.theme, [key]: value } } : prev))
  }

  function updateLayout<K extends keyof SiteAppearanceConfig['layout']>(key: K, value: SiteAppearanceConfig['layout'][K]) {
    setDraftConfig((prev) => (prev ? { ...prev, layout: { ...prev.layout, [key]: value } } : prev))
  }

  function updateComponents<K extends keyof SiteAppearanceConfig['components']>(
    key: K,
    value: SiteAppearanceConfig['components'][K],
  ) {
    setDraftConfig((prev) => (prev ? { ...prev, components: { ...prev.components, [key]: value } } : prev))
  }

  function toggleSectionEnabled(key: HomeSectionKey) {
    setDraftConfig((prev) => {
      if (!prev) return prev
      return {
        ...prev,
        homeSections: prev.homeSections.map((section) =>
          section.key === key ? { ...section, enabled: !section.enabled } : section,
        ),
      }
    })
  }

  function moveSection(key: HomeSectionKey, direction: -1 | 1) {
    setDraftConfig((prev) => {
      if (!prev) return prev
      const ordered = [...prev.homeSections].sort((a, b) => a.order - b.order)
      const index = ordered.findIndex((section) => section.key === key)
      const targetIndex = index + direction
      if (index < 0 || targetIndex < 0 || targetIndex >= ordered.length) {
        return prev
      }

      const reordered = [...ordered]
      const temp = reordered[index]
      reordered[index] = reordered[targetIndex]
      reordered[targetIndex] = temp

      return {
        ...prev,
        homeSections: reordered.map((section, newIndex) => ({ ...section, order: newIndex + 1 })),
      }
    })
  }

  function resetToDefault() {
    if (!initialConfig) return
    setDraftConfig(cloneAppearance(initialConfig))
  }

  function saveConfig() {
    if (!draftConfig || !selectedSiteId) return
    saveMutation.mutate({ siteId: selectedSiteId, input: draftConfig })
  }

  if (isSitesPending) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
        {t('messages.loadingSites')}
      </section>
    )
  }

  return (
    <section>
      <PageHeader
        title={t('title')}
        description={t('description')}
        action={
          <div className="flex flex-wrap items-center gap-2">
            <label className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-sm text-slate-700">
              <span className="text-slate-500">{t('currentSite')}</span>
              <select
                value={selectedSiteId}
                onChange={(event) => setSelectedSiteId(event.target.value)}
                className="min-w-36 border-none bg-transparent text-sm outline-none"
              >
                {sites.map((site) => (
                  <option key={site.id} value={site.id}>
                    {site.name}
                  </option>
                ))}
              </select>
            </label>

            <button
              type="button"
              onClick={saveConfig}
              disabled={!draftConfig || saveMutation.isPending}
              className="rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700 disabled:opacity-60"
            >
              {t('saveConfig')}
            </button>
            <button
              type="button"
              onClick={resetToDefault}
              disabled={!draftConfig}
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-60"
            >
              {t('resetDefault')}
            </button>
            <button
              type="button"
              onClick={() => toast.info(t('messages.previewPlaceholder'))}
              className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
            >
              {t('previewSite')}
            </button>
          </div>
        }
      />

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(360px,1fr)]">
        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <nav className="mb-4 flex flex-wrap gap-2">
            {APPEARANCE_TABS.map((tabKey) => (
              <button
                key={tabKey}
                type="button"
                onClick={() => setActiveTab(tabKey)}
                className={cn(
                  'rounded-lg border px-3 py-1.5 text-sm font-medium transition',
                  activeTab === tabKey
                    ? 'border-blue-200 bg-blue-50 text-blue-700'
                    : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
                )}
              >
                {t(`tabs.${tabKey}`)}
              </button>
            ))}
          </nav>

          {!draftConfig && (
            <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-3 py-4 text-sm text-slate-600">
              {isAppearancePending ? t('messages.loadingConfig') : t('messages.configUnavailable')}
            </div>
          )}

          {draftConfig && activeTab === 'theme' && (
            <div className="grid gap-4 md:grid-cols-2">
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.colorScheme')}</span>
                <select
                  value={draftConfig.theme.colorScheme}
                  onChange={(event) => updateTheme('colorScheme', event.target.value as SiteAppearanceConfig['theme']['colorScheme'])}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="light">{t('options.colorScheme.light')}</option>
                  <option value="warm">{t('options.colorScheme.warm')}</option>
                  <option value="dark-lite">{t('options.colorScheme.dark-lite')}</option>
                </select>
              </label>

              {(
                [
                  ['primaryColor', 'fields.primaryColor'],
                  ['secondaryColor', 'fields.secondaryColor'],
                  ['accentColor', 'fields.accentColor'],
                  ['backgroundColor', 'fields.backgroundColor'],
                  ['surfaceColor', 'fields.surfaceColor'],
                  ['textColor', 'fields.textColor'],
                  ['mutedTextColor', 'fields.mutedTextColor'],
                ] as const
              ).map(([key, labelKey]) => (
                <label key={key} className="space-y-1.5 text-sm">
                  <span className="font-medium text-slate-700">{t(labelKey)}</span>
                  <div className="flex items-center gap-2">
                    <input
                      type="color"
                      value={draftConfig.theme[key]}
                      onChange={(event) => updateTheme(key, event.target.value)}
                      className="h-10 w-12 rounded-lg border border-slate-200 bg-white"
                    />
                    <input
                      value={draftConfig.theme[key]}
                      onChange={(event) => updateTheme(key, event.target.value)}
                      className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                    />
                  </div>
                </label>
              ))}

              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.fontStyle')}</span>
                <select
                  value={draftConfig.theme.fontStyle}
                  onChange={(event) => updateTheme('fontStyle', event.target.value as SiteAppearanceConfig['theme']['fontStyle'])}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="modern">{t('options.fontStyle.modern')}</option>
                  <option value="classic">{t('options.fontStyle.classic')}</option>
                  <option value="soft">{t('options.fontStyle.soft')}</option>
                  <option value="japanese">{t('options.fontStyle.japanese')}</option>
                </select>
              </label>
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.radiusStyle')}</span>
                <select
                  value={draftConfig.theme.radiusStyle}
                  onChange={(event) => updateTheme('radiusStyle', event.target.value as SiteAppearanceConfig['theme']['radiusStyle'])}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="sharp">{t('options.radiusStyle.sharp')}</option>
                  <option value="soft">{t('options.radiusStyle.soft')}</option>
                  <option value="rounded">{t('options.radiusStyle.rounded')}</option>
                </select>
              </label>
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.shadowStyle')}</span>
                <select
                  value={draftConfig.theme.shadowStyle}
                  onChange={(event) => updateTheme('shadowStyle', event.target.value as SiteAppearanceConfig['theme']['shadowStyle'])}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="flat">{t('options.shadowStyle.flat')}</option>
                  <option value="soft">{t('options.shadowStyle.soft')}</option>
                  <option value="elevated">{t('options.shadowStyle.elevated')}</option>
                </select>
              </label>
            </div>
          )}

          {draftConfig && activeTab === 'layout' && (
            <div className="grid gap-4 md:grid-cols-2">
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.headerStyle')}</span>
                <select
                  value={draftConfig.layout.headerStyle}
                  onChange={(event) => updateLayout('headerStyle', event.target.value as SiteAppearanceConfig['layout']['headerStyle'])}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="solid">{t('options.headerStyle.solid')}</option>
                  <option value="transparent">{t('options.headerStyle.transparent')}</option>
                  <option value="minimal">{t('options.headerStyle.minimal')}</option>
                </select>
              </label>
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.footerStyle')}</span>
                <select
                  value={draftConfig.layout.footerStyle}
                  onChange={(event) => updateLayout('footerStyle', event.target.value as SiteAppearanceConfig['layout']['footerStyle'])}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="simple">{t('options.footerStyle.simple')}</option>
                  <option value="multi-column">{t('options.footerStyle.multi-column')}</option>
                </select>
              </label>
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.containerWidth')}</span>
                <select
                  value={draftConfig.layout.containerWidth}
                  onChange={(event) =>
                    updateLayout('containerWidth', event.target.value as SiteAppearanceConfig['layout']['containerWidth'])
                  }
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="standard">{t('options.containerWidth.standard')}</option>
                  <option value="wide">{t('options.containerWidth.wide')}</option>
                  <option value="compact">{t('options.containerWidth.compact')}</option>
                </select>
              </label>
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.sectionSpacing')}</span>
                <select
                  value={draftConfig.layout.sectionSpacing}
                  onChange={(event) =>
                    updateLayout('sectionSpacing', event.target.value as SiteAppearanceConfig['layout']['sectionSpacing'])
                  }
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="compact">{t('options.sectionSpacing.compact')}</option>
                  <option value="normal">{t('options.sectionSpacing.normal')}</option>
                  <option value="spacious">{t('options.sectionSpacing.spacious')}</option>
                </select>
              </label>
              <label className="space-y-1.5 text-sm md:col-span-2">
                <span className="font-medium text-slate-700">{t('fields.heroLayout')}</span>
                <select
                  value={draftConfig.layout.heroLayout}
                  onChange={(event) => updateLayout('heroLayout', event.target.value as SiteAppearanceConfig['layout']['heroLayout'])}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="left-text-right-visual">{t('options.heroLayout.left-text-right-visual')}</option>
                  <option value="centered">{t('options.heroLayout.centered')}</option>
                  <option value="full-bleed">{t('options.heroLayout.full-bleed')}</option>
                </select>
              </label>
            </div>
          )}

          {draftConfig && activeTab === 'homeSections' && (
            <div className="space-y-3">
              {sortedSections.map((section, index) => (
                <article key={section.key} className="rounded-xl border border-slate-200 bg-slate-50 p-3">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <p className="text-sm font-semibold text-slate-900">{t(`homeSectionLabels.${section.key}`)}</p>
                      <p className="mt-1 text-xs text-slate-500">
                        {t('variantLabel')}: {section.variant}
                      </p>
                    </div>
                    <span
                      className={cn(
                        'inline-flex rounded-md px-2 py-1 text-xs font-medium',
                        section.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500',
                      )}
                    >
                      {section.enabled ? t('sectionActions.enabled') : t('sectionActions.disabled')}
                    </span>
                  </div>
                  <div className="mt-3 flex flex-wrap gap-2">
                    <button
                      type="button"
                      onClick={() => toggleSectionEnabled(section.key)}
                      className="rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-50"
                    >
                      {section.enabled ? t('sectionActions.disabled') : t('sectionActions.enabled')}
                    </button>
                    <button
                      type="button"
                      onClick={() => moveSection(section.key, -1)}
                      disabled={index === 0}
                      className="rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-50"
                    >
                      {t('sectionActions.moveUp')}
                    </button>
                    <button
                      type="button"
                      onClick={() => moveSection(section.key, 1)}
                      disabled={index === sortedSections.length - 1}
                      className="rounded-md border border-slate-200 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-50"
                    >
                      {t('sectionActions.moveDown')}
                    </button>
                    <span className="inline-flex items-center text-xs text-slate-500">
                      #{section.order}
                    </span>
                  </div>
                </article>
              ))}
            </div>
          )}

          {draftConfig && activeTab === 'components' && (
            <div className="grid gap-4 md:grid-cols-2">
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.buttonStyle')}</span>
                <select
                  value={draftConfig.components.buttonStyle}
                  onChange={(event) =>
                    updateComponents('buttonStyle', event.target.value as SiteAppearanceConfig['components']['buttonStyle'])
                  }
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="rounded">{t('options.buttonStyle.rounded')}</option>
                  <option value="pill">{t('options.buttonStyle.pill')}</option>
                  <option value="square">{t('options.buttonStyle.square')}</option>
                </select>
              </label>
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.cardStyle')}</span>
                <select
                  value={draftConfig.components.cardStyle}
                  onChange={(event) =>
                    updateComponents('cardStyle', event.target.value as SiteAppearanceConfig['components']['cardStyle'])
                  }
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="bordered">{t('options.cardStyle.bordered')}</option>
                  <option value="shadow">{t('options.cardStyle.shadow')}</option>
                  <option value="flat">{t('options.cardStyle.flat')}</option>
                </select>
              </label>
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.imageStyle')}</span>
                <select
                  value={draftConfig.components.imageStyle}
                  onChange={(event) =>
                    updateComponents('imageStyle', event.target.value as SiteAppearanceConfig['components']['imageStyle'])
                  }
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="rounded">{t('options.imageStyle.rounded')}</option>
                  <option value="soft">{t('options.imageStyle.soft')}</option>
                  <option value="square">{t('options.imageStyle.square')}</option>
                </select>
              </label>
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('fields.titleStyle')}</span>
                <select
                  value={draftConfig.components.titleStyle}
                  onChange={(event) =>
                    updateComponents('titleStyle', event.target.value as SiteAppearanceConfig['components']['titleStyle'])
                  }
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="plain">{t('options.titleStyle.plain')}</option>
                  <option value="with-line">{t('options.titleStyle.with-line')}</option>
                  <option value="accent-mark">{t('options.titleStyle.accent-mark')}</option>
                </select>
              </label>
              <label className="space-y-1.5 text-sm md:col-span-2">
                <span className="font-medium text-slate-700">{t('fields.ctaStyle')}</span>
                <select
                  value={draftConfig.components.ctaStyle}
                  onChange={(event) =>
                    updateComponents('ctaStyle', event.target.value as SiteAppearanceConfig['components']['ctaStyle'])
                  }
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  <option value="solid">{t('options.ctaStyle.solid')}</option>
                  <option value="gradient-lite">{t('options.ctaStyle.gradient-lite')}</option>
                  <option value="outlined">{t('options.ctaStyle.outlined')}</option>
                </select>
              </label>
            </div>
          )}
        </article>

        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-base font-semibold text-slate-900">{t('previewSite')}</h2>
            <div className="inline-flex rounded-lg border border-slate-200 bg-slate-50 p-1">
              <button
                type="button"
                onClick={() => setPreviewMode('desktop')}
                className={cn(
                  'inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium',
                  previewMode === 'desktop' ? 'bg-white text-blue-700 shadow-sm' : 'text-slate-600',
                )}
              >
                <Monitor className="h-3.5 w-3.5" />
                {t('previewMode.desktop')}
              </button>
              <button
                type="button"
                onClick={() => setPreviewMode('mobile')}
                className={cn(
                  'inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium',
                  previewMode === 'mobile' ? 'bg-white text-blue-700 shadow-sm' : 'text-slate-600',
                )}
              >
                <Smartphone className="h-3.5 w-3.5" />
                {t('previewMode.mobile')}
              </button>
            </div>
          </div>

          {!draftConfig && (
            <div className="rounded-xl border border-dashed border-slate-300 bg-slate-50 px-3 py-5 text-sm text-slate-600">
              {isAppearancePending ? t('messages.loadingConfig') : t('messages.configUnavailable')}
            </div>
          )}
          {draftConfig && <SiteAppearancePreview siteName={selectedSite?.name ?? '-'} config={draftConfig} previewMode={previewMode} />}
        </article>
      </div>
    </section>
  )
}
