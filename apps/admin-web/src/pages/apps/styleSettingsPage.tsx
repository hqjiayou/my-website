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
  agentApi,
  type AppearanceConfigV1,
  type AppearanceDetail,
  type ButtonStyle,
  type HeroLayout,
  type AgentFontStyle,
} from '@/api'
import { AdminPageHeader } from '@/components/admin'
import { cn } from '@/lib/cn'
import { AgentAppearancePreview } from '@/pages/apps/style/agentAppearancePreview'
import { PermissionGate, useHasPermission } from '@/router/permission'
import { PERMISSION_CODES } from '@/types/rbac'

function cloneConfig(config: AppearanceConfigV1): AppearanceConfigV1 {
  return { ...config }
}

export function StyleSettingsPage() {
  const { t } = useTranslation('styleSettings')
  const queryClient = useQueryClient()
  const [previewMode, setPreviewMode] = useState<'desktop' | 'mobile'>('desktop')
  const [selectedAgentId, setSelectedAgentId] = useState<string>('')

  const listQuery = useMemo(() => ({ pageNum: 1, pageSize: 100 }), [])
  const {
    data: sitesPage,
    isPending: isSitesPending,
    isError: isSitesError,
    error: sitesError,
  } = useQuery({
    queryKey: ['sites', listQuery],
    queryFn: () => agentApi.listSites(listQuery),
  })
  const sites = useMemo(() => sitesPage?.records ?? [], [sitesPage?.records])

  const activeAgentId = selectedAgentId || (sites[0] ? String(sites[0].id) : '')
  const numericAgentId = Number(activeAgentId)

  const {
    data: appearanceDetail,
    isPending: isAppearancePending,
    isError: isAppearanceError,
    error: appearanceError,
  } = useQuery({
    queryKey: ['appearances', numericAgentId],
    queryFn: () => appearanceApi.getAppearance(numericAgentId),
    enabled: Number.isFinite(numericAgentId) && numericAgentId > 0,
  })

  const selectedSite = useMemo(
    () => sites.find((site) => String(site.id) === activeAgentId) ?? null,
    [activeAgentId, sites],
  )

  if (isSitesPending) {
    return (
      <section className="admin-content-form rounded-xl border border-border bg-card p-6 text-sm text-muted-foreground">
        {t('messages.loadingSites')}
      </section>
    )
  }

  if (isSitesError) {
    return (
      <section className="rounded-xl border border-danger/35 bg-danger-muted p-6 text-sm text-danger-emphasis">
        {sitesError instanceof Error ? sitesError.message : t('messages.configUnavailable')}
      </section>
    )
  }

  if (sites.length === 0) {
    return (
      <section>
        <AdminPageHeader title={t('title')} description={t('description')} />
        <div className="mt-4 rounded-xl border border-dashed border-border bg-card p-6 text-sm text-muted-foreground">
          {t('messages.noSites')}
        </div>
      </section>
    )
  }

  return (
    <section>
      <AdminPageHeader
        title={t('title')}
        description={t('description')}
        action={
          <label className="flex items-center gap-2 rounded-lg border border-border bg-card px-2.5 py-1.5 text-sm text-foreground">
            <span className="text-muted-foreground">{t('currentSite')}</span>
            <select
              value={activeAgentId}
              onChange={(event) => setSelectedAgentId(event.target.value)}
              className="min-w-36 border-none bg-transparent text-sm outline-none"
            >
              {sites.map((site) => (
                <option key={site.id} value={String(site.id)}>
                  {site.agentName}
                </option>
              ))}
            </select>
          </label>
        }
      />

      {isAppearancePending ? (
        <div className="mt-4 rounded-xl border border-border bg-card p-6 text-sm text-muted-foreground">
          {t('messages.loadingConfig')}
        </div>
      ) : null}

      {isAppearanceError ? (
        <div className="mt-4 rounded-xl border border-danger/35 bg-danger-muted p-6 text-sm text-danger-emphasis">
          {appearanceError instanceof Error ? appearanceError.message : t('messages.configUnavailable')}
        </div>
      ) : null}

      {appearanceDetail && !isAppearancePending && !isAppearanceError ? (
        <StyleSettingsWorkspace
          key={`${numericAgentId}-${appearanceDetail.version}`}
          appearanceDetail={appearanceDetail}
          agentId={numericAgentId}
          selectedAgentName={selectedSite?.agentName ?? '-'}
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
  agentId: number
  selectedAgentName: string
  previewMode: 'desktop' | 'mobile'
  setPreviewMode: (mode: 'desktop' | 'mobile') => void
  t: ReturnType<typeof useTranslation<'styleSettings'>>['t']
  queryClient: ReturnType<typeof useQueryClient>
}

function StyleSettingsWorkspace({
  appearanceDetail,
  agentId,
  selectedAgentName,
  previewMode,
  setPreviewMode,
  t,
  queryClient,
}: StyleSettingsWorkspaceProps) {
  const canUpdate = useHasPermission(PERMISSION_CODES.agentUpdate)
  // 父级以 id+version 作为 key 重建本组件，避免 effect 内同步 setState
  const [draftConfig, setDraftConfig] = useState(() => cloneConfig(appearanceDetail.config))
  const [version, setVersion] = useState(appearanceDetail.version)

  const saveMutation = useMutation({
    mutationFn: (input: { config: AppearanceConfigV1; version: number }) =>
      appearanceApi.putAppearance(agentId, {
        schemaVersion: APPEARANCE_SCHEMA_VERSION_V1,
        config: input.config,
        version: input.version,
      }),
    onSuccess: async (updated) => {
      setDraftConfig(cloneConfig(updated.config))
      setVersion(updated.version)
      await queryClient.invalidateQueries({ queryKey: ['appearances', agentId] })
      toast.success(t('messages.saved'))
    },
    onError: (error: Error) => {
      toast.error(error.message)
      // 乐观锁冲突后刷新 version，避免连点沿用旧 version
      void queryClient.invalidateQueries({ queryKey: ['appearances', agentId] })
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
        <PermissionGate code={PERMISSION_CODES.agentUpdate}>
          <button
            type="button"
            onClick={saveConfig}
            disabled={saveMutation.isPending}
            className="rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground transition hover:bg-primary/92 disabled:opacity-60"
          >
            {saveMutation.isPending ? t('saving') : t('saveConfig')}
          </button>
        </PermissionGate>
        <button
          type="button"
          onClick={resetToLoaded}
          disabled={saveMutation.isPending}
          className="rounded-lg border border-border bg-card px-3 py-2 text-sm font-medium text-foreground transition hover:bg-card-muted disabled:opacity-60"
        >
          {t('resetDefault')}
        </button>
        <span className="text-xs text-muted-foreground">
          {t('versionLabel')}: {version}
        </span>
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(360px,1fr)]">
        <article className="rounded-xl border border-border bg-card p-5">
          <h2 className="mb-4 text-base font-semibold text-foreground">{t('editorTitle')}</h2>
          <div className="grid gap-4 md:grid-cols-2">
            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-foreground">{t('fields.primaryColor')}</span>
              <div className="flex items-center gap-2">
                <input
                  type="color"
                  value={draftConfig.primaryColor}
                  onChange={(event) => updateField('primaryColor', event.target.value.toUpperCase())}
                  className="h-10 w-12 rounded-lg border border-border bg-card"
                />
                <input
                  value={draftConfig.primaryColor}
                  onChange={(event) => updateField('primaryColor', event.target.value)}
                  className="w-full rounded-lg border border-border px-3 py-2 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring"
                />
              </div>
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-foreground">{t('fields.fontStyle')}</span>
              <select
                value={draftConfig.fontStyle}
                onChange={(event) => updateField('fontStyle', event.target.value as AgentFontStyle)}
                className="w-full rounded-lg border border-border px-3 py-2 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {FONT_STYLE_VALUES.map((value) => (
                  <option key={value} value={value}>
                    {t(`options.fontStyle.${value}`)}
                  </option>
                ))}
              </select>
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-foreground">{t('fields.buttonStyle')}</span>
              <select
                value={draftConfig.buttonStyle}
                onChange={(event) => updateField('buttonStyle', event.target.value as ButtonStyle)}
                className="w-full rounded-lg border border-border px-3 py-2 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {BUTTON_STYLE_VALUES.map((value) => (
                  <option key={value} value={value}>
                    {t(`options.buttonStyle.${value}`)}
                  </option>
                ))}
              </select>
            </label>

            <label className="space-y-1.5 text-sm">
              <span className="font-medium text-foreground">{t('fields.heroLayout')}</span>
              <select
                value={draftConfig.heroLayout}
                onChange={(event) => updateField('heroLayout', event.target.value as HeroLayout)}
                className="w-full rounded-lg border border-border px-3 py-2 text-foreground outline-none focus-visible:ring-2 focus-visible:ring-ring"
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

        <article className="rounded-xl border border-border bg-card p-5">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-base font-semibold text-foreground">{t('previewSite')}</h2>
            <div className="inline-flex rounded-lg border border-border bg-card-muted p-1">
              <button
                type="button"
                onClick={() => setPreviewMode('desktop')}
                className={cn(
                  'inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium',
                  previewMode === 'desktop' ? 'bg-card text-primary ' : 'text-muted-foreground',
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
                  previewMode === 'mobile' ? 'bg-card text-primary ' : 'text-muted-foreground',
                )}
              >
                <Smartphone className="h-3.5 w-3.5" />
                {t('previewMode.mobile')}
              </button>
            </div>
          </div>

          <AgentAppearancePreview agentName={selectedAgentName} config={draftConfig} previewMode={previewMode} />
        </article>
      </div>
    </>
  )
}
