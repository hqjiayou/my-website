import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check } from 'lucide-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { useNavigate } from 'react-router-dom'

import {
  BUTTON_STYLE_VALUES,
  FONT_STYLE_VALUES,
  HERO_LAYOUT_VALUES,
  PRIMARY_COLOR_PATTERN,
  SITE_CODE_PATTERN,
  SITE_TAG_PATTERN,
  SITE_TYPE_PRESETS,
  createDefaultAppearanceConfig,
  siteApi,
  templateApi,
  type AppearanceConfigV1,
  type ButtonStyle,
  type HeroLayout,
  type SiteFontStyle,
  type SiteType,
  type TemplateListItem,
} from '@/api'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/config/routePaths'
import { PERMISSION_CODES } from '@/types/rbac'
import { PermissionGate } from '@/router/permission'
import { SiteAppearancePreview } from '@/pages/apps/style/siteAppearancePreview'

const CREATE_SITE_STEPS = ['basicInfo', 'selectTemplate', 'configureAppearance', 'confirm'] as const
type CreateSiteStep = (typeof CREATE_SITE_STEPS)[number]

interface SiteCreateFormState {
  siteName: string
  siteCode: string
  siteType: SiteType
  defaultDomain: string
  description: string
  templateId: number | null
  appearance: AppearanceConfigV1
}

function normalizePrimaryColor(value: string | null | undefined): string {
  if (value && PRIMARY_COLOR_PATTERN.test(value)) return value
  return '#2563EB'
}

export function SiteCreatePage() {
  const { t } = useTranslation('sites')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [step, setStep] = useState<CreateSiteStep>('basicInfo')
  const [customType, setCustomType] = useState(false)
  const [noTemplate, setNoTemplate] = useState(false)
  const [form, setForm] = useState<SiteCreateFormState>({
    siteName: '',
    siteCode: '',
    siteType: SITE_TYPE_PRESETS[0],
    defaultDomain: '',
    description: '',
    templateId: null,
    appearance: createDefaultAppearanceConfig(),
  })

  const currentStepIndex = CREATE_SITE_STEPS.indexOf(step)

  // 模板已与站点用途标签解耦：列出全部启用模板，不再按类型过滤。
  const templateFilters = {
    pageNum: 1,
    pageSize: 100,
    status: 'ENABLED' as const,
  }

  const templatesQuery = useQuery({
    queryKey: ['templates', templateFilters],
    queryFn: () => templateApi.listTemplates(templateFilters),
    enabled: step === 'selectTemplate' || step === 'configureAppearance' || step === 'confirm',
  })

  const templates = useMemo(() => templatesQuery.data?.records ?? [], [templatesQuery.data?.records])
  const selectedTemplate = useMemo(
    () => templates.find((item) => item.id === form.templateId) ?? null,
    [form.templateId, templates],
  )

  const createMutation = useMutation({
    mutationFn: siteApi.createSite,
    onSuccess: async (createdSite) => {
      await queryClient.invalidateQueries({ queryKey: ['sites'] })
      toast.success(t('create.messages.createdSuccess'))
      navigate(ROUTES.apps.siteOverview(createdSite.id))
    },
    onError: (error: Error) => toast.error(error.message),
  })

  function updateField<K extends keyof SiteCreateFormState>(field: K, value: SiteCreateFormState[K]) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  function updateAppearance<K extends keyof AppearanceConfigV1>(key: K, value: AppearanceConfigV1[K]) {
    setForm((prev) => ({
      ...prev,
      appearance: { ...prev.appearance, [key]: value },
    }))
  }

  function validateBasicInfo() {
    if (!form.siteName.trim() || !form.siteCode.trim() || !form.siteType.trim()) {
      toast.warning(t('create.messages.requiredFields'))
      return false
    }
    if (!SITE_CODE_PATTERN.test(form.siteCode.trim())) {
      toast.warning(t('create.messages.invalidSiteCode'))
      return false
    }
    if (!SITE_TAG_PATTERN.test(form.siteType.trim())) {
      toast.warning(t('create.messages.invalidSiteType'))
      return false
    }
    return true
  }

  function validateTemplate() {
    if (!noTemplate && form.templateId == null) {
      toast.warning(t('create.messages.selectTemplateFirst'))
      return false
    }
    return true
  }

  function validateAppearance() {
    if (!PRIMARY_COLOR_PATTERN.test(form.appearance.primaryColor)) {
      toast.warning(t('create.messages.invalidPrimaryColor'))
      return false
    }
    return true
  }

  function selectTemplate(template: TemplateListItem) {
    setNoTemplate(false)
    setForm((prev) => ({
      ...prev,
      templateId: template.id,
      appearance: {
        ...prev.appearance,
        primaryColor: normalizePrimaryColor(template.defaultPrimaryColor),
      },
    }))
  }

  function selectNoTemplate() {
    setNoTemplate(true)
    setForm((prev) => ({ ...prev, templateId: null }))
  }

  function goToNextStep() {
    if (step === 'basicInfo') {
      if (!validateBasicInfo()) return
      setStep('selectTemplate')
      return
    }
    if (step === 'selectTemplate') {
      if (!validateTemplate()) return
      setStep(noTemplate ? 'confirm' : 'configureAppearance')
      return
    }
    if (step === 'configureAppearance') {
      if (!validateAppearance()) return
      setStep('confirm')
    }
  }

  function goToPrevStep() {
    if (step === 'selectTemplate') setStep('basicInfo')
    else if (step === 'configureAppearance') setStep('selectTemplate')
    else if (step === 'confirm') setStep(noTemplate ? 'selectTemplate' : 'configureAppearance')
  }

  function handleSiteTypeChange(siteType: SiteType) {
    // 标签与模板已解耦：改标签不再重置已选模板与外观。
    setForm((prev) => ({ ...prev, siteType }))
  }

  function handleTypeSelect(value: string) {
    if (value === '__CUSTOM__') {
      setCustomType(true)
      handleSiteTypeChange('')
      return
    }
    setCustomType(false)
    handleSiteTypeChange(value)
  }

  function handleCreateSite() {
    if (!validateBasicInfo()) {
      setStep('basicInfo')
      return
    }
    if (!validateTemplate()) {
      setStep('selectTemplate')
      return
    }
    if (!noTemplate && !validateAppearance()) {
      setStep('configureAppearance')
      return
    }
    if (createMutation.isPending) return
    if (!noTemplate && form.templateId == null) return

    createMutation.mutate({
      siteCode: form.siteCode.trim(),
      siteName: form.siteName.trim(),
      siteType: form.siteType.trim(),
      defaultDomain: form.defaultDomain.trim() || undefined,
      description: form.description.trim() || undefined,
      templateId: noTemplate ? undefined : (form.templateId ?? undefined),
      appearance: noTemplate ? undefined : { ...form.appearance },
    })
  }

  function renderConfirmationItem(label: string, value: string) {
    return (
      <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2">
        <p className="text-xs text-slate-500">{label}</p>
        <p className="mt-1 text-sm font-medium text-slate-900">{value}</p>
      </div>
    )
  }

  return (
    <section>
      <PageHeader title={t('create.title')} description={t('create.description')} />

      <ol className="mb-4 grid gap-2 rounded-2xl border border-slate-200 bg-white p-3 shadow-sm md:grid-cols-4">
        {CREATE_SITE_STEPS.map((stepKey, index) => {
          const state = index < currentStepIndex ? 'completed' : index === currentStepIndex ? 'current' : 'upcoming'
          return (
            <li
              key={stepKey}
              className={cn(
                'flex items-center gap-2 rounded-xl border px-3 py-2',
                state === 'current' && 'border-blue-200 bg-blue-50',
                state === 'completed' && 'border-blue-200 bg-blue-50/60',
                state === 'upcoming' && 'border-slate-200 bg-slate-50',
              )}
            >
              <span
                className={cn(
                  'inline-flex h-6 w-6 items-center justify-center rounded-full border text-xs font-semibold',
                  state === 'current' && 'border-blue-600 bg-blue-600 text-white',
                  state === 'completed' && 'border-blue-600 bg-blue-600 text-white',
                  state === 'upcoming' && 'border-slate-300 bg-white text-slate-500',
                )}
              >
                {state === 'completed' ? <Check className="h-3.5 w-3.5" /> : index + 1}
              </span>
              <span className={cn('text-sm font-medium', state === 'upcoming' ? 'text-slate-500' : 'text-slate-900')}>
                {t(`create.steps.${stepKey}`)}
              </span>
            </li>
          )
        })}
      </ol>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(320px,1fr)]">
        <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
          <h2 className="mb-4 text-lg font-semibold text-slate-900">{t(`create.steps.${step}`)}</h2>

          {step === 'basicInfo' ? (
            <div className="grid gap-4 md:grid-cols-2">
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('create.fields.siteName')}</span>
                <input
                  value={form.siteName}
                  onChange={(event) => updateField('siteName', event.target.value)}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                  placeholder={t('create.placeholders.siteName')}
                />
              </label>

              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('create.fields.siteCode')}</span>
                <input
                  value={form.siteCode}
                  onChange={(event) => updateField('siteCode', event.target.value)}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                  placeholder={t('create.placeholders.siteCode')}
                />
              </label>

              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('create.fields.siteType')}</span>
                <select
                  value={customType ? '__CUSTOM__' : form.siteType}
                  onChange={(event) => handleTypeSelect(event.target.value)}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  {SITE_TYPE_PRESETS.map((value) => (
                    <option key={value} value={value}>
                      {t(`siteType.${value}`)}
                    </option>
                  ))}
                  <option value="__CUSTOM__">{t('create.fields.customSiteType')}</option>
                </select>
                {customType ? (
                  <input
                    value={form.siteType}
                    onChange={(event) => handleSiteTypeChange(event.target.value)}
                    className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                    placeholder={t('create.placeholders.customSiteType')}
                  />
                ) : null}
              </label>

              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('create.fields.defaultDomain')}</span>
                <input
                  value={form.defaultDomain}
                  onChange={(event) => updateField('defaultDomain', event.target.value)}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                  placeholder={t('create.placeholders.domain')}
                />
              </label>

              <label className="space-y-1.5 text-sm md:col-span-2">
                <span className="font-medium text-slate-700">{t('create.fields.siteDescription')}</span>
                <textarea
                  value={form.description}
                  onChange={(event) => updateField('description', event.target.value)}
                  rows={4}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                  placeholder={t('create.placeholders.siteDescription')}
                />
              </label>
            </div>
          ) : null}

          {step === 'selectTemplate' ? (
            <div className="space-y-3">
              {templatesQuery.isPending ? (
                <p className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-3 text-sm text-slate-500">
                  {t('create.templates.loading')}
                </p>
              ) : null}
              {templatesQuery.isError ? (
                <div className="rounded-xl border border-rose-200 bg-rose-50 px-3 py-3 text-sm text-rose-700">
                  <p>{templatesQuery.error instanceof Error ? templatesQuery.error.message : t('create.templates.empty')}</p>
                  <button
                    type="button"
                    onClick={() => void templatesQuery.refetch()}
                    className="mt-2 rounded-md border border-rose-200 bg-white px-2.5 py-1 text-xs font-medium"
                  >
                    {t('create.actions.retry')}
                  </button>
                </div>
              ) : null}
              {!templatesQuery.isPending && !templatesQuery.isError && templates.length === 0 ? (
                <p className="rounded-xl border border-dashed border-slate-200 bg-slate-50 px-3 py-3 text-sm text-slate-500">
                  {t('create.templates.empty')}
                </p>
              ) : null}
              <div className="grid gap-3 md:grid-cols-2">
                <button
                  type="button"
                  onClick={selectNoTemplate}
                  className={cn(
                    'rounded-xl border p-4 text-left transition',
                    noTemplate
                      ? 'border-blue-300 bg-blue-50 shadow-sm'
                      : 'border-dashed border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50',
                  )}
                >
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <p className="text-sm font-semibold text-slate-900">{t('create.templates.noTemplateOption')}</p>
                      <p className="mt-1 text-xs text-slate-500">{t('create.templates.noTemplateHint')}</p>
                    </div>
                    {noTemplate ? (
                      <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-blue-600 text-white">
                        <Check className="h-3 w-3" />
                      </span>
                    ) : null}
                  </div>
                </button>
                {templates.map((template) => {
                  const selected = form.templateId === template.id
                  return (
                    <button
                      key={template.id}
                      type="button"
                      onClick={() => selectTemplate(template)}
                      className={cn(
                        'rounded-xl border p-4 text-left transition',
                        selected
                          ? 'border-blue-300 bg-blue-50 shadow-sm'
                          : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50',
                      )}
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div>
                          <p className="text-sm font-semibold text-slate-900">{template.name}</p>
                          <p className="mt-1 text-xs text-slate-500">{template.code}</p>
                        </div>
                        {selected ? (
                          <span className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-blue-600 text-white">
                            <Check className="h-3 w-3" />
                          </span>
                        ) : null}
                      </div>
                      <div className="mt-3 flex items-center gap-2 text-xs text-slate-600">
                        <span
                          className="inline-block h-4 w-4 rounded border border-slate-200"
                          style={{ backgroundColor: normalizePrimaryColor(template.defaultPrimaryColor) }}
                        />
                        <span>{normalizePrimaryColor(template.defaultPrimaryColor)}</span>
                      </div>
                    </button>
                  )
                })}
              </div>
            </div>
          ) : null}

          {step === 'configureAppearance' ? (
            <div className="grid gap-4 md:grid-cols-2">
              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('create.fields.primaryColor')}</span>
                <div className="flex items-center gap-2">
                  <input
                    type="color"
                    value={form.appearance.primaryColor}
                    onChange={(event) => updateAppearance('primaryColor', event.target.value.toUpperCase())}
                    className="h-10 w-12 rounded-lg border border-slate-200 bg-white"
                  />
                  <input
                    value={form.appearance.primaryColor}
                    onChange={(event) => updateAppearance('primaryColor', event.target.value)}
                    className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                  />
                </div>
              </label>

              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('create.fields.fontStyle')}</span>
                <select
                  value={form.appearance.fontStyle}
                  onChange={(event) => updateAppearance('fontStyle', event.target.value as SiteFontStyle)}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  {FONT_STYLE_VALUES.map((value) => (
                    <option key={value} value={value}>
                      {t(`create.options.fontStyle.${value}`)}
                    </option>
                  ))}
                </select>
              </label>

              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('create.fields.buttonStyle')}</span>
                <select
                  value={form.appearance.buttonStyle}
                  onChange={(event) => updateAppearance('buttonStyle', event.target.value as ButtonStyle)}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  {BUTTON_STYLE_VALUES.map((value) => (
                    <option key={value} value={value}>
                      {t(`create.options.buttonStyle.${value}`)}
                    </option>
                  ))}
                </select>
              </label>

              <label className="space-y-1.5 text-sm">
                <span className="font-medium text-slate-700">{t('create.fields.heroLayout')}</span>
                <select
                  value={form.appearance.heroLayout}
                  onChange={(event) => updateAppearance('heroLayout', event.target.value as HeroLayout)}
                  className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
                >
                  {HERO_LAYOUT_VALUES.map((value) => (
                    <option key={value} value={value}>
                      {t(`create.options.heroLayout.${value}`)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          ) : null}

          {step === 'confirm' ? (
            <div className="space-y-3">
              <h3 className="text-base font-semibold text-slate-900">{t('create.confirmation.title')}</h3>
              <div className="grid gap-3 md:grid-cols-2">
                {renderConfirmationItem(t('create.fields.siteName'), form.siteName)}
                {renderConfirmationItem(t('create.fields.siteCode'), form.siteCode)}
                {renderConfirmationItem(
                  t('create.fields.siteType'),
                  t(`siteType.${form.siteType}`, { defaultValue: form.siteType }),
                )}
                {renderConfirmationItem(t('create.fields.defaultDomain'), form.defaultDomain || '—')}
                {renderConfirmationItem(t('create.fields.siteDescription'), form.description || '—')}
                {renderConfirmationItem(
                  t('create.fields.template'),
                  noTemplate
                    ? t('create.templates.noTemplateOption')
                    : (selectedTemplate?.name ?? (form.templateId != null ? `#${form.templateId}` : '—')),
                )}
                {!noTemplate ? (
                  <>
                    {renderConfirmationItem(t('create.fields.primaryColor'), form.appearance.primaryColor)}
                    {renderConfirmationItem(
                      t('create.fields.fontStyle'),
                      t(`create.options.fontStyle.${form.appearance.fontStyle}`),
                    )}
                    {renderConfirmationItem(
                      t('create.fields.buttonStyle'),
                      t(`create.options.buttonStyle.${form.appearance.buttonStyle}`),
                    )}
                    {renderConfirmationItem(
                      t('create.fields.heroLayout'),
                      t(`create.options.heroLayout.${form.appearance.heroLayout}`),
                    )}
                  </>
                ) : null}
              </div>
            </div>
          ) : null}

          <div className="mt-6 flex flex-wrap justify-between gap-2">
            <button
              type="button"
              onClick={() => navigate(ROUTES.apps.sites)}
              className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
            >
              {t('create.actions.cancel')}
            </button>

            <div className="flex flex-wrap gap-2">
              {step !== 'basicInfo' && (
                <button
                  type="button"
                  onClick={goToPrevStep}
                  className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
                >
                  {t('create.actions.back')}
                </button>
              )}
              {step !== 'confirm' ? (
                <button
                  type="button"
                  onClick={goToNextStep}
                  className="rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-blue-700"
                >
                  {t('create.actions.next')}
                </button>
              ) : (
                <PermissionGate code={PERMISSION_CODES.siteCreate}>
                  <button
                    type="button"
                    onClick={handleCreateSite}
                    className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white transition hover:bg-slate-800 disabled:opacity-60"
                    disabled={createMutation.isPending}
                  >
                    {createMutation.isPending ? t('create.actions.creating') : t('create.actions.createSite')}
                  </button>
                </PermissionGate>
              )}
            </div>
          </div>
        </article>

        <aside className="space-y-4">
          <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <h3 className="text-base font-semibold text-slate-900">{t('create.preview.title')}</h3>
            <div className="mt-3 space-y-2">
              <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2">
                <p className="text-xs text-slate-500">{t('create.fields.siteName')}</p>
                <p className="mt-1 text-sm font-medium text-slate-900">{form.siteName || '—'}</p>
              </div>
              <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2">
                <p className="text-xs text-slate-500">{t('create.fields.template')}</p>
                <p className="mt-1 text-sm font-medium text-slate-900">
                  {selectedTemplate?.name ?? t('create.preview.noTemplate')}
                </p>
              </div>
              {(step === 'configureAppearance' || step === 'confirm') && (
                <div className="pt-2">
                  <SiteAppearancePreview
                    siteName={form.siteName || t('create.preview.title')}
                    config={form.appearance}
                    previewMode="desktop"
                  />
                </div>
              )}
            </div>
          </article>
        </aside>
      </div>
    </section>
  )
}
