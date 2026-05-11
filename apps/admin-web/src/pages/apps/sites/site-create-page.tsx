import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { useNavigate } from 'react-router-dom'

import { siteApi, SITE_INDUSTRY_VALUES } from '@/api/site-api'
import { templateApi, type SiteTemplate, type TemplateStatus } from '@/api/template-api'
import { PageHeader } from '@/components/page'
import { cn } from '@/lib/cn'
import { ROUTES } from '@/router/route-paths'

const CREATE_SITE_STEPS = ['basicInfo', 'selectTemplate', 'themeSettings', 'confirmCreation'] as const

type CreateSiteStep = (typeof CREATE_SITE_STEPS)[number]
type ButtonStyle = 'rounded' | 'pill' | 'square'
type FontStyle = 'modern' | 'classic' | 'soft'
type HeroStyle = 'clean' | 'image-focused' | 'campaign'

interface SiteCreateWizardState {
  name: string
  code: string
  industry: (typeof SITE_INDUSTRY_VALUES)[number]
  domain: string
  description: string
  phone: string
  address: string
  businessHours: string
  primaryColor: string
  buttonStyle: ButtonStyle
  fontStyle: FontStyle
  heroStyle: HeroStyle
  logoPlaceholder: string
}

function templateStatusClassName(status: TemplateStatus) {
  switch (status) {
    case 'enabled':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'disabled':
      return 'border-rose-200 bg-rose-50 text-rose-700'
    default:
      return 'border-amber-200 bg-amber-50 text-amber-700'
  }
}

function previewButtonClassName(buttonStyle: ButtonStyle) {
  if (buttonStyle === 'pill') return 'rounded-full'
  if (buttonStyle === 'square') return 'rounded-none'
  return 'rounded-lg'
}

export function SiteCreatePage() {
  const { t } = useTranslation('sites')
  const { t: tTemplates } = useTranslation('templates')
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [step, setStep] = useState<CreateSiteStep>('basicInfo')
  const [selectedTemplateId, setSelectedTemplateId] = useState<string | null>(null)
  const [form, setForm] = useState<SiteCreateWizardState>({
    name: '',
    code: '',
    industry: SITE_INDUSTRY_VALUES[0],
    domain: '',
    description: '',
    phone: '',
    address: '',
    businessHours: '09:00 - 18:00',
    primaryColor: '#2563EB',
    buttonStyle: 'rounded',
    fontStyle: 'modern',
    heroStyle: 'clean',
    logoPlaceholder: '',
  })

  const { data: templates = [], isPending: isTemplatesPending } = useQuery({
    queryKey: ['templates', 'site-create'],
    queryFn: templateApi.listTemplates,
  })

  const filteredTemplates = useMemo(
    () => templates.filter((template) => template.industry === form.industry),
    [form.industry, templates],
  )

  const selectedTemplate = useMemo(
    () => templates.find((template) => template.id === selectedTemplateId) ?? null,
    [selectedTemplateId, templates],
  )

  useEffect(() => {
    if (!selectedTemplateId) return
    const isStillMatched = filteredTemplates.some((template) => template.id === selectedTemplateId)
    if (!isStillMatched) {
      setSelectedTemplateId(null)
    }
  }, [filteredTemplates, selectedTemplateId])

  const currentStepIndex = CREATE_SITE_STEPS.indexOf(step)

  const createMutation = useMutation({
    mutationFn: siteApi.createSite,
    onSuccess: async (createdSite) => {
      await queryClient.invalidateQueries({ queryKey: ['sites'] })
      toast.success(t('create.messages.createdSuccess'))
      navigate(ROUTES.apps.siteOverview(createdSite.id))
    },
  })

  function updateField<K extends keyof SiteCreateWizardState>(field: K, value: SiteCreateWizardState[K]) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  function isBasicInfoValid() {
    return (
      form.name.trim().length > 0 &&
      form.code.trim().length > 0 &&
      form.industry.trim().length > 0 &&
      form.domain.trim().length > 0
    )
  }

  function goToNextStep() {
    if (step === 'basicInfo') {
      if (!isBasicInfoValid()) {
        toast.warning(t('create.messages.requiredFields'))
        return
      }
      setStep('selectTemplate')
      return
    }

    if (step === 'selectTemplate') {
      if (!selectedTemplate) {
        toast.warning(t('create.messages.selectTemplateFirst'))
        return
      }
      setStep('themeSettings')
      return
    }

    if (step === 'themeSettings') {
      setStep('confirmCreation')
    }
  }

  function goToPreviousStep() {
    if (step === 'confirmCreation') {
      setStep('themeSettings')
      return
    }
    if (step === 'themeSettings') {
      setStep('selectTemplate')
      return
    }
    if (step === 'selectTemplate') {
      setStep('basicInfo')
    }
  }

  function renderBasicInfoStep() {
    return (
      <div className="grid gap-4 md:grid-cols-2">
        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.siteName')}</span>
          <input
            value={form.name}
            onChange={(event) => updateField('name', event.target.value)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
            placeholder={t('create.placeholders.siteName')}
          />
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.siteCode')}</span>
          <input
            value={form.code}
            onChange={(event) => updateField('code', event.target.value)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
            placeholder={t('create.placeholders.siteCode')}
          />
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.industry')}</span>
          <select
            value={form.industry}
            onChange={(event) => updateField('industry', event.target.value as SiteCreateWizardState['industry'])}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
          >
            {SITE_INDUSTRY_VALUES.map((value) => (
              <option key={value} value={value}>
                {t(`industry.${value}`)}
              </option>
            ))}
          </select>
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.domain')}</span>
          <input
            value={form.domain}
            onChange={(event) => updateField('domain', event.target.value)}
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

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.phone')}</span>
          <input
            value={form.phone}
            onChange={(event) => updateField('phone', event.target.value)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
            placeholder={t('create.placeholders.phone')}
          />
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.businessHours')}</span>
          <input
            value={form.businessHours}
            onChange={(event) => updateField('businessHours', event.target.value)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
            placeholder={t('create.placeholders.businessHours')}
          />
        </label>

        <label className="space-y-1.5 text-sm md:col-span-2">
          <span className="font-medium text-slate-700">{t('create.fields.address')}</span>
          <input
            value={form.address}
            onChange={(event) => updateField('address', event.target.value)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
            placeholder={t('create.placeholders.address')}
          />
        </label>
      </div>
    )
  }

  function renderTemplateCard(template: SiteTemplate) {
    const isSelected = template.id === selectedTemplateId
    return (
      <button
        key={template.id}
        type="button"
        onClick={() => setSelectedTemplateId(template.id)}
        className={cn(
          'rounded-2xl border p-4 text-left shadow-sm transition',
          isSelected ? 'border-blue-500 bg-blue-50' : 'border-slate-200 bg-white hover:border-slate-300',
        )}
      >
        <div className="flex items-start justify-between gap-2">
          <div>
            <p className="text-sm font-semibold text-slate-900">{template.name}</p>
            <p className="mt-1 text-xs text-slate-500">{template.code}</p>
          </div>
          <span className={cn('rounded-md border px-2 py-1 text-xs font-medium', templateStatusClassName(template.status))}>
            {tTemplates(`status.${template.status}`)}
          </span>
        </div>
        <dl className="mt-3 grid grid-cols-2 gap-2">
          <div className="rounded-lg border border-slate-100 bg-slate-50 p-2">
            <dt className="text-xs text-slate-500">{t('create.fields.industry')}</dt>
            <dd className="mt-1 text-sm font-medium text-slate-900">{t(`industry.${template.industry}`)}</dd>
          </div>
          <div className="rounded-lg border border-slate-100 bg-slate-50 p-2">
            <dt className="text-xs text-slate-500">{t('create.fields.primaryColor')}</dt>
            <dd className="mt-1 flex items-center gap-2 text-sm font-medium text-slate-900">
              <span className="h-3 w-3 rounded-full border border-slate-200" style={{ backgroundColor: template.defaultPrimaryColor }} />
              {template.defaultPrimaryColor}
            </dd>
          </div>
          <div className="rounded-lg border border-slate-100 bg-slate-50 p-2">
            <dt className="text-xs text-slate-500">{t('create.templateCard.supportedPages')}</dt>
            <dd className="mt-1 text-sm font-medium text-slate-900">{template.supportedPages.length}</dd>
          </div>
          <div className="rounded-lg border border-slate-100 bg-slate-50 p-2">
            <dt className="text-xs text-slate-500">{t('create.templateCard.supportedBlocks')}</dt>
            <dd className="mt-1 text-sm font-medium text-slate-900">{template.supportedBlocks.length}</dd>
          </div>
        </dl>
      </button>
    )
  }

  function renderSelectTemplateStep() {
    if (isTemplatesPending) {
      return <p className="text-sm text-slate-500">{t('create.templates.loading')}</p>
    }

    if (filteredTemplates.length === 0) {
      return (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-6 text-sm text-slate-600">
          {t('create.templates.empty')}
        </div>
      )
    }

    return <div className="grid gap-3 md:grid-cols-2">{filteredTemplates.map((template) => renderTemplateCard(template))}</div>
  }

  function renderThemeSettingsStep() {
    return (
      <div className="grid gap-4 md:grid-cols-2">
        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.primaryColor')}</span>
          <div className="flex items-center gap-2">
            <input
              type="color"
              value={form.primaryColor}
              onChange={(event) => updateField('primaryColor', event.target.value)}
              className="h-10 w-12 rounded-lg border border-slate-200 bg-white"
            />
            <input
              value={form.primaryColor}
              onChange={(event) => updateField('primaryColor', event.target.value)}
              className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
            />
          </div>
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.logoPlaceholder')}</span>
          <input
            value={form.logoPlaceholder}
            onChange={(event) => updateField('logoPlaceholder', event.target.value)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
            placeholder={t('create.placeholders.logoPlaceholder')}
          />
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.buttonStyle')}</span>
          <select
            value={form.buttonStyle}
            onChange={(event) => updateField('buttonStyle', event.target.value as ButtonStyle)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
          >
            <option value="rounded">{t('create.options.buttonStyle.rounded')}</option>
            <option value="pill">{t('create.options.buttonStyle.pill')}</option>
            <option value="square">{t('create.options.buttonStyle.square')}</option>
          </select>
        </label>

        <label className="space-y-1.5 text-sm">
          <span className="font-medium text-slate-700">{t('create.fields.fontStyle')}</span>
          <select
            value={form.fontStyle}
            onChange={(event) => updateField('fontStyle', event.target.value as FontStyle)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
          >
            <option value="modern">{t('create.options.fontStyle.modern')}</option>
            <option value="classic">{t('create.options.fontStyle.classic')}</option>
            <option value="soft">{t('create.options.fontStyle.soft')}</option>
          </select>
        </label>

        <label className="space-y-1.5 text-sm md:col-span-2">
          <span className="font-medium text-slate-700">{t('create.fields.heroStyle')}</span>
          <select
            value={form.heroStyle}
            onChange={(event) => updateField('heroStyle', event.target.value as HeroStyle)}
            className="w-full rounded-lg border border-slate-200 px-3 py-2 text-slate-800 outline-none focus-visible:ring-2 focus-visible:ring-blue-200"
          >
            <option value="clean">{t('create.options.heroStyle.clean')}</option>
            <option value="image-focused">{t('create.options.heroStyle.image-focused')}</option>
            <option value="campaign">{t('create.options.heroStyle.campaign')}</option>
          </select>
        </label>
      </div>
    )
  }

  function renderConfirmationItem(label: string, value: string) {
    return (
      <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2">
        <p className="text-xs text-slate-500">{label}</p>
        <p className="mt-1 text-sm font-medium text-slate-900">{value}</p>
      </div>
    )
  }

  function renderConfirmCreationStep() {
    return (
      <div className="space-y-3">
        <h3 className="text-base font-semibold text-slate-900">{t('create.confirmation.title')}</h3>
        <div className="grid gap-3 md:grid-cols-2">
          {renderConfirmationItem(t('create.fields.siteName'), form.name)}
          {renderConfirmationItem(t('create.fields.siteCode'), form.code)}
          {renderConfirmationItem(t('create.fields.industry'), t(`industry.${form.industry}`))}
          {renderConfirmationItem(t('create.fields.domain'), form.domain)}
          {renderConfirmationItem(t('create.fields.template'), selectedTemplate?.name ?? t('create.templates.selectPrompt'))}
          {renderConfirmationItem(t('create.fields.primaryColor'), form.primaryColor)}
          {renderConfirmationItem(t('create.fields.phone'), form.phone || '-')}
          {renderConfirmationItem(t('create.fields.address'), form.address || '-')}
          {renderConfirmationItem(t('create.fields.businessHours'), form.businessHours || '-')}
        </div>
      </div>
    )
  }

  function renderCurrentStep() {
    if (step === 'basicInfo') return renderBasicInfoStep()
    if (step === 'selectTemplate') return renderSelectTemplateStep()
    if (step === 'themeSettings') return renderThemeSettingsStep()
    return renderConfirmCreationStep()
  }

  function handleCreateSite() {
    if (!selectedTemplate) {
      toast.warning(t('create.messages.selectTemplateFirst'))
      setStep('selectTemplate')
      return
    }

    createMutation.mutate({
      name: form.name.trim(),
      code: form.code.trim(),
      industry: form.industry,
      templateCode: selectedTemplate.code,
      domain: form.domain.trim(),
      description: form.description.trim(),
      primaryColor: form.primaryColor,
      phone: form.phone.trim(),
      address: form.address.trim(),
      businessHours: form.businessHours.trim(),
    })
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
          {renderCurrentStep()}

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
                  onClick={goToPreviousStep}
                  className="rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50"
                >
                  {t('create.actions.back')}
                </button>
              )}
              {step !== 'confirmCreation' ? (
                <button
                  type="button"
                  onClick={goToNextStep}
                  className="rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-blue-700"
                >
                  {t('create.actions.next')}
                </button>
              ) : (
                <button
                  type="button"
                  onClick={handleCreateSite}
                  className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white transition hover:bg-slate-800 disabled:opacity-60"
                  disabled={createMutation.isPending}
                >
                  {t('create.actions.createSite')}
                </button>
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
                <p className="mt-1 text-sm font-medium text-slate-900">{form.name || '-'}</p>
              </div>
              <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2">
                <p className="text-xs text-slate-500">{t('create.fields.template')}</p>
                <p className="mt-1 text-sm font-medium text-slate-900">{selectedTemplate?.name ?? t('create.preview.noTemplate')}</p>
              </div>
              <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2">
                <p className="text-xs text-slate-500">{t('create.fields.primaryColor')}</p>
                <div className="mt-1 flex items-center gap-2 text-sm font-medium text-slate-900">
                  <span className="h-3.5 w-3.5 rounded-full border border-slate-200" style={{ backgroundColor: form.primaryColor }} />
                  {form.primaryColor}
                </div>
              </div>
              <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2">
                <p className="text-xs text-slate-500">{t('create.preview.buttonPreview')}</p>
                <button
                  type="button"
                  className={cn(
                    'mt-2 border border-blue-200 bg-blue-50 px-3 py-1.5 text-xs font-medium text-blue-700',
                    previewButtonClassName(form.buttonStyle),
                  )}
                >
                  {t('create.preview.buttonLabel')}
                </button>
              </div>
              <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2">
                <p className="text-xs text-slate-500">{t('create.fields.fontStyle')}</p>
                <p className="mt-1 text-sm font-medium text-slate-900">{t(`create.options.fontStyle.${form.fontStyle}`)}</p>
              </div>
              <div className="rounded-xl border border-slate-100 bg-slate-50 px-3 py-2">
                <p className="text-xs text-slate-500">{t('create.fields.heroStyle')}</p>
                <p className="mt-1 text-sm font-medium text-slate-900">{t(`create.options.heroStyle.${form.heroStyle}`)}</p>
              </div>
            </div>
          </article>
        </aside>
      </div>
    </section>
  )
}
