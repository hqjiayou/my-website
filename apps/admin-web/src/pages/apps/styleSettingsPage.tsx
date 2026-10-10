import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Monitor, Smartphone } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'

import {
  APPEARANCE_SCHEMA_VERSION_V1,
  BUTTON_STYLE_VALUES,
  FONT_STYLE_VALUES,
  HERO_LAYOUT_VALUES,
  PRIMARY_COLOR_PATTERN,
  appearanceApi,
  siteApi,
  type AppearanceConfigV1,
  type AppearanceDetail,
  type ButtonStyle,
  type HeroLayout,
  type SiteFontStyle,
} from '@/api'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { SiteAppearancePreview } from '@/pages/apps/style/siteAppearancePreview'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

function cloneConfig(config: AppearanceConfigV1): AppearanceConfigV1 {
  return { ...config }
}

export function StyleSettingsPage() {
  const { t } = useTranslation('styleSettings')
  const queryClient = useQueryClient()
  const [previewMode, setPreviewMode] = useState<'desktop' | 'mobile'>('desktop')
  const [selectedSiteId, setSelectedSiteId] = useState<string>('')

  const listQuery = useMemo(() => ({ pageNum: 1, pageSize: 100 }), [])
  const {
    data: sitesPage,
    isPending: isSitesPending,
    isError: isSitesError,
    error: sitesError,
  } = useQuery({
    queryKey: ['sites', listQuery],
    queryFn: () => siteApi.listSites(listQuery),
  })
  const sites = useMemo(() => sitesPage?.records ?? [], [sitesPage?.records])

  const activeSiteId = selectedSiteId || (sites[0] ? String(sites[0].id) : '')
  const numericSiteId = Number(activeSiteId)

  const {
    data: appearanceDetail,
    isPending: isAppearancePending,
    isError: isAppearanceError,
    error: appearanceError,
  } = useQuery({
    queryKey: ['appearances', numericSiteId],
    queryFn: () => appearanceApi.getAppearance(numericSiteId),
    enabled: Number.isFinite(numericSiteId) && numericSiteId > 0,
  })

  const selectedSite = useMemo(
    () => sites.find((site) => String(site.id) === activeSiteId) ?? null,
    [activeSiteId, sites],
  )

  if (isSitesPending) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
        {t('messages.loadingSites')}
      </section>
    )
  }

  if (isSitesError) {
    return (
      <section className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700 shadow-sm">
        {sitesError instanceof Error ? sitesError.message : t('messages.configUnavailable')}
      </section>
    )
  }

  if (sites.length === 0) {
    return (
      <section>
        <PageHeader title={t('title')} description={t('description')} />
        <div className="mt-4 rounded-2xl border border-dashed border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
          {t('messages.noSites')}
        </div>
      </section>
    )
  }

  return (
    <section>
      <PageHeader
        title={t('title')}
        description={t('description')}
        action={
          <label className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-sm text-slate-700">
            <span className="text-slate-500">{t('currentSite')}</span>
            <select
              value={activeSiteId}
              onChange={(event) => setSelectedSiteId(event.target.value)}
              className="min-w-36 border-none bg-transparent text-sm outline-none"
            >
              {sites.map((site) => (
                <option key={site.id} value={String(site.id)}>
                  {site.siteName}
                </option>
              ))}
            </select>
          </label>
        }
      />

      {isAppearancePending ? (
        <div className="mt-4 rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">
          {t('messages.loadingConfig')}
        </div>
      ) : null}

      {isAppearanceError ? (
        <div className="mt-4 rounded-2xl border border-rose-200 bg-rose-50 p-6 text-sm text-rose-700 shadow-sm">
          {appearanceError instanceof Error ? appearanceError.message : t('messages.configUnavailable')}
        </div>
      ) : null}

      {appearanceDetail && !isAppearancePending && !isAppearanceError ? (
        <StyleSettingsWorkspace
          key={`${numericSiteId}-${appearanceDetail.version}`}
          appearanceDetail={appearanceDetail}
          siteId={numericSiteId}
          selectedSiteName={selectedSite?.siteName ?? '-'}
          previewMode={previewMode}
          setPreviewMode={setPreviewMode}
          t={t}
          queryClient={queryClient}
        />
      ) : null}
    </section>
  )
}

type StyleSettingsWorkspaceProps = {
  appearanceDetail: AppearanceDetail
  siteId: number
  selectedSiteName: string
  previewMode: 'desktop' | 'mobile'
  setPreviewMode: (mode: 'desktop' | 'mobile') => void
  t: ReturnType<typeof useTranslation<'styleSettings'>>['t']
  queryClient: ReturnType<typeof useQueryClient>
}

function StyleSettingsWorkspace({
  appearanceDetail,
  siteId,
  selectedSiteName,
  previewMode,
  setPreviewMode,
  t,
  queryClient,
}: StyleSettingsWorkspaceProps) {
  const canUpdate = useHasPermission(PERMISSION_CODES.siteUpdate)
  // 父级以 id+version 作为 key 重建本组件，避免 effect 内同步 setState
  const [draftConfig, setDraftConfig] = useState(() => cloneConfig(appearanceDetail.config))
  const [version, setVersion] = useState(appearanceDetail.version)

  const saveMutation = useMutation({
    mutationFn: (input: { config: AppearanceConfigV1; version: number }) =>
      appearanceApi.putAppearance(siteId, {
        schemaVersion: APPEARANCE_SCHEMA_VERSION_V1,
        config: input.config,
        version: input.version,
      }),
    onSuccess: async (updated) => {
      setDraftConfig(cloneConfig(updated.config))
      setVersion(updated.version)
      await queryClient.invalidateQueries({ queryKey: ['appearances', siteId] })
      toast.success(t('messages.saved'))
    },
    onError: (error: Error) => {
      toast.error(error.message)
      // 乐观锁冲突后刷新 version，避免连点沿用旧 version
      void queryClient.invalidateQueries({ queryKey: ['appearances', siteId] })
    },
  })

  function updateField<K extends keyof AppearanceConfigV1>(key: K, value: AppearanceConfigV1[K]) {
    setDraftConfig((prev) => ({ ...prev, [key]: value }))
  }

  function resetToLoaded() {
    setDraftConfig(cloneConfig(appearanceDetail.config))
    setVersion(appearanceDetail.version)
  }

  function saveConfig() {
    if (!canUpdate) return
    if (!PRIMARY_COLOR_PATTERN.test(draftConfig.primaryColor)) {
      toast.warning(t('messages.invalidPrimaryColor'))
      return
    }
    if (saveMutation.isPending) return
    saveMutation.mutate({ config: { ...draftConfig }, version })
  }

  return (
    <>
      <div className="mt-4 flex flex-wrap items-center gap-2">
        <PermissionGate code={PERMISSION_CODES.siteUpdate}>
          <button
            type="button"
            onClick={saveConfig}
            disabled={saveMutation.isPending}
            className="rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white shadow-sm transition hover:bg-blue-700 disabled:opacity-60"
          >
            {saveMutation.isPending ? t('saving') : t('saveConfig')}
          </button>
        </PermissionGate>
        <button
          type="button"
          onClick={resetToLoaded}
          disabled={saveMutation.isPending}
          className="rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:opacity-60"
        >
          {t('resetDefault')}
        </button>
        <span className="text-xs text-slate-500">
          {t('versionLabel')}: {version}
        </span>
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(360px,1fr)]">
        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-4 text-base font-semibold text-slate-900">{t('editorTitle')}</h2>
          <div className="grid gap-4 md:grid-cols-2">
            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-slate-700">{t('fields.primaryColor')}</span>
              <div className="flex items-center gap-2">
                <input
                  type="color"
                  value={draftConfig.primaryColor}
                  onChange={(event) => updateField('primaryColor', event.target.value.toUpperCase())}
                  className="h-10 w-12 rounded-lg border border-slate-200 bg-white"
                />
                <input
                  value={draftConfig.primaryColor}
                  onChange={(event) => updateField('primaryColor', event.target.value)}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                />
              </div>
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-slate-700">{t('fields.fontStyle')}</span>
              <select
                value={draftConfig.fontStyle}
                onChange={(event) => updateField('fontStyle', event.target.value as SiteFontStyle)}
                className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
              >
                {FONT_STYLE_VALUES.map((value) => (
                  <option key={value} value={value}>
                    {t(`options.fontStyle.${value}`)}
                  </option>
                ))}
              </select>
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-slate-700">{t('fields.buttonStyle')}</span>
              <select
                value={draftConfig.buttonStyle}
                onChange={(event) => updateField('buttonStyle', event.target.value as ButtonStyle)}
                className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
              >
                {BUTTON_STYLE_VALUES.map((value) => (
                  <option key={value} value={value}>
                    {t(`options.buttonStyle.${value}`)}
                  </option>
                ))}
              </select>
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-slate-700">{t('fields.heroLayout')}</span>
              <select
                value={draftConfig.heroLayout}
                onChange={(event) => updateField('heroLayout', event.target.value as HeroLayout)}
                className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
              >
                {HERO_LAYOUT_VALUES.map((value) => (
                  <option key={value} value={value}>
                    {t(`options.heroLayout.${value}`)}
                  </option>
                ))}
              </select>
            </label>
          </div>
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

          <SiteAppearancePreview siteName={selectedSiteName} config={draftConfig} previewMode={previewMode} />
        </article>
      </div>
    </>
  )
}
